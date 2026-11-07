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

import IBDCServer.Server.IBDCServerV021.FetchIncidentsRequest;
import IBDCServer.Server.IBDCServerV021.FetchOffenderHistoryRequest;
import IBDCServer.Server.IBDCServerV021.FetchRepeatOffendersRequest;
import IBDCServer.Server.IBDCServerV021.HeatmapDataRequest;
import IBDCServer.Server.IBDCServerV021.ServerToWeb;
import IBDCServer.Server.IBDCServerV021.WebToServer;
import Server.Controller.AuthenticationController;
import Server.Model.DatabaseCommunicationContext;

// Handles the different request endpoints from the web
@RestController
public class WebRequestHandler {
    private static final String PROTO = "application/x-protobuf";
    private static final Duration SESSION_LIFETIME = Duration.ofHours(8);

    private final DatabaseCommunicationContext db;

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

        AuthenticationController.Session session = AuthenticationController.getSession(sessionToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(sessionToken).toString())
                .body(Map.of(
                    "authenticated", true,
                    "userId", session.userId(),
                    "sessionToken", sessionToken,
                    "isAdmin", session.isAdmin()
        ));
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

        if (result.status() != AuthenticationController.RegisterStatus.SUCCESS) {
            return ResponseEntity.status(statusFor(result.status()))
                    .body(Map.of(
                            "registered", false,
                            "message", result.message()
                    ));
        }

        AuthenticationController.Session session = AuthenticationController.getSession(result.sessionToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(result.sessionToken()).toString())
                .body(Map.of(
                    "registered", true,
                    "message", result.message(),
                    "userId", session.userId(),
                    "sessionToken", result.sessionToken(),
                    "isAdmin", session.isAdmin()
        ));
    }

    private static HttpStatus statusFor(AuthenticationController.RegisterStatus status) {
        return switch (status) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case DUPLICATE -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case SUCCESS -> HttpStatus.OK;
        };
    }

    private static ResponseCookie sessionCookie(String sessionToken) {
        return ResponseCookie.from("SESSION", sessionToken)
            .httpOnly(true)
            .path("/")
            .sameSite("Lax")
            .maxAge(SESSION_LIFETIME)
            .build();
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


    @GetMapping(value = "/api/incidents", produces = PROTO)
    public ServerToWeb incidents(
        @RequestParam(defaultValue = "") String userId,
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
}
