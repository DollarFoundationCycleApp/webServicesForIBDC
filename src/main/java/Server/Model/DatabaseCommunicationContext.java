package Server.Model;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import IBDCServer.Server.IBDCServerV021.Incident;
import IBDCServer.Server.IBDCServerV021.IncidentPhoto;
import IBDCServer.Server.IBDCServerV021.Location;
import IBDCServer.Server.IBDCServerV021.OffenderVehicle;

/**
 * DatabaseCommunicationContext provides JDBC operations for storing and retrieving
 * incident data, images, and user information.
 *
 * Incident information and incident images live in two separate tables
 * ({@code incidents} and {@code incident_photos}) that are linked by
 * {@code incident_id}. A {@code device_event_id} is only unique per user, so
 * the server-assigned {@code incident_id} is the reliable link.
 */
public class DatabaseCommunicationContext {

    public enum CreateUserResult {
        CREATED, DUPLICATE, FAILED, UNAVAILABLE
    }

    private final String dbHost;
    private final int dbPort;
    private final String dbName;
    private final String dbUser;
    private final String dbPassword;

    public DatabaseCommunicationContext(String host, int port, String name, String user, String password) {
        this.dbHost = host;
        this.dbPort = port;
        this.dbName = name;
        this.dbUser = user;
        this.dbPassword = password;
    }

    /**
     * Open a new database connection. Callers are responsible for closing it.
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(getJdbcUrl(), dbUser, dbPassword);
    }

    private String getJdbcUrl() {
        return "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName
                + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    }

    /**
     * Store an incident, its linked photos, and the optional offender vehicle in a
     * single transaction. The photos and vehicle are linked to the incident via
     * its server-assigned incident_id.
     *
     * @return the server-assigned incident_id on success, 0 on failure.
     */
    public long storeIncident(String userId, Incident incident, List<IncidentPhoto> photos,
                              OffenderVehicle vehicle) {
        String incidentSql = "INSERT INTO incidents (device_event_id, user_id, timestamp_ms, "
                + "latitude, longitude, accuracy_meters, distance_cm, time_offset_ms, is_anonymized) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String photoSql = "INSERT INTO incident_photos (incident_id, image_index, format, "
                + "image_data, contains_sensitive_data) VALUES (?, ?, ?, ?, ?)";
        String vehicleSql = "INSERT INTO offender_vehicles (incident_id, license_plate, "
                + "state_or_region, vehicle_make, vehicle_model, vehicle_color) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        String vehicleMakeIdSql = "SELECT make_id FROM vehicle_makes WHERE make_name = ?";
        String vehicleModelSql = "INSERT IGNORE INTO vehicle_models (make_id, model_name) VALUES (?, ?)";

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            int deviceEventId = incident.getDeviceEventId();
            Location location = incident.getLocation();

            long incidentId;
            try (PreparedStatement ps = conn.prepareStatement(incidentSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, deviceEventId);
                ps.setString(2, userId);
                ps.setLong(3, incident.getTimestampMs());
                ps.setDouble(4, location.getLatitude());
                ps.setDouble(5, location.getLongitude());
                ps.setFloat(6, location.getAccuracyMeters());
                ps.setInt(7, incident.getDistanceCm());
                ps.setFloat(8, incident.getTimeOffsetMs());
                ps.setBoolean(9, incident.getIsAnonymized());
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("No incident_id returned for device_event_id " + deviceEventId);
                    }
                    incidentId = keys.getLong(1);
                }
            }

