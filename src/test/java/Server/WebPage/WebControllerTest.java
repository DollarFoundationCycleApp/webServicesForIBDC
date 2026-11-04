package Server.WebPage;

import Server.Controller.AuthenticationController;
import Server.Model.DatabaseCommunicationContext;
import Server.Model.Incident;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

public class WebControllerTest {

    /**
     * Heatmap page returns its view and passes the map settings to it.
     * Runs without starting the server.
     */
    @Test
    public void heatmapProvidesMapSettings() {
        ExtendedModelMap model = new ExtendedModelMap();

        String session = AuthenticationController.authenticateUser("admin", "password");

        String view = new WebController(null).heatmap(session, model);

        assertEquals("heatmapView", view);
        assertTrue(model.containsAttribute("mapStyleUrl"));
        assertTrue(model.containsAttribute("mapCenterLat"));
        assertTrue(model.containsAttribute("mapCenterLon"));
        assertTrue(model.containsAttribute("mapZoom"));
        assertTrue(model.containsAttribute("incidents"));
    }

    /** Records comment saves and serves three owned database incidents. */
    private static class FakeDatabase extends DatabaseCommunicationContext {
        long savedId = -1;
        String savedComments;
        long deletedId = -1;

        FakeDatabase() {
            super("", 0, "", "", "");
        }

        static Map<String, Object> row(long incidentId, String ownerId, boolean anonymized, long timestampMs) {
            Map<String, Object> row = new HashMap<>();
            row.put("incident_id", incidentId);
            row.put("user_id", ownerId);
            row.put("timestamp_ms", timestampMs);
            row.put("latitude", 33.0);
            row.put("longitude", -111.0);
            row.put("is_anonymized", anonymized);
            row.put("additional_comments", "stored text");
            return row;
        }

        /** 100 and 300 belong to user-1, 200 belongs to user-2. 100 is private. */
        @Override
        public List<Map<String, Object>> getAllIncidents(int limit, int offset) {
            return List.of(
                    row(100L, "user-1", true, 1_000L),
                    row(200L, "user-2", false, 2_000L),
                    row(300L, "user-1", false, 3_000L));
        }

        @Override
        public List<Map<String, Object>> getIncidentPhotos(long incidentId) {
            Map<String, Object> photo = new HashMap<>();
            photo.put("photo_data", new byte[] {1});
            photo.put("format", "jpeg");
            return List.of(photo);
        }

        @Override
        public boolean updateAdditionalComments(long incidentId, String additionalComments) {
            savedId = incidentId;
            savedComments = additionalComments;
            return true;
        }

        @Override
        public boolean deleteIncident(long incidentId) {
            deletedId = incidentId;
            return true;
        }
    }

    /** Pretend a session token belongs to the given account. */
    private static MockedStatic<AuthenticationController> asUser(String token, String userId, boolean admin) {
        MockedStatic<AuthenticationController> auth = mockStatic(AuthenticationController.class);
        auth.when(() -> AuthenticationController.isValidSession(token)).thenReturn(true);
        auth.when(() -> AuthenticationController.getSession(token))
                .thenReturn(new AuthenticationController.Session(userId, "account", admin));
        auth.when(() -> AuthenticationController.isAdminSession(token)).thenReturn(admin);
        return auth;
    }

    @Test
    public void savesAdditionalCommentsToDatabaseIncident() {
        String session = AuthenticationController.authenticateUser("admin", "password");
        FakeDatabase db = new FakeDatabase();

        String view = new WebController(db).saveAdditionalComments(session, "100", "  line one\r\nline two ");

        assertEquals("redirect:/incident/100?commentsSaved=true", view);
        assertEquals(100L, db.savedId);
        assertEquals("line one\nline two", db.savedComments);
    }

    @Test
    public void sampleIncidentCommentsAreNotSaved() {
        String session = AuthenticationController.authenticateUser("admin", "password");
        FakeDatabase db = new FakeDatabase();

        String view = new WebController(db).saveAdditionalComments(session, "1", "text");

        assertEquals("redirect:/incident/1?commentsSaved=false", view);
        assertNull(db.savedComments);
    }

    @Test
    public void commentsAreRejectedWhenSignedOutOrTooLong() {
        String session = AuthenticationController.authenticateUser("admin", "password");
        FakeDatabase db = new FakeDatabase();
        WebController controller = new WebController(db);

        assertEquals("redirect:/login", controller.saveAdditionalComments(null, "100", "text"));
        assertThrows(ResponseStatusException.class,
                () -> controller.saveAdditionalComments(session, "100", "x".repeat(5001)));
        assertNull(db.savedComments);
    }

    /** A signed-in account that is not an admin cannot add notes. */
    @Test
    public void nonAdminCannotSaveAdditionalComments() {
        DatabaseCommunicationContext stub = new DatabaseCommunicationContext("", 0, "", "", "") {
            @Override
            public CreateUserResult createUser(String userId, String username, String email,
                                               String passwordHash, boolean isAdmin) {
                return CreateUserResult.CREATED;
            }
        };
        String userSession = AuthenticationController.register(
                stub, "rider", "rider@example.com", "password123", "password123").sessionToken();
        FakeDatabase db = new FakeDatabase();

        ResponseStatusException denied = assertThrows(ResponseStatusException.class,
                () -> new WebController(db).saveAdditionalComments(userSession, "100", "text"));

        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertNull(db.savedComments);
    }

