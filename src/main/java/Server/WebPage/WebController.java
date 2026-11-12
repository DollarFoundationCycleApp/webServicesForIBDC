package Server.WebPage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import Server.Controller.AuthenticationController;
import Server.Model.DatabaseCommunicationContext;
import Server.Model.Incident;

@Controller
public class WebController {

    @Value("${map.style-url:}")
    private String mapStyleUrl;

    @Value("${map.center-lat:0}")
    private double mapCenterLat;

    @Value("${map.center-lon:0}")
    private double mapCenterLon;

    @Value("${map.zoom:12}")
    private double mapZoom;

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a");

    // Demo data until incidents come from the app
    private static final List<Incident> SAMPLE_INCIDENTS = List.of(
            new Incident("1", 33.4310, -111.9370, "ABC1234", "img-0001", "Minor",
                    true, "Jane Smith, 602-555-0142", "Clipped by side mirror while passing",
                    "Toyota", "Camry", "Silver", 2019,
                    LocalDateTime.of(2026, 9, 3, 8, 15), true),
            new Incident("2", 33.4935, -111.9195, "XYZ9876", "img-0002", "Moderate",
                    false, "", "Driver left the scene heading north",
                    "Ford", "F-150", "Black", 2021,
                    LocalDateTime.of(2026, 9, 12, 17, 42), false),
            new Incident("3", 33.4210, -112.0735, "LMN4567", "img-0003", "None",
                    true, "Carlos Ruiz, 480-555-0199", "Door opened into bike lane",
                    "Honda", "Civic", "Blue", 2016,
                    LocalDateTime.of(2026, 9, 24, 12, 5), false), 
            // Tempe Beach Park
            new Incident("4", 33.4302, -111.9428, "TMP1001", "img-0004", "Minor",
                    true, "Alex Chen, 480-555-0111", "Close pass on the lake path entrance",
                    "Nissan", "Altima", "White", 2018,
                    LocalDateTime.of(2026, 9, 1, 7, 50), false),
            new Incident("5", 33.4298, -111.9415, "TMP1002", "img-0005", "None",
                    true, "Maria Lopez, 480-555-0123", "Car rolled through crosswalk",
                    "Chevrolet", "Malibu", "Gray", 2020,
                    LocalDateTime.of(2026, 9, 4, 18, 10), true),
            new Incident("6", 33.4306, -111.9436, "TMP1003", "img-0006", "Moderate",
                    false, "", "Hit while turning into parking lot",
                    "Dodge", "Ram 1500", "Red", 2017,
                    LocalDateTime.of(2026, 9, 7, 16, 25), false),
            new Incident("7", 33.4295, -111.9422, "TMP1004", "img-0007", "Minor",
                    true, "Tom Becker, 602-555-0134", "Cut off at Rio Salado Pkwy",
                    "Hyundai", "Elantra", "Black", 2022,
                    LocalDateTime.of(2026, 9, 9, 8, 5), true),
            new Incident("8", 33.4309, -111.9410, "TMP1005", "img-0008", "None",
                    true, "Priya Patel, 480-555-0145", "Driver honked and swerved toward rider",
                    "Subaru", "Outback", "Green", 2015,
                    LocalDateTime.of(2026, 9, 13, 10, 40), true),
            new Incident("9", 33.4300, -111.9440, "TMP1006", "img-0009", "Severe",
                    false, "", "Rider knocked down near the boat ramp",
                    "Jeep", "Wrangler", "Yellow", 2019,
                    LocalDateTime.of(2026, 9, 16, 19, 15), false),
            new Incident("10", 33.4304, -111.9418, "TMP1007", "img-0010", "Minor",
                    true, "Kevin Ortiz, 602-555-0156", "Door opened in front of rider",
                    "Kia", "Sorento", "Silver", 2021,
                    LocalDateTime.of(2026, 9, 19, 13, 30), true),
            new Incident("11", 33.4297, -111.9431, "TMP1008", "img-0011", "None",
                    true, "Hannah Wu, 480-555-0167", "Near miss at event drop-off",
                    "Tesla", "Model 3", "White", 2023,
                    LocalDateTime.of(2026, 9, 22, 17, 55), false),
            new Incident("12", 33.4307, -111.9425, "TMP1009", "img-0012", "Moderate",
                    true, "Derek Hall, 480-555-0178", "Sideswiped leaving the park",
                    "GMC", "Sierra", "Blue", 2016,
                    LocalDateTime.of(2026, 9, 27, 9, 20), false),
            // Scottsdale, Eldorado Park
            new Incident("13", 33.4928, -111.9203, "SCT2001", "img-0013", "Minor",
                    true, "Olivia Grant, 480-555-0189", "Clipped on the greenbelt crossing",
                    "BMW", "X5", "Black", 2020,
                    LocalDateTime.of(2026, 9, 2, 6, 45), false),
            new Incident("14", 33.4941, -111.9188, "SCT2002", "img-0014", "None",
                    true, "Sam Nguyen, 602-555-0191", "Car blocked bike lane, forced swerve",
                    "Lexus", "RX 350", "White", 2018,
                    LocalDateTime.of(2026, 9, 5, 17, 20), true),
            new Incident("15", 33.4933, -111.9210, "SCT2003", "img-0015", "Moderate",
                    false, "", "Hit and run at Miller Rd",
                    "Chevrolet", "Tahoe", "Gray", 2019,
                    LocalDateTime.of(2026, 9, 8, 20, 5), true),
            new Incident("16", 33.4946, -111.9199, "SCT2004", "img-0016", "Minor",
                    true, "Rachel Kim, 480-555-0102", "Right hook at park entrance",
                    "Audi", "Q5", "Blue", 2021,
                    LocalDateTime.of(2026, 9, 11, 7, 35), false),
            new Incident("17", 33.4922, -111.9192, "SCT2005", "img-0017", "None",
                    true, "Mike Diaz, 602-555-0113", "Driver passed too close on McDowell",
                    "Ford", "Explorer", "Red", 2017,
                    LocalDateTime.of(2026, 9, 15, 12, 50),false),
            new Incident("18", 33.4938, -111.9182, "SCT2006", "img-0018", "Severe",
                    true, "Laura Brooks, 480-555-0124", "Rider struck while crossing path",
                    "Toyota", "Tacoma", "Tan", 2022,
                    LocalDateTime.of(2026, 9, 20, 18, 40), true),
            new Incident("19", 33.4930, -111.9206, "SCT2007", "img-0019", "Minor",
                    true, "Ben Carter, 602-555-0135", "Mirror contact while merging",
                    "Mazda", "CX-5", "Silver", 2020,
                    LocalDateTime.of(2026, 9, 25, 8, 10), true),
            new Incident("20", 33.4944, -111.9194, "SCT2008", "img-0020", "None",
                    false, "", "Aggressive driver near the playground lot",
                    "Volkswagen", "Jetta", "Black", 2014,
                    LocalDateTime.of(2026, 9, 29, 16, 0), false));

