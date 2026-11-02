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

    /** Records comment saves and reports one database incident, id 100. */
    private static class FakeDatabase extends DatabaseCommunicationContext {
        long savedId;
        String savedComments;

        FakeDatabase() {
            super("", 0, "", "", "");
        }

        @Override
        public List<Map<String, Object>> getAllIncidents(int limit, int offset) {
            Map<String, Object> row = new HashMap<>();
            row.put("incident_id", 100L);
            row.put("timestamp_ms", 0L);
            row.put("latitude", 33.0);
            row.put("longitude", -111.0);
            row.put("is_anonymized", true);
            row.put("additional_comments", "stored text");
            return List.of(row);
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

    @Test
    @SuppressWarnings("unchecked")
    public void nonAdminPagesReceiveNoPrivateData() {
        try (MockedStatic<AuthenticationController> auth = mockStatic(AuthenticationController.class)) {
            auth.when(() -> AuthenticationController.isValidSession("user")).thenReturn(true);
            WebController controller = new WebController(null);

            ExtendedModelMap dashboard = new ExtendedModelMap();
            controller.dashboard("user", dashboard);
            assertPrivateIncidentMasked((List<Incident>) dashboard.getAttribute("incidents"));

            ExtendedModelMap incidentPage = new ExtendedModelMap();
            controller.incident("user", "3", incidentPage);
            assertPrivateIncidentMasked((List<Incident>) incidentPage.getAttribute("incidents"));
            assertNull(((Incident) incidentPage.getAttribute("incident")).licensePlate());
        }
    }

    private static void assertPrivateIncidentMasked(List<Incident> incidents) {
        Incident incident = incidents.stream()
                .filter(i -> i.incidentId().equals("3"))
                .findFirst()
                .orElseThrow();
        assertNull(incident.licensePlate());
        assertNull(incident.driverInformation());
        assertNull(incident.extraComment());
        assertNull(incident.imageId());
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
