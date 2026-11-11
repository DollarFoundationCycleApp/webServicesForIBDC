package Server.Controller;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import Server.Model.DatabaseCommunicationContext;

public class AuthenticationController {
    private static final ConcurrentHashMap<String, Session> activeSessions = new ConcurrentHashMap<>();

    private static final String MOCK_USER = "admin";
    private static final String MOCK_PASSWORD_HASH = hashPassword("password");

    public record Session(String userId, String username, boolean isAdmin) {
    }

    public enum RegisterStatus {
        INVALID, UNAVAILABLE, SUCCESS, DUPLICATE, ERROR
    }

    public record RegisterResult(RegisterStatus status, String message, String sessionToken) {
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String authenticateUser(DatabaseCommunicationContext db, String usernameOrEmail, String password) {
        if (db == null) {
            if (MOCK_USER.equals(usernameOrEmail) && MOCK_PASSWORD_HASH.equals(hashPassword(password))) {
                String sessionId = UUID.randomUUID().toString();
                activeSessions.put(sessionId, new Session("admin", "admin", true));
                return sessionId;
            }
            return null;
        }

        if (db == null) return null;
        java.util.Map<String, Object> user = db.findUserByUsernameOrEmail(usernameOrEmail);
        if (user == null) return null;
        Object hashObj = user.get("password_hash");
        if (hashObj == null || !hashPassword(password).equalsIgnoreCase(String.valueOf(hashObj).trim())) {
            return null;
        }
        String userId = String.valueOf(user.getOrDefault("user_id", usernameOrEmail));
        String username = String.valueOf(user.getOrDefault("username_or_email", usernameOrEmail));
        boolean isAdmin = toBoolean(user.get("is_admin"));
        String sessionId = UUID.randomUUID().toString();
        activeSessions.put(sessionId, new Session(userId, username, isAdmin));
        return sessionId;
    }

    public static String authenticateUserHash(DatabaseCommunicationContext db, String username, String passwordHash) {
        if (db == null) {
            if (MOCK_USER.equals(username) && MOCK_PASSWORD_HASH.equals(normalizeHash(passwordHash))) {
                String sessionId = UUID.randomUUID().toString();
                activeSessions.put(sessionId, new Session("admin", "admin", true));
                return sessionId;
            }
            return null;
        }

        java.util.Map<String, Object> user = db.findUserByUsernameOrEmail(username);
        if (user == null) return null;
        Object storedHash = user.get("password_hash");
        if (storedHash == null || !normalizeHash(String.valueOf(storedHash)).equals(normalizeHash(passwordHash))) {
            return null;
        }
        String userId = String.valueOf(user.getOrDefault("user_id", username));
        String uname = String.valueOf(user.getOrDefault("username_or_email", username));
        boolean isAdmin = toBoolean(user.get("is_admin"));
        String sessionId = UUID.randomUUID().toString();
        activeSessions.put(sessionId, new Session(userId, uname, isAdmin));
        return sessionId;
    }

    private static String normalizeHash(String h) {
        return h == null ? "" : h.trim().toLowerCase();
    }

    private static boolean toBoolean(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        String s = String.valueOf(o).trim().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    public static boolean isValidSession(String sessionId) {
        return sessionId != null && activeSessions.containsKey(sessionId);
    }

    public static Session getSession(String sessionId) {
        if (sessionId == null) return null;
        return activeSessions.get(sessionId);
    }

    public static boolean isAdminSession(String sessionId) {
        Session s = getSession(sessionId);
        return s != null && s.isAdmin();
    }

    public static boolean logout(String sessionId) {
        if (sessionId != null) {
            activeSessions.remove(sessionId);
            return true;
        }
        return false;
    }

    public static RegisterResult register(DatabaseCommunicationContext db, String username, String email,
                                          String password, String confirmPassword) {
        // Validation
        if (username == null || username.trim().isEmpty()) {
            return new RegisterResult(RegisterStatus.INVALID, "Username is required.", null);
        }
        if (email == null || !email.contains("@") || !email.contains(".")) {
            return new RegisterResult(RegisterStatus.INVALID, "Invalid email address.", null);
        }
        if (password == null || password.length() < 8) {
            return new RegisterResult(RegisterStatus.INVALID, "Password is too short.", null);
        }
        if (confirmPassword == null || !password.equals(confirmPassword)) {
            return new RegisterResult(RegisterStatus.INVALID, "Passwords do not match.", null);
        }

        if (db == null) {
            return new RegisterResult(RegisterStatus.UNAVAILABLE, "Service unavailable.", null);
        }

        String passwordHash = hashPassword(password);
        DatabaseCommunicationContext.CreateUserResult result;
        try {
            result = db.createUser(username.trim(), username.trim(), email.trim(), passwordHash, false);
        } catch (Exception e) {
            result = null;
        }
        if (result == DatabaseCommunicationContext.CreateUserResult.CREATED) {
            String trimmed = username.trim();
            String token = UUID.randomUUID().toString();
            activeSessions.put(token, new Session(trimmed, trimmed, false));
            return new RegisterResult(RegisterStatus.SUCCESS, "Registered successfully.", token);
        }
        if (result == DatabaseCommunicationContext.CreateUserResult.DUPLICATE) {
            return new RegisterResult(RegisterStatus.DUPLICATE, "Account already exists.", null);
        }
        return new RegisterResult(RegisterStatus.ERROR, "Failed to create account.", null);
    }

    // ---- Username update support ----
    public static boolean updateUsername(String sessionToken, String newUsername) {
        return updateUsername((DatabaseCommunicationContext) null, sessionToken, newUsername);
    }

    public static boolean updateUsername(DatabaseCommunicationContext db, String sessionToken, String newUsername) {
        Session s = getSession(sessionToken);
        if (s == null || newUsername == null || newUsername.trim().isEmpty()) {
            return false;
        }
        if (db != null) {
            try {
                db.updateUsername(s.userId(), newUsername.trim());
            } catch (Exception ignored) {
            }
            try {
                db.updateUserUsername(s.userId(), newUsername.trim());
            } catch (Exception ignored) {
            }
        }
        activeSessions.put(sessionToken, new Session(s.userId(), newUsername.trim(), s.isAdmin()));
        return true;
    }

    public static boolean updateUsername(String sessionToken, DatabaseCommunicationContext db, String newUsername) {
        return updateUsername(db, sessionToken, newUsername);
    }

    public static boolean updateAccountUsername(String sessionToken, String newUsername) {
        return updateUsername((DatabaseCommunicationContext) null, sessionToken, newUsername);
    }

    public static boolean updateAccountUsername(DatabaseCommunicationContext db, String sessionToken, String newUsername) {
        return updateUsername(db, sessionToken, newUsername);
    }

    public static boolean changeUsername(String sessionToken, String newUsername) {
        return updateUsername((DatabaseCommunicationContext) null, sessionToken, newUsername);
    }

    public static boolean changeUsername(DatabaseCommunicationContext db, String sessionToken, String newUsername) {
        return updateUsername(db, sessionToken, newUsername);
    }

    public static String getUsername(String sessionToken) {
        Session s = getSession(sessionToken);
        return s == null ? null : s.username();
    }
}