    private final DatabaseCommunicationContext db;

    public WebController(DatabaseCommunicationContext db) {
        this.db = db;
    }

    /**
     * Merge the sample incidents with those stored in the database. Database
     * incidents replace any sample sharing the same id and add new ones, so the
     * pages gradually fill out with real data as reports arrive. The ids that
     * came from the database are tracked so they can be managed (deleted).
     */
    private IncidentData loadIncidentData() {
        Map<String, Incident> merged = new LinkedHashMap<>();
        for (Incident sample : SAMPLE_INCIDENTS) {
            merged.put(sample.incidentId(), sample);
        }

        Set<String> databaseIds = new HashSet<>();
        if (db != null) {
            for (Map<String, Object> row : db.getAllIncidents(500, 0)) {
                Incident incident = toIncident(row);
                merged.put(incident.incidentId(), incident);
                databaseIds.add(incident.incidentId());
            }
        }
        return new IncidentData(new ArrayList<>(merged.values()), databaseIds);
    }

    private record IncidentData(List<Incident> incidents, Set<String> databaseIds) {
    }

    private static Incident toIncident(Map<String, Object> row) {
        long incidentId = ((Number) row.get("incident_id")).longValue();
        long timestampMs = ((Number) row.get("timestamp_ms")).longValue();
        return new Incident(
                String.valueOf(incidentId),
                ((Number) row.get("latitude")).doubleValue(),
                ((Number) row.get("longitude")).doubleValue(),
                text(row.get("license_plate")),
                "incident-" + incidentId,
                "Unknown",
                false,
                "",
                "",
                text(row.get("vehicle_make")),
                text(row.get("vehicle_model")),
                text(row.get("vehicle_color")),
                0,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMs), ZoneOffset.UTC),
                Boolean.TRUE.equals(row.get("is_anonymized")));
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    @GetMapping({"/", "/login"})
    public String login(@CookieValue(value = "SESSION", required = false) String session) {
        if (AuthenticationController.isValidSession(session)) {
            return "redirect:/dashboard";
        }
        return "loginView"; }

    @GetMapping({"/dashboard"})
    public String dashboard(@CookieValue(value = "SESSION", required = false) String session, Model model) {
        if (!AuthenticationController.isValidSession(session)) {
            return "redirect:/login";
        }
        model.addAttribute("incidents", loadIncidentData().incidents().stream()
                .sorted(Comparator.comparing(Incident::createdTime).reversed())
                .toList());
        long userCount = 0;
        if (db != null) {
            userCount = db.countUsers();
        }
        model.addAttribute("userCount", userCount);
        return "dashboardView";
    }

    @GetMapping({"/heatmap"})
    public String heatmap(@CookieValue(value = "SESSION", required = false) String session, Model model) {
        if (!AuthenticationController.isValidSession(session)) {
            return "redirect:/login";
        }
        model.addAttribute("mapStyleUrl", mapStyleUrl);
        model.addAttribute("mapCenterLat", mapCenterLat);
        model.addAttribute("mapCenterLon", mapCenterLon);
        model.addAttribute("mapZoom", mapZoom);
        // Plain values only, so they serialize cleanly into the page script
        model.addAttribute("incidents", loadIncidentData().incidents().stream()
                .map(incident -> {Map<String, Object> fields = new HashMap<>();
                        fields.put("incidentId", incident.incidentId());
                        fields.put("latitude", incident.latitude());
                        fields.put("longitude", incident.longitude());
                        fields.put("imageId", incident.imageId());
                        fields.put("injurySeverity", incident.injurySeverity());
                        fields.put("driverPresent", incident.driverPresent());
                        fields.put("vehicleMake", incident.vehicleMake());
                        fields.put("vehicleModel", incident.vehicleModel());
                        fields.put("vehicleColor", incident.vehicleColor());
                        fields.put("vehicleYear", incident.vehicleYear());
                        fields.put("createdTime", incident.createdTime().format(DISPLAY_TIME));
                        fields.put("hideInfo", incident.hideInfo());
                        
                        if (!incident.hideInfo()) {
                                fields.put("licensePlate", incident.licensePlate());
                                fields.put("driverInformation", incident.driverInformation());
                                fields.put("extraComment", incident.extraComment());
                        }
                        return fields;
                }).toList());
        return "heatmapView";
    }

    @GetMapping({"/incident/{id}"})
    public String incident(@CookieValue(value = "SESSION", required = false) String session,
                @PathVariable String id, Model model) {
        if (!AuthenticationController.isValidSession(session)) {
            return "redirect:/login";
        }

        Incident incident = loadIncidentData().incidents().stream()
                .filter(i -> id.equals(i.incidentId()))
                .findFirst()
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.NOT_FOUND));

        model.addAttribute("incident", Incident.hideInfo(incident));
        return "incidentView";
    }

    /**
     * Serve the photo stored in the database for a database incident. The
     * database incidents use imageId "incident-{incidentId}", so this mapping
     * lines up with the existing image tags. Missing photos return 404 and the
     * pages fall back to the placeholder image.
     */
    @GetMapping({"/Images/incident-{id}"})
    public ResponseEntity<byte[]> incidentPhoto(@CookieValue(value = "SESSION", required = false) String session,
                @PathVariable long id) {
        if (!AuthenticationController.isValidSession(session) || db == null) {
            return ResponseEntity.notFound().build();
        }

        List<Map<String, Object>> photos = db.getIncidentPhotos(id);
        if (photos.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        byte[] photoData = (byte[]) photos.get(0).get("photo_data");
        if (photoData == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(imageMediaType(text(photos.get(0).get("format"))))
                .body(photoData);
    }

    private static MediaType imageMediaType(String format) {
        if (!format.isEmpty()) {
            try {
                return MediaType.parseMediaType("image/" + format.toLowerCase());
            } catch (IllegalArgumentException ignored) {
                // fall through to the generic type
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @GetMapping({"/reports"})
    public String reports(@CookieValue(value = "SESSION", required = false) String session) { 
        if (!AuthenticationController.isValidSession(session)) {
            return "redirect:/login";
        }
        return "reportsView"; }

    @GetMapping({"/incidents"})
    public String incidents(@CookieValue(value = "SESSION", required = false) String session,
                @RequestParam(required = false) String id, Model model) {
        if (!AuthenticationController.isValidSession(session)) {
            return "redirect:/login";
        }

        IncidentData data = loadIncidentData();
        List<Incident> incidents = data.incidents().stream()
                .sorted(Comparator.comparing(Incident::createdTime).reversed())
                .toList();

        // Most recent incident unless one was picked from the dropdown
        Incident incident = incidents.stream()
                .filter(i -> i.incidentId().equals(id))
                .findFirst()
                .orElse(incidents.get(0));

        model.addAttribute("incidents", incidents);
        model.addAttribute("incident", incident);
        model.addAttribute("databaseIncidentIds", data.databaseIds());
        return "myIncidentsView"; }

    @PostMapping({"/incidents/{id}/delete"})
    public String deleteIncident(@CookieValue(value = "SESSION", required = false) String session,
                @PathVariable String id) {
        if (!AuthenticationController.isValidSession(session)) {
            return "redirect:/login";
        }
        if (db != null) {
            try {
                db.deleteIncident(Long.parseLong(id));
            } catch (NumberFormatException e) {
                // Sample incidents use non-numeric ids and have nothing to delete
            }
        }
        return "redirect:/incidents"; }

    @GetMapping({"/settings"})
    public String settings(@CookieValue(value = "SESSION", required = false) String session) { 
        if (!AuthenticationController.isValidSession(session)) {
            return "redirect:/login";
        }
        return "settingsView"; }
}