            if (photos != null && !photos.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(photoSql)) {
                    for (IncidentPhoto photo : photos) {
                        ps.setLong(1, incidentId);
                        ps.setInt(2, photo.getImageIndex());
                        ps.setString(3, photo.getFormat());
                        ps.setBytes(4, photo.getImageData().toByteArray());
                        ps.setBoolean(5, photo.getContainsSensitiveData());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            if (vehicle != null && hasVehicleData(vehicle)) {
                try (PreparedStatement ps = conn.prepareStatement(vehicleSql)) {
                    ps.setLong(1, incidentId);
                    ps.setString(2, vehicle.getLicensePlate());
                    ps.setString(3, vehicle.getStateOrRegion());
                    ps.setString(4, vehicle.getVehicleMake());
                    ps.setString(5, vehicle.getVehicleModel());
                    ps.setString(6, vehicle.getVehicleColor());
                    ps.executeUpdate();
                }
            }

            if (vehicle != null) {
                if (!vehicle.getVehicleMake().isEmpty() || vehicle.getVehicleMake() != null) {
                    storeVehicleMake(vehicle.getVehicleMake());
                    if (!vehicle.getVehicleModel().isEmpty() || vehicle.getVehicleModel() != null) {
                        storeVehicleModel(vehicle.getVehicleMake(), vehicle.getVehicleModel());
                    }
                }
            }

            conn.commit();
            return incidentId;
        } catch (SQLException e) {
            System.err.println("Error storing incident: " + e.getMessage());
            rollbackQuietly(conn);
            return 0;
        } finally {
            closeQuietly(conn);
        }
    }

