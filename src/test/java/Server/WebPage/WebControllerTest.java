package Server.WebPage;

import Server.Controller.AuthenticationController;
import Server.Model.DatabaseCommunicationContext;
import Server.Model.Incident;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    public void privateIncidentsStillShowAdditionalComments() {
        Incident incident = new Incident("1", 0, 0, "PLATE", "img", "None", false, "driver", "comment",
                "", "", "", 0, LocalDateTime.now(), true, "shared");

        Incident hidden = Incident.hideInfo(incident);

        assertNull(hidden.licensePlate());
        assertEquals("shared", hidden.additionalComments());
    }
}
