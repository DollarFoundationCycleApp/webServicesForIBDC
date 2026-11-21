package Server.App;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import IBDCServer.Server.IBDCServerV021.*;
import IBDCServer.Server.IBDCServerV021.AppToServer;
import IBDCServer.Server.IBDCServerV021.Incident;
import IBDCServer.Server.IBDCServerV021.IncidentPhoto;
import IBDCServer.Server.IBDCServerV021.OffenderVehicle;
import IBDCServer.Server.IBDCServerV021.ServerErrorResponse;
import IBDCServer.Server.IBDCServerV021.ServerToApp;
import IBDCServer.Server.IBDCServerV021.SubmitIncidentRequest;
import IBDCServer.Server.IBDCServerV021.SubmitIncidentResponse;
import IBDCServer.Server.IBDCServerV021.UserAuthRequest;
import IBDCServer.Server.IBDCServerV021.UserAuthResponse;
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
    private static ServerToApp.Builder handleUserAuthentication(UserAuthRequest request,
                                                                DatabaseCommunicationContext db) {
        ServerToApp.Builder response = ServerToApp.newBuilder();

        String username = request.getUsernameOrEmail();
        String token = request.getAuthTokenOrHash();

        String sessionToken = AuthenticationController.authenticateUserHash(db, username, token);
        AuthenticationController.Session session = AuthenticationController.getSession(sessionToken);

        response.setUserAuthResponse(UserAuthResponse.newBuilder()
                .setAuthenticated(session != null)
                .setUserId(session != null ? session.userId() : "")
                .setSessionToken(sessionToken != null ? sessionToken : "")
                .setIsAdmin(session != null && session.isAdmin()));

        return response;
    }
}

