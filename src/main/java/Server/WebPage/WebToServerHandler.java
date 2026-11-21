package Server.WebPage;

import java.util.Collections;

import IBDCServer.Server.IBDCServerV021.FetchIncidentsRequest;
import IBDCServer.Server.IBDCServerV021.FetchIncidentsResponse;
import IBDCServer.Server.IBDCServerV021.FetchOffenderHistoryRequest;
import IBDCServer.Server.IBDCServerV021.FetchOffenderHistoryResponse;
import IBDCServer.Server.IBDCServerV021.FetchRepeatOffendersRequest;
import IBDCServer.Server.IBDCServerV021.FetchRepeatOffendersResponse;
import IBDCServer.Server.IBDCServerV021.HeatmapDataRequest;
import IBDCServer.Server.IBDCServerV021.HeatmapDataResponse;
import IBDCServer.Server.IBDCServerV021.Incident;
import IBDCServer.Server.IBDCServerV021.Location;
import IBDCServer.Server.IBDCServerV021.RepeatOffenderSummary;
import IBDCServer.Server.IBDCServerV021.ServerErrorResponse;
import IBDCServer.Server.IBDCServerV021.ServerToWeb;
import IBDCServer.Server.IBDCServerV021.UserAuthRequest;
import IBDCServer.Server.IBDCServerV021.UserAuthResponse;
import IBDCServer.Server.IBDCServerV021.WebToServer;

public class WebToServerHandler {

    // Handles the different requests from the web client and returns the appropriate response
    public static ServerToWeb handleRequest(WebToServer request) {
        ServerToWeb.Builder response = ServerToWeb.newBuilder();

        // Determine the type of request and call the appropriate handler method
        switch (request.getPayloadCase()) {
            case FETCH_INCIDENTS_REQUEST:
                FetchIncidentsRequest fetchRequest = request.getFetchIncidentsRequest();
                response = handleFetchIncidents(fetchRequest);
                break;

            case USER_AUTH_REQUEST:
                UserAuthRequest authRequest = request.getUserAuthRequest();
                response = handleUserAuthentication(authRequest);
                break;

            case HEATMAP_DATA_REQUEST:
                HeatmapDataRequest heatmapRequest = request.getHeatmapDataRequest();
                response = handleHeatmapDataRequest(heatmapRequest);
                break;

            case FETCH_REPEAT_OFFENDERS_REQUEST:
                FetchRepeatOffendersRequest repeatOffendersRequest = request.getFetchRepeatOffendersRequest();
                response = handleFetchRepeatOffenders(repeatOffendersRequest);
                break;

            case FETCH_OFFENDER_HISTORY_REQUEST:
                FetchOffenderHistoryRequest historyRequest = request.getFetchOffenderHistoryRequest();
                response = handleFetchOffenderHistory(historyRequest);
                break;

            default:
                response.setErrorResponse(ServerErrorResponse.newBuilder()
                        .setErrorCode(400)
                        .setErrorMessage("Unknown request type")
                        .build())
                        .build();
                break;
        }

        return response.build();
    }

    // Handles FectchIncidentsRequest and returns a FetchIncidentsResponse
    private static ServerToWeb.Builder handleFetchIncidents(FetchIncidentsRequest request) {
        ServerToWeb.Builder response = ServerToWeb.newBuilder();

        String userId = request.getUserId();
        int limit = request.getLimit();
        int offset = request.getOffset();
        boolean anonymized = request.getIncludeAnonymizedOnly();


        //TO_DO: Implement logic to fetch incidents

        Location location = Location.newBuilder()
                .setLatitude(37.7749)
                .setLongitude(-122.4194)
                .setAccuracyMeters(5.0f)
                .build();

        Incident incident = Incident.newBuilder()
                .setDeviceEventId(1)
                .setTimestampMs(System.currentTimeMillis())
                .setLocation(location)
                .setDistanceCm(200)
                .setTimeOffsetMs(500)
                .setIsAnonymized(anonymized)
                .build();





        response.setFetchIncidentsResponse(FetchIncidentsResponse.newBuilder()
                .addIncidents(incident)     // Replace with the actual list of incidents
                .setTotalCount(1)           // Replace 0 with the actual total count of incidents
                .build());                        
        return response;
    }

    // Handles UserAuthRequest and returns a UserAuthResponse
    private static ServerToWeb.Builder handleUserAuthentication(UserAuthRequest request) {
        ServerToWeb.Builder response = ServerToWeb.newBuilder();

        String username = request.getUsernameOrEmail();
        String token = request.getAuthTokenOrHash();


        //TO_DO: Implement logic to authenticate the user

        response.setUserAuthResponse(UserAuthResponse.newBuilder()
                .setAuthenticated(true)
                .setUserId(username)
                .setSessionToken("123")
                .setIsAdmin(true));

        return response;
    }

    // Handles HeatmapDataRequest and returns a HeatmapDataResponse
    private static ServerToWeb.Builder handleHeatmapDataRequest(HeatmapDataRequest request) {
        ServerToWeb.Builder response = ServerToWeb.newBuilder();

        double minLatitude = request.getMinLatitude();
        double maxLatitude = request.getMaxLatitude();
        double minLongitude = request.getMinLongitude();
        double maxLongitude = request.getMaxLongitude();
        long startTime = request.getStartTimestampMs();
        long endTime = request.getEndTimestampMs();

        //TO_DO: Implement logic to fetch heatmap data

        response.setHeatmapDataResponse(HeatmapDataResponse.newBuilder()
                .addAllPoints(Collections.emptyList())
                .build());

        return response;
    }

    // Handles FetchRepeatOffendersRequest and returns a FetchRepeatOffendersResponse
    private static ServerToWeb.Builder handleFetchRepeatOffenders(FetchRepeatOffendersRequest request) {
        ServerToWeb.Builder response = ServerToWeb.newBuilder();

        int minIncidents = request.getMinIncidentCount();
        int limit = request.getLimit();
        int offset = request.getOffset();
        String state = request.getStateOrRegionFilter();

        //TO_DO: Implement logic to fetch repeat offenders

        response.setFetchRepeatOffendersResponse(FetchRepeatOffendersResponse.newBuilder()
                .addAllOffenders(Collections.emptyList())     // Replace with the actual list of repeat offenders
                .setTotalCount(0)           // Replace 0 with the actual total count of repeat offenders
                .build());

        return response;
    }

    // Handles FetchOffenderHistoryRequest and returns a FetchOffenderHistoryResponse
    private static ServerToWeb.Builder handleFetchOffenderHistory(FetchOffenderHistoryRequest request) {
        ServerToWeb.Builder response = ServerToWeb.newBuilder();

        String licensePlate = request.getLicensePlate();
        String state = request.getStateOrRegion();
        int limit = request.getLimit();
        int offset = request.getOffset();

        //TO_DO: Implement logic to fetch offender history

        response.setFetchOffenderHistoryResponse(FetchOffenderHistoryResponse.newBuilder()
                .setOffenderInfo(RepeatOffenderSummary.newBuilder()
                        .setLicensePlate(licensePlate)
                        .setStateOrRegion(state)
                        .setTotalIncidents(0)
                        .setFirstSeenTimestampMs(0)
                        .setLastSeenTimestampMs(0)
                        .setVehicleMake("Unknown")
                        .setVehicleModel("Unknown")
                        .setVehicleColor("Unknown")
                        .build())
                .addAllIncidents(Collections.emptyList()) // Replace with actual list of incidents
                .build());

        return response;
    }
}