    /**
     * Store a single incident photo linked to an incident by incident_id.
     */
    public boolean storeIncidentPhoto(long incidentId, int imageIndex, String format,
                                      byte[] imageData, boolean containsSensitiveData) {
        String sql = "INSERT INTO incident_photos (incident_id, image_index, format, "
                + "image_data, contains_sensitive_data) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, incidentId);
            ps.setInt(2, imageIndex);
            ps.setString(3, format);
            ps.setBytes(4, imageData);
            ps.setBoolean(5, containsSensitiveData);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error storing incident photo: " + e.getMessage());
            return false;
        }
    }

    /**
     * Store a vehicle make in the database if it doesn't already exist.
     */
    public boolean storeVehicleMake(String makeName) {
        String sql = "INSERT IGNORE INTO vehicle_makes (make_name) VALUES (?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, makeName);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error storing vehicle make: " + e.getMessage());
            return false;
        }
    }

    public boolean storeVehicleModel(String makeName, String modelName) {
        String sql = "SELECT make_id FROM vehicle_makes WHERE make_name = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, makeName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int makeId = rs.getInt("make_id");
                String modelSql = "INSERT IGNORE INTO vehicle_models (make_id, model_name) VALUES (?, ?)";
                PreparedStatement modelPs = conn.prepareStatement(modelSql);
                modelPs.setInt(1, makeId);
                modelPs.setString(2, modelName);
                return modelPs.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error storing vehicle model: " + e.getMessage());
            return false;
        }
        return false;
    }

    /**
     * Get all incidents for a specific user.
     */
    public List<Map<String, Object>> getIncidentsByUser(String userId, int limit, int offset) {
        List<Map<String, Object>> incidents = new ArrayList<>();

        String sql = "SELECT incident_id, device_event_id, user_id, timestamp_ms, latitude, "
                + "longitude, accuracy_meters, distance_cm, time_offset_ms, is_anonymized "
                + "FROM incidents WHERE user_id = ? ORDER BY timestamp_ms DESC LIMIT ? OFFSET ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            ps.setInt(2, limit);
            ps.setInt(3, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> incident = new HashMap<>();
                    incident.put("incident_id", rs.getLong("incident_id"));
                    incident.put("device_event_id", rs.getInt("device_event_id"));
                    incident.put("user_id", rs.getString("user_id"));
                    incident.put("timestamp_ms", rs.getLong("timestamp_ms"));
                    incident.put("latitude", rs.getDouble("latitude"));
                    incident.put("longitude", rs.getDouble("longitude"));
                    incident.put("accuracy_meters", rs.getFloat("accuracy_meters"));
                    incident.put("distance_cm", rs.getInt("distance_cm"));
                    incident.put("time_offset_ms", rs.getFloat("time_offset_ms"));
                    incident.put("is_anonymized", rs.getBoolean("is_anonymized"));
                    incidents.add(incident);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching incidents: " + e.getMessage());
        }

        return incidents;
    }

    /**
     * Get incidents across all users, newest first, joined with any reported
     * offender vehicle. Used by the web front end.
     */
    public List<Map<String, Object>> getAllIncidents(int limit, int offset) {
        List<Map<String, Object>> incidents = new ArrayList<>();

        String sql = "SELECT i.incident_id, i.user_id, i.device_event_id, i.timestamp_ms, i.latitude, "
                + "i.longitude, i.accuracy_meters, i.distance_cm, i.time_offset_ms, i.is_anonymized, "
                + "i.additional_comments, v.license_plate, v.state_or_region, v.vehicle_make, v.vehicle_model, v.vehicle_color "
                + "FROM incidents i "
                + "LEFT JOIN offender_vehicles v ON v.incident_id = i.incident_id "
                + "ORDER BY i.timestamp_ms DESC LIMIT ? OFFSET ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, limit);
            ps.setInt(2, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> incident = new HashMap<>();
                    incident.put("incident_id", rs.getLong("incident_id"));
                    incident.put("user_id", rs.getString("user_id"));
                    incident.put("device_event_id", rs.getInt("device_event_id"));
                    incident.put("timestamp_ms", rs.getLong("timestamp_ms"));
                    incident.put("latitude", rs.getDouble("latitude"));
                    incident.put("longitude", rs.getDouble("longitude"));
                    incident.put("accuracy_meters", rs.getFloat("accuracy_meters"));
                    incident.put("distance_cm", rs.getInt("distance_cm"));
                    incident.put("time_offset_ms", rs.getFloat("time_offset_ms"));
                    incident.put("is_anonymized", rs.getBoolean("is_anonymized"));
                    incident.put("additional_comments", rs.getString("additional_comments"));
                    incident.put("license_plate", rs.getString("license_plate"));
                    incident.put("state_or_region", rs.getString("state_or_region"));
                    incident.put("vehicle_make", rs.getString("vehicle_make"));
                    incident.put("vehicle_model", rs.getString("vehicle_model"));
                    incident.put("vehicle_color", rs.getString("vehicle_color"));
                    incidents.add(incident);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching all incidents: " + e.getMessage());
        }

        return incidents;
    }

    /**
     * Replace the additional comments of an incident.
     *
     * @return true if the incident exists and was saved, false otherwise.
     */
    public boolean updateAdditionalComments(long incidentId, String additionalComments) {
        String sql = "UPDATE incidents SET additional_comments = ? WHERE incident_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, additionalComments);
            ps.setLong(2, incidentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating additional comments: " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete an incident by its server-assigned incident_id. Linked photos and
     * offender vehicle rows are removed by the foreign key cascade.
     *
     * @return true if a row was deleted, false otherwise.
     */
    public boolean deleteIncident(long incidentId) {
        String sql = "DELETE FROM incidents WHERE incident_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, incidentId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting incident: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get all incident images for a specific incident_id.
     */
    public List<Map<String, Object>> getIncidentPhotos(long incidentId) {
        List<Map<String, Object>> photos = new ArrayList<>();

        String sql = "SELECT image_index, format, image_data, contains_sensitive_data "
                + "FROM incident_photos WHERE incident_id = ? ORDER BY image_index";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, incidentId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> photo = new HashMap<>();
                    photo.put("image_index", rs.getInt("image_index"));
                    photo.put("format", rs.getString("format"));
                    photo.put("photo_data", rs.getBytes("image_data"));
                    photo.put("contains_sensitive_data", rs.getBoolean("contains_sensitive_data"));
                    photos.add(photo);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching incident photos: " + e.getMessage());
        }

        return photos;
    }

    public enum CreateUserResult {
        CREATED,
        DUPLICATE,
        ERROR
    }

    /**
     * Look up an account by username or email. Used by the login flow so a
     * user can sign in with either one.
     *
     * @return the user row, or null when no account matches or the database
     *         cannot be reached.
     */
    public Map<String, Object> findUserByUsernameOrEmail(String usernameOrEmail) {
        String sql = "SELECT user_id, username_or_email, email, password_hash, is_admin "
                + "FROM users WHERE username_or_email = ? OR email = ? LIMIT 1";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, usernameOrEmail);
            ps.setString(2, usernameOrEmail);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> user = new HashMap<>();
                user.put("user_id", rs.getString("user_id"));
                user.put("username_or_email", rs.getString("username_or_email"));
                user.put("email", rs.getString("email"));
                user.put("password_hash", rs.getString("password_hash"));
                user.put("is_admin", rs.getBoolean("is_admin"));
                return user;
            }
        } catch (SQLException e) {
            System.err.println("Error fetching user: " + e.getMessage());
            return null;
        }
    }

    /**
     * Insert a new account. The username and email columns are unique, so a
     * duplicate registration is reported instead of creating a second row.
     */
    public CreateUserResult createUser(String userId, String username, String email,
                                       String passwordHash, boolean isAdmin) {
        String sql = "INSERT INTO users (user_id, username_or_email, email, password_hash, is_admin) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            ps.setString(2, username);
            ps.setString(3, email);
            ps.setString(4, passwordHash);
            ps.setBoolean(5, isAdmin);
            ps.executeUpdate();
            return CreateUserResult.CREATED;
        } catch (SQLIntegrityConstraintViolationException e) {
            return CreateUserResult.DUPLICATE;
        } catch (SQLException e) {
            System.err.println("Error creating user: " + e.getMessage());
            return CreateUserResult.ERROR;
        }
    }

    public List<String> getAllVehicleMakes() {
        List<String> makes = new ArrayList<>();
        String sql = "SELECT make_name FROM vehicle_makes ORDER BY make_name";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                makes.add(rs.getString("make_name"));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching vehicle makes: " + e.getMessage());
        }

        return makes;
    }

    public List<String> getAllVehicleModels(String makeName) {
        List<String> models = new ArrayList<>();
        String sql = "SELECT vm.model_name FROM vehicle_models vm "
                + "JOIN vehicle_makes vma ON vm.make_id = vma.make_id "
                + "WHERE vma.make_name = ? ORDER BY vm.model_name";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, makeName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    models.add(rs.getString("model_name"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching vehicle models: " + e.getMessage());
        }

        return models;
    }



    private static boolean hasVehicleData(OffenderVehicle vehicle) {
        return !vehicle.getLicensePlate().isEmpty()
                || !vehicle.getStateOrRegion().isEmpty()
                || !vehicle.getVehicleMake().isEmpty()
                || !vehicle.getVehicleModel().isEmpty()
                || !vehicle.getVehicleColor().isEmpty();
    }

    private static void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // nothing we can do here
            }
        }
    }

    /**
     * Look up a user by username or email. Returns null when not found.
     * Default implementation returns null (no database).
     */
    public Map<String, Object> findUserByUsernameOrEmail(String usernameOrEmail) {
        return null;
    }

    /**
     * Create a new user account. Default implementation reports unavailable.
     */
    public CreateUserResult createUser(String userId, String username, String email,
                                      String passwordHash, boolean isAdmin) {
        return CreateUserResult.UNAVAILABLE;
    }

    /**
     * Update the username (username_or_email) for the given user id.
     */
    public boolean updateUsername(String userId, String newUsername) {
        return updateUserUsername(userId, newUsername);
    }

    public boolean setUsername(String userId, String newUsername) {
        return updateUserUsername(userId, newUsername);
    }

    public boolean renameUser(String userId, String newUsername) {
        return updateUserUsername(userId, newUsername);
    }

    public boolean updateAccountUsername(String userId, String newUsername) {
        return updateUserUsername(userId, newUsername);
    }

    public boolean updateUserName(String userId, String newUsername) {
        return updateUserUsername(userId, newUsername);
    }

    /**
     * Update the username (username_or_email) for the given user id.
     */
    public boolean updateUserUsername(String userId, String newUsername) {
        String sql = "UPDATE users SET username_or_email = ? WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newUsername);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating username: " + e.getMessage());
            return false;
        }
    }

    private static void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
                // nothing we can do here
            }
        }
    }
}