    /** The sidebar information is available to every page. */
    @Test
    public void profileModelExposesSignedInAccount() {
        String admin = AuthenticationController.authenticateUser("admin", "password");
        ExtendedModelMap adminModel = new ExtendedModelMap();

        new WebController(null).addCurrentUser(admin, adminModel);

        assertEquals("admin", adminModel.getAttribute("currentUsername"));
        assertEquals(Boolean.TRUE, adminModel.getAttribute("currentUserIsAdmin"));

        ExtendedModelMap guestModel = new ExtendedModelMap();
        new WebController(null).addCurrentUser(null, guestModel);

        assertEquals("", guestModel.getAttribute("currentUsername"));
        assertEquals(Boolean.FALSE, guestModel.getAttribute("currentUserIsAdmin"));
    }

    @Test
    public void privateIncidentsStillShowAdditionalComments() {
        Incident incident = new Incident("1", 0, 0, "PLATE", "img", "None", false, "driver", "comment",
                "", "", "", 0, LocalDateTime.now(), true, "shared");

        Incident hidden = Incident.hideInfo(incident);

        assertNull(hidden.licensePlate());
        assertEquals("shared", hidden.additionalComments());
    }

    @Test
    public void hidingInfoRemovesEveryPrivateField() {
        Incident incident = new Incident("1", 0, 0, "PLATE", "img", "None", false, "driver", "comment",
                "", "", "", 0, LocalDateTime.now(), true, "shared");

        Incident hidden = Incident.hideInfo(incident);

        assertNull(hidden.licensePlate());
        assertNull(hidden.driverInformation());
        assertNull(hidden.extraComment());
        assertNull(hidden.imageId());
    }

    /** Sample incident 3 is marked private. */
    @Test
    @SuppressWarnings("unchecked")
    public void heatmapOmitsPrivateFieldsOfPrivateIncidents() {
        ExtendedModelMap model = new ExtendedModelMap();
        String session = AuthenticationController.authenticateUser("admin", "password");

        new WebController(null).heatmap(session, model);

        Map<String, Object> incident = ((List<Map<String, Object>>) model.getAttribute("incidents")).stream()
                .filter(fields -> "3".equals(fields.get("incidentId")))
                .findFirst()
                .orElseThrow();
        assertFalse(incident.containsKey("licensePlate"));
        assertFalse(incident.containsKey("driverInformation"));
        assertFalse(incident.containsKey("extraComment"));
        assertFalse(incident.containsKey("imageId"));
    }

    /**
     * A regular account only sees incidents it reported; admin-only pages
     * become a redirect to My Incidents.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void nonAdminOnlySeesOwnIncidentsAndIsBlockedFromAdminPages() {
        WebController controller = new WebController(new FakeDatabase());

        try (MockedStatic<AuthenticationController> auth = asUser("user", "user-1", false)) {
            ExtendedModelMap incidentPage = new ExtendedModelMap();
            assertEquals("incidentView", controller.incident("user", "300", incidentPage));

            List<Incident> picker = (List<Incident>) incidentPage.getAttribute("incidents");
            assertEquals(List.of("300", "100"), picker.stream().map(Incident::incidentId).toList());

            assertEquals("redirect:/incident", controller.dashboard("user", new ExtendedModelMap()));
            assertEquals("redirect:/incident", controller.reports("user"));
        }
    }

    /** Typing another account's incident id, or a sample id, sends them back. */
    @Test
    public void nonAdminIsRedirectedAwayFromIncidentsTheyCannotSee() {
        WebController controller = new WebController(new FakeDatabase());

        try (MockedStatic<AuthenticationController> auth = asUser("user", "user-1", false)) {
            assertEquals("redirect:/incident",
                    controller.incident("user", "200", new ExtendedModelMap()));

            assertEquals("redirect:/incident",
                    controller.incident("user", "3", new ExtendedModelMap()));
        }
    }

    @Test
    public void nonAdminPickerCannotJumpToAnotherUsersIncident() {
        WebController controller = new WebController(new FakeDatabase());

        try (MockedStatic<AuthenticationController> auth = asUser("user", "user-1", false)) {
            assertEquals("redirect:/incident/300", controller.pickIncident("user", "300"));
            assertEquals("redirect:/incident", controller.pickIncident("user", "200"));
        }
    }

