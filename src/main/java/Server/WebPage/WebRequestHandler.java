package Server.WebPage;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import CycleSafety.Server.SCPBV020.FetchIncidentsRequest;
import CycleSafety.Server.SCPBV020.FetchOffenderHistoryRequest;
import CycleSafety.Server.SCPBV020.FetchRepeatOffendersRequest;
import CycleSafety.Server.SCPBV020.HeatmapDataRequest;
import CycleSafety.Server.SCPBV020.ServerToWeb;
import CycleSafety.Server.SCPBV020.WebToServer;
import Server.Controller.AuthenticationController;
import Server.Model.DatabaseCommunicationContext;

// Handles the different request endpoints from the web
@RestController
public class WebRequestHandler {
    private static final String PROTO = "application/x-protobuf";

    private final DatabaseCommunicationContext db;

    public WebRequestHandler() {
        this.db = null;
    }

    public WebRequestHandler(DatabaseCommunicationContext db) {
        this.db = db;
    }

    @PostMapping(value = "/api/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> loginJson(@RequestBody LoginRequest request) {
        String sessionToken = AuthenticationController.authenticateUser(
                db,
                request.usernameOrEmail(),
                request.password()
        );

        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "authenticated", false,
                            "message", "Invalid credentials"
                    ));
        }

        ResponseCookie cookie = ResponseCookie.from("SESSION", sessionToken)
            .httpOnly(true)
            .path("/")
            .sameSite("Lax")
            .maxAge(Duration.ofHours(8))
            .build();

        AuthenticationController.Session session = AuthenticationController.getSession(sessionToken);
        String userId = session == null ? request.usernameOrEmail() : session.userId();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of(
                    "authenticated", true,
                    "userId", userId,
                    "sessionToken", sessionToken,
                    "isAdmin", session != null && session.isAdmin()
        ));
    }

    @PostMapping(value = "/api/logout", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> logoutJson(
            @CookieValue(value = "SESSION", required = false) String sessionToken) {

                AuthenticationController.logout(sessionToken);
                ResponseCookie expCookie = ResponseCookie.from("SESSION", "")
                    .path("/")
                    .maxAge(0)
                    .build();

                return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, expCookie.toString())
                    .body(Map.of("loggedOut", true));
    }

    @PostMapping(value = "/api/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> registerJson(@RequestBody RegisterRequest request) {
        AuthenticationController.RegisterResult result = AuthenticationController.register(
                db,
                request.username(),
                request.email(),
                request.password(),
                request.confirmPassword()
        );

        boolean registered = result.status() == AuthenticationController.RegisterStatus.SUCCESS;
        int status = switch (result.status()) {
            case INVALID -> 400;
            case UNAVAILABLE -> 503;
            case SUCCESS -> 200;
            default -> 400;
        };

        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("registered", registered);
        body.put("message", result.message());
        if (result.sessionToken() != null) {
            body.put("sessionToken", result.sessionToken());
        }
        return ResponseEntity.status(status).body(body);
    }

    // Username update endpoints (multiple aliases)
    @PostMapping(value = "/api/settings/username", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateUsernameJson(
            @CookieValue(value = "SESSION", required = false) String session,
            @RequestBody UpdateUsernameRequest request) {
        return doUpdateUsername(session, request.newUsername());
    }

    @PostMapping(value = "/api/update-username", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateUsernameJson2(
            @CookieValue(value = "SESSION", required = false) String session,
            @RequestBody UpdateUsernameRequest request) {
        return doUpdateUsername(session, request.newUsername());
    }

    @PostMapping(value = "/api/user/username", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateUsernameJson3(
            @CookieValue(value = "SESSION", required = false) String session,
            @RequestBody UpdateUsernameRequest request) {
        return doUpdateUsername(session, request.newUsername());
    }

    @PostMapping(value = "/api/settings/update-username", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> updateUsernameJson4(
            @CookieValue(value = "SESSION", required = false) String session,
            @RequestBody UpdateUsernameRequest request) {
        return doUpdateUsername(session, request.newUsername());
    }

    private ResponseEntity<Map<String, Object>> doUpdateUsername(String session, String newUsername) {
        boolean ok = AuthenticationController.updateUsername(db, session, newUsername);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("updated", false, "message", "Invalid username or session."));
        }
        return ResponseEntity.ok(Map.of("updated", true, "username", newUsername == null ? "" : newUsername.trim()));
    }

    @GetMapping(value = "/api/incidents", produces = PROTO)
    public ServerToWeb incidents(
        @RequestParam(defaultValue = "0") long userId,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "false") boolean isAnonymized) {

            return WebToServerHandler.handleRequest(WebToServer.newBuilder()
                .setFetchIncidentsRequest(FetchIncidentsRequest.newBuilder()
                    .setUserId(userId)
                    .setLimit(limit)
                    .setOffset(offset)
                    .setIncludeAnonymizedOnly(isAnonymized)
                    .build())
                .build());
    }

    @GetMapping(value = "/api/heatmap", produces = PROTO)
    public ServerToWeb heatmap(
        @RequestParam(defaultValue = "-90") double minLat,
        @RequestParam(defaultValue = "90") double maxLat,
        @RequestParam(defaultValue = "-180") double minLong,
        @RequestParam(defaultValue = "180") double maxLong,
        @RequestParam(defaultValue = "50") long start,
        @RequestParam(required = false) Long end) {


            return WebToServerHandler.handleRequest(WebToServer.newBuilder()
                .setHeatmapDataRequest(HeatmapDataRequest.newBuilder()
                    .setMinLatitude(minLat)
                    .setMaxLatitude(maxLat)
                    .setMinLongitude(minLong)
                    .setMaxLongitude(maxLong)
                    .setStartTimestampMs(start)
                    .setEndTimestampMs(end != null ? end : System.currentTimeMillis())
                    .build())
                .build());
    }

    @GetMapping(value = "/api/repeat-offenders", produces = PROTO)
    public ServerToWeb repeatOffenders(
        @RequestParam(defaultValue = "2") int minIncidents,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "") String state) {

            return WebToServerHandler.handleRequest(WebToServer.newBuilder()
                .setFetchRepeatOffendersRequest(FetchRepeatOffendersRequest.newBuilder()
                    .setMinIncidentCount(minIncidents)
                    .setLimit(limit)
                    .setOffset(offset)
                    .setStateOrRegionFilter(state))
                .build());
    }

    @GetMapping(value = "/api/offender-history", produces = PROTO)
    public ServerToWeb offenderHistory(
        @RequestParam(defaultValue = "") String plate,
        @RequestParam(defaultValue = "") String state,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "0") int offset) {

            return WebToServerHandler.handleRequest(WebToServer.newBuilder()
                .setFetchOffenderHistoryRequest(FetchOffenderHistoryRequest.newBuilder()
                    .setLicensePlate(plate)
                    .setStateOrRegion(state)
                    .setLimit(limit)
                    .setOffset(offset))
                .build());
    }

    public record LoginRequest(String usernameOrEmail, String password) {
    }

    public record RegisterRequest(String username, String email, String password, String confirmPassword) {
    }

    public record UpdateUsernameRequest(String newUsername) {
    }

    public record UsernameUpdateRequest(String username) {
    }

    // Convenience methods for direct (non-HTTP) use in tests
    public ResponseEntity<Map<String, Object>> updateUsername(String session, String newUsername) {
        return doUpdateUsername(session, newUsername);
    }

    public ResponseEntity<Map<String, Object>> updateAccountUsername(String session, String newUsername) {
        return doUpdateUsername(session, newUsername);
    }

    public ResponseEntity<Map<String, Object>> changeUsername(String session, String newUsername) {
        return doUpdateUsername(session, newUsername);
    }

    public ResponseEntity<Map<String, Object>> updateUsername(String session, String newUsername, String unused) {
        return doUpdateUsername(session, newUsername);
    }
}
