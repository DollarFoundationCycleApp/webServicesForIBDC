package Server.App;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import CycleSafety.Server.SCPBV020.*;
import CycleSafety.Server.SCPBV020.AppToServer;
import CycleSafety.Server.SCPBV020.Incident;
import CycleSafety.Server.SCPBV020.IncidentPhoto;
import CycleSafety.Server.SCPBV020.OffenderVehicle;
import CycleSafety.Server.SCPBV020.ServerErrorResponse;
import CycleSafety.Server.SCPBV020.ServerToApp;
import CycleSafety.Server.SCPBV020.SubmitIncidentRequest;
import CycleSafety.Server.SCPBV020.SubmitIncidentResponse;
import CycleSafety.Server.SCPBV020.UserAuthRequest;
import CycleSafety.Server.SCPBV020.UserAuthResponse;
import Server.Controller.AuthenticationController;
import Server.Model.DatabaseCommunicationContext;

public class AppToServerHandler {

    // Handles the different requests from the app client and returns the appropriate response
    public static void handleAppToServer(InputStream in, OutputStream out, int clientID,
                                         DatabaseCommunicationContext db) {
        AppToServer request;
        try {        
            while ((request = AppToServer.parseDelimitedFrom(in)) != null) {
                ServerToApp.Builder response = ServerToApp.newBuilder();

                switch (request.getPayloadCase()) {
                    case SUBMIT_INCIDENT_REQUEST:
                        SubmitIncidentRequest submitRequest = request.getSubmitIncidentRequest();
                        response = handleIncidentSubmission(submitRequest, db);
                        break;
                    case USER_AUTH_REQUEST:
                        UserAuthRequest authRequest = request.getUserAuthRequest();
                        response = handleUserAuthentication(authRequest, db);
                        break;
                    default:
                        response.setErrorResponse(ServerErrorResponse.newBuilder()
                                .setErrorCode(400)
                                .setErrorMessage("Unknown request type")
                                .build())
                                .build();
                        break;
                }
                    response.build().writeDelimitedTo(out);
                    out.flush();
            }
        } catch (Exception e) {
            System.err.println("Error processing request from client " + clientID + ": " + e.getMessage());
        }
    }

    // Handles SubmitIncidentRequest and returns a SubmitIncidentResponse
    private static ServerToApp.Builder handleIncidentSubmission(SubmitIncidentRequest request,
                                                                DatabaseCommunicationContext db) {
        ServerToApp.Builder response = ServerToApp.newBuilder();

        String userId = request.getUserId();
        Incident incident = request.getIncident();
        List<IncidentPhoto> photos = request.getPhotosList();
        OffenderVehicle offenderVehicle = request.hasVehicleInfo() ? request.getVehicleInfo() : null;

        long serverIncidentId = 0;
        if (db != null) {
            serverIncidentId = db.storeIncident(userId, incident, photos, offenderVehicle);
        }

        boolean success = serverIncidentId > 0;
        response.setSubmitIncidentResponse(SubmitIncidentResponse.newBuilder()
                .setSuccess(success)
                .setServerIncidentId(serverIncidentId)
                .setMessage(success
                        ? "Incident submitted successfully"
                        : "Failed to store incident")
                .setTimestampMs(System.currentTimeMillis())
                .build());

        return response;
    }

    // Handles UserAuthRequest and returns a UserAuthResponse
    private static ServerToApp.Builder handleUserAuthentication(UserAuthRequest request, DatabaseCommunicationContext db) {
        ServerToApp.Builder response = ServerToApp.newBuilder();

        String username = request.getUsernameOrEmail();
        String token = request.getAuthTokenOrHash();

        String sessionToken = AuthenticationController.authenticateUserHash(db, username, token);

        String userId = username;
        boolean isAdmin = false;
        if (sessionToken != null) {
            AuthenticationController.Session session = AuthenticationController.getSession(sessionToken);
            if (session != null) {
                userId = session.userId();
                isAdmin = session.isAdmin();
            }
        }

        response.setUserAuthResponse(UserAuthResponse.newBuilder()
                .setAuthenticated(sessionToken != null)
                .setUserId(userId)
                .setSessionToken(sessionToken != null ? sessionToken : "")
                .setIsAdmin(isAdmin));

        return response;
    }
}

