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
import org.springframework.core.io.ClassPathResource;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import Server.Controller.AuthenticationController;
import Server.Model.DatabaseCommunicationContext;
import Server.Model.Incident;
import jakarta.servlet.http.HttpSession;
import tools.jackson.databind.json.JsonMapper;

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

    // Matches the textarea's maxlength; fits in the TEXT column even at 4 bytes per character
    private static final int MAX_ADDITIONAL_COMMENTS_LENGTH = 5000;

    private final DatabaseCommunicationContext db;

    public WebController(DatabaseCommunicationContext db) {
        this.db = db;
        
        // Load sample vehicle makes and models into the database
        for (Incident sample : SAMPLE_INCIDENTS) {
            db.storeVehicleMake(sample.vehicleMake());
            db.storeVehicleModel(sample.vehicleMake(), sample.vehicleModel());
        }
    }
    
    // Load sample incidents from JSON file
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final List<Incident> SAMPLE_INCIDENTS = loadSampleIncidents();

    private record SampleFile(List<Incident> incidents) {}

    private static List<Incident> loadSampleIncidents() {
        try {
            return List.copyOf(MAPPER.readValue(new ClassPathResource("/SampleIncidents.json").getInputStream(),
                    SampleFile.class).incidents());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load sample incidents", e);
        }
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

    private static List<Incident> newestFirst(List<Incident> incidents) {
        return incidents.stream()
                .sorted(Comparator.comparing(Incident::createdTime).reversed())
                .toList();
    }

    // Private fields are masked for everyone but admins
    private static List<Incident> visibleTo(String session, List<Incident> incidents) {
        if (AuthenticationController.isAdminSession(session)) {
            return incidents;
        }
        return incidents.stream().map(incident -> Incident.hideInfo(incident)).toList();
    }

    private static Incident findIncident(IncidentData data, String id) {
        return data.incidents().stream()
                .filter(i -> i.incidentId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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
                Boolean.TRUE.equals(row.get("is_anonymized")),
                text(row.get("additional_comments")));
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
        model.addAttribute("incidents", visibleTo(session, newestFirst(loadIncidentData().incidents())));
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
                        fields.put("injurySeverity", incident.injurySeverity());
                        fields.put("driverPresent", incident.driverPresent());
                        fields.put("vehicleMake", incident.vehicleMake());
                        fields.put("vehicleModel", incident.vehicleModel());
                        fields.put("vehicleColor", incident.vehicleColor());
                        fields.put("vehicleYear", incident.vehicleYear());
                        fields.put("createdTime", incident.createdTime().format(DISPLAY_TIME));
                        fields.put("hideInfo", incident.hideInfo());
                        
                        if (!incident.hideInfo()) {
                                fields.put("imageId", incident.imageId());
                                fields.put("licensePlate", incident.licensePlate());
                                fields.put("driverInformation", incident.driverInformation());
                                fields.put("extraComment", incident.extraComment());
                        }
                        return fields;
                }).toList());
        return "heatmapView";
    }

    /**
     * Send the most recent incident to its detail page. With no incidents the
     * page shows an empty state instead.
     */
    @GetMapping({"/incident"})
    public String incidents(@CookieValue(value = "SESSION", required = false) String session, Model model) {
        if (!AuthenticationController.isValidSession(session)) {
            return "redirect:/login";
        }

        List<Incident> incidents = newestFirst(loadIncidentData().incidents());
        if (incidents.isEmpty()) {
            model.addAttribute("incidents", incidents);
            return "incidentView";
        }
        return "redirect:/incident/" + incidents.get(0).incidentId(); }

    /**
     * The picker's no-script fallback posts the chosen id here, which is
     * redirected to the incident's own path so only /incident/{id} is used.
     */
    @PostMapping({"/incident"})
    public String pickIncident(@CookieValue(value = "SESSION", required = false) String session,
                @RequestParam String id) {
        if (!AuthenticationController.isValidSession(session)) {
            return "redirect:/login";
        }
        return "redirect:/incident/" + findIncident(loadIncidentData(), id).incidentId(); }

    @GetMapping({"/incident/{id}"})
    public String incident(@CookieValue(value = "SESSION", required = false) String session,
                @PathVariable String id, Model model) {
        if (!AuthenticationController.isValidSession(session)) {
            return "redirect:/login";
        }

        IncidentData data = loadIncidentData();
        Incident incident = findIncident(data, id);

        // Private fields are masked for everyone but admins
        boolean canSeePrivate = AuthenticationController.isAdminSession(session);

        List<Incident> incidents = newestFirst(data.incidents());
        model.addAttribute("incidents", visibleTo(session, incidents));

        // Neighbours in the newest-first list drive the picker's step buttons
        int position = incidents.indexOf(incident);
        model.addAttribute("incidentPosition", position + 1);
        model.addAttribute("newerIncidentId", position > 0 ? incidents.get(position - 1).incidentId() : null);
        model.addAttribute("olderIncidentId", position >= 0 && position < incidents.size() - 1
                ? incidents.get(position + 1).incidentId() : null);

        model.addAttribute("incident", canSeePrivate ? incident : Incident.hideInfo(incident));
        model.addAttribute("canSeePrivate", canSeePrivate);
        model.addAttribute("databaseIncidentIds", data.databaseIds());
        return "incidentView";
    }

    /**
     * Save the additional comments of a database incident. Any signed-in user
     * may edit them. Sample incidents have no row to save to, so they report
     * a failed save.
     */
    @PostMapping({"/incident/{id}/comments"})
    public String saveAdditionalComments(@CookieValue(value = "SESSION", required = false) String session,
                @PathVariable String id, @RequestParam String additionalComments) {
        if (!AuthenticationController.isValidSession(session)) {
            return "redirect:/login";
        }

        // Browsers send line breaks as \r\n, but maxlength counts them as one character
        String comments = additionalComments.replace("\r\n", "\n").strip();
        if (comments.length() > MAX_ADDITIONAL_COMMENTS_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Additional comments are too long");
        }

        IncidentData data = loadIncidentData();
        String incidentId = findIncident(data, id).incidentId();
        boolean saved = db != null && data.databaseIds().contains(incidentId)
                && db.updateAdditionalComments(Long.parseLong(incidentId), comments.isEmpty() ? null : comments);
        return "redirect:/incident/" + incidentId + "?commentsSaved=" + saved; }

    @PostMapping({"/incident/{id}/delete"})
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
        return "redirect:/incident"; }

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

        // Photos of private incidents are only served to admins
        if (!AuthenticationController.isAdminSession(session)
                && findIncident(loadIncidentData(), String.valueOf(id)).hideInfo()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
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


    @PostMapping({"/reports"})
    public String searchReports(@CookieValue(value = "SESSION", required = false) String session,
                    @RequestParam(defaultValue = "") String keyword,
                    @RequestParam(defaultValue = "10") int limit,
                    @RequestParam (defaultValue = "") String vehicleMake,
                    @RequestParam (defaultValue = "") String vehicleModel,
                    @RequestParam (defaultValue = "") String vehicleColor,
                    @RequestParam (defaultValue = "-1") int startYear,
                    @RequestParam (defaultValue = "-1") int endYear,
                    HttpSession httpSession) {
        if (!AuthenticationController.isValidSession(session)) {
            return "redirect:/login";
        }

        httpSession.setAttribute("keyword", keyword.trim());
        httpSession.setAttribute("vehicleMake", vehicleMake.trim());
        httpSession.setAttribute("vehicleModel", vehicleModel.trim());
        httpSession.setAttribute("vehicleColor", vehicleColor.trim());
        httpSession.setAttribute("startYear", startYear);
        httpSession.setAttribute("endYear", endYear);

        return "redirect:/reports?limit=" + limit; 
    }

    @GetMapping({"/reports"})
    public String reports(@CookieValue(value = "SESSION", required = false) String session, 
                        @RequestParam(defaultValue = "1") int pageNum, 
                        @RequestParam(defaultValue = "10") int limit, 
                        HttpSession httpSession, Model model) { 
        if (!AuthenticationController.isValidSession(session)) {
            return "redirect:/login";
        }
        if (limit <= 0) limit = 10; 


        String keyword = (String) httpSession.getAttribute("keyword");
        String vehicleMake = (String) httpSession.getAttribute("vehicleMake");
        String vehicleModel = (String) httpSession.getAttribute("vehicleModel");
        String vehicleColor = (String) httpSession.getAttribute("vehicleColor");
        Integer startYearAtt = (Integer) httpSession.getAttribute("startYear");
        int startYear = startYearAtt == null ? -1 : startYearAtt;
        Integer endYearAtt = (Integer) httpSession.getAttribute("endYear");
        int endYear = endYearAtt == null ? -1 : endYearAtt;

        List<Incident> allIncidents = loadIncidentData().incidents().stream()
                .filter(incident -> keywordFilter(incident, keyword))
                .filter(incident -> vehicleFilter(incident, vehicleMake, vehicleModel, vehicleColor))
                .filter(incident -> yearFilter(incident, startYear, endYear))
                .sorted(Comparator.comparing(Incident::createdTime).reversed())
                .toList();

        int totalPages = Math.max(1, (int) Math.ceil(allIncidents.size() / (double) limit));
        int from = (Math.min(pageNum, totalPages) - 1) * limit;
        int to = Math.min(from + limit, allIncidents.size());
        
        model.addAttribute("incidents", allIncidents.subList(from, to));
        model.addAttribute("limit", limit);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("currentPage", Math.min(pageNum, totalPages));
        model.addAttribute("pageButtons", pageButtons(Math.min(pageNum, totalPages), totalPages));
        model.addAttribute("vehicleMakes", db.getAllVehicleMakes());
        model.addAttribute("vehicleModels", db.getAllVehicleModels(vehicleMake));
        model.addAttribute("keyword", keyword);
        model.addAttribute("vehicleMake", vehicleMake);
        model.addAttribute("vehicleModel", vehicleModel);
        model.addAttribute("vehicleColor", vehicleColor);
        model.addAttribute("startYear", startYear);
        model.addAttribute("endYear", endYear);

        return "reportsView"; 
    }

    @GetMapping({"/api/models"}) @ResponseBody
    public List<String> vehicleModels(@CookieValue(value = "SESSION", required = false) String session, 
                        @RequestParam(defaultValue = "") String vehicleMake) {
        if (!AuthenticationController.isValidSession(session)) { 
            return List.of(); 
        }

        return db.getAllVehicleModels(vehicleMake);
    }

    private static boolean keywordFilter(Incident incident, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase();
        return incident.incidentId().toLowerCase().contains(lowerKeyword) ||
                incident.driverInformation().toLowerCase().contains(lowerKeyword) ||
                incident.extraComment().toLowerCase().contains(lowerKeyword) ||
                incident.additionalComments().toLowerCase().contains(lowerKeyword);
    }

    private static boolean vehicleFilter(Incident incident, String vehicleMake, String vehicleModel, String vehicleColor) {
        if ((vehicleMake == null || vehicleMake.isEmpty()) && 
            (vehicleModel == null || vehicleModel.isEmpty()) &&
            (vehicleColor == null || vehicleColor.isEmpty())) {
            return true;
        }
        boolean makeMatches = vehicleMake == null || vehicleMake.isEmpty() || 
            incident.vehicleMake().toLowerCase().contains(vehicleMake.toLowerCase());
        boolean modelMatches = vehicleModel == null || vehicleModel.isEmpty() || 
            incident.vehicleModel().toLowerCase().contains(vehicleModel.toLowerCase());
        boolean colorMatches = vehicleColor == null || vehicleColor.isEmpty() || 
            incident.vehicleColor().toLowerCase().contains(vehicleColor.toLowerCase());
        return makeMatches && modelMatches && colorMatches;
    }

    private static boolean yearFilter(Incident incident, int startYear, int endYear) {
        return (startYear == -1 || startYear <= incident.vehicleYear()) && (endYear == -1 || endYear >= incident.vehicleYear());        
    }

    /**
     * Helper to build a list of page buttons
     */
    private static List<Integer> pageButtons(int current, int total) {
    List<Integer> buttons = new ArrayList<>();
    if (total <= 7) {
        for (int i = 1; i <= total; i++) {
            buttons.add(i);
        }
    } else if (current <= 4) {
        for (int i = 1; i <= 5; i++) {
            buttons.add(i);
        }
        buttons.add(-1);
        buttons.add(total);
    } else if (current >= total - 3) {
        buttons.add(1);
        buttons.add(-1);
        for (int i = total - 4; i <= total; i++) {
            buttons.add(i);
        }
    } else {
        buttons.add(1);
        buttons.add(-1);
        for (int i = current - 1; i <= current + 1; i++) {
            buttons.add(i);
        }
        buttons.add(-1);
        buttons.add(total);
    }
    return buttons;
}

    @GetMapping({"/settings"})
    public String settings(@CookieValue(value = "SESSION", required = false) String session) { 
        if (!AuthenticationController.isValidSession(session)) {
            return "redirect:/login";
        }
        return "settingsView"; }
}