    @Test
    public void nonAdminWithNoOwnIncidentsSeesEmptyState() {
        WebController controller = new WebController(new FakeDatabase());

        try (MockedStatic<AuthenticationController> auth = asUser("user", "user-9", false)) {
            ExtendedModelMap model = new ExtendedModelMap();

            assertEquals("incidentView", controller.incidents("user", model));
            assertEquals(List.of(), model.getAttribute("incidents"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void adminStillSeesEveryIncidentAndAdminPages() {
        String admin = AuthenticationController.authenticateUser("admin", "password");
        WebController controller = new WebController(new FakeDatabase());

        ExtendedModelMap page = new ExtendedModelMap();
        assertEquals("incidentView", controller.incident(admin, "200", page));

        List<Incident> picker = (List<Incident>) page.getAttribute("incidents");
        assertTrue(picker.stream().anyMatch(i -> i.incidentId().equals("300")));
        assertTrue(picker.stream().anyMatch(i -> i.incidentId().equals("200")));

        assertEquals("dashboardView", controller.dashboard(admin, new ExtendedModelMap()));
        assertEquals("reportsView", controller.reports(admin));
    }

    /** Each pin is clickable only for incidents the account may open. */
    @Test
    public void heatmapLinksOnlyIncidentsTheAccountCanOpen() {
        WebController controller = new WebController(new FakeDatabase());

        // Admin: every pin links to its detail page.
        ExtendedModelMap adminModel = new ExtendedModelMap();
        controller.heatmap(AuthenticationController.authenticateUser("admin", "password"), adminModel);
        assertEquals(Boolean.TRUE, heatmapIncident(adminModel, "100").get("canViewDetails"));
        assertEquals(Boolean.TRUE, heatmapIncident(adminModel, "20").get("canViewDetails"));

        // Regular account: own reports link; samples and other accounts' pins do not.
        try (MockedStatic<AuthenticationController> auth = asUser("user", "user-1", false)) {
            ExtendedModelMap userModel = new ExtendedModelMap();
            controller.heatmap("user", userModel);
            assertEquals(Boolean.TRUE, heatmapIncident(userModel, "100").get("canViewDetails"));
            assertEquals(Boolean.TRUE, heatmapIncident(userModel, "300").get("canViewDetails"));
            assertEquals(Boolean.FALSE, heatmapIncident(userModel, "200").get("canViewDetails"));
            assertEquals(Boolean.FALSE, heatmapIncident(userModel, "20").get("canViewDetails"));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> heatmapIncident(ExtendedModelMap model, String incidentId) {
        return ((List<Map<String, Object>>) model.getAttribute("incidents")).stream()
                .filter(fields -> incidentId.equals(fields.get("incidentId")))
                .findFirst()
                .orElseThrow();
    }

    /** Owners may delete their own reports; other accounts get 404. */
    @Test
    public void usersCanDeleteOnlyTheirOwnIncidents() {
        FakeDatabase db = new FakeDatabase();
        WebController controller = new WebController(db);

        try (MockedStatic<AuthenticationController> auth = asUser("user", "user-1", false)) {
            assertEquals("redirect:/incident", controller.deleteIncident("user", "300"));
            assertEquals(300L, db.deletedId);

            db.deletedId = -1;
            assertEquals("redirect:/incident", controller.deleteIncident("user", "200"));
            assertEquals(-1L, db.deletedId);
        }

        assertEquals("redirect:/login", controller.deleteIncident(null, "300"));
    }

    /** Photos follow the same ownership rule, plus private masking. */
    @Test
    public void usersCanOnlyFetchPhotosOfTheirOwnNonPrivateIncidents() {
        WebController controller = new WebController(new FakeDatabase());

        try (MockedStatic<AuthenticationController> auth = asUser("user", "user-1", false)) {
            assertEquals(HttpStatus.OK, controller.incidentPhoto("user", 300L).getStatusCode());

            ResponseStatusException another = assertThrows(ResponseStatusException.class,
                    () -> controller.incidentPhoto("user", 200L));
            assertEquals(HttpStatus.NOT_FOUND, another.getStatusCode());

            ResponseStatusException ownPrivate = assertThrows(ResponseStatusException.class,
                    () -> controller.incidentPhoto("user", 100L));
            assertEquals(HttpStatus.NOT_FOUND, ownPrivate.getStatusCode());
        }
    }

    /** Incident 100 in the fake database is private and has a photo. */
    @Test
    public void privatePhotoIsOnlyServedToAdmins() {
        String admin = AuthenticationController.authenticateUser("admin", "password");
        WebController controller = new WebController(new FakeDatabase());

        assertEquals(HttpStatus.OK, controller.incidentPhoto(admin, 100L).getStatusCode());

        try (MockedStatic<AuthenticationController> auth = mockStatic(AuthenticationController.class)) {
            auth.when(() -> AuthenticationController.isValidSession("user")).thenReturn(true);

            ResponseStatusException denied = assertThrows(ResponseStatusException.class,
                    () -> controller.incidentPhoto("user", 100L));
            assertEquals(HttpStatus.NOT_FOUND, denied.getStatusCode());
        }
    }

    /**
     * The create account page is reachable while logged out and bounces
     * authenticated users back to the dashboard.
     */
    @Test
    public void registerShowsViewWhenLoggedOut() {
        WebController controller = new WebController(null);

        assertEquals("registerView", controller.register(null));

        String session = AuthenticationController.authenticateUser("admin", "password");
        assertEquals("redirect:/dashboard", controller.register(session));
    }
}
