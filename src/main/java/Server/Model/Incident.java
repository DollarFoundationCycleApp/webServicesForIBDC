package Server.Model;

import java.time.LocalDateTime;

public record Incident(
        String incidentId,
        double latitude,
        double longitude,
        String location,
        String licensePlate,
        String imageId,
        String injurySeverity,
        boolean driverPresent,
        String driverInformation,
        String extraComment,
        String vehicleMake,
        String vehicleModel,
        String vehicleColor,
        int vehicleYear,
        LocalDateTime createdTime,
        boolean hideInfo,
        String additionalComments) {

        public Incident(String incidentId, double latitude, double longitude, String location, 
                        String licensePlate, String imageId, String injurySeverity, 
                        boolean driverPresent, String driverInformation, String extraComment, 
                        String vehicleMake, String vehicleModel, String vehicleColor, 
                        int vehicleYear, LocalDateTime createdTime, boolean hideInfo) {
                this(incidentId, latitude, longitude, location, licensePlate, imageId, injurySeverity, driverPresent,
                        driverInformation, extraComment, vehicleMake, vehicleModel, vehicleColor, vehicleYear,
                        createdTime, hideInfo, "");
        }

        // additionalComments stays visible: it is shared between all users
        public static Incident hideInfo(Incident incident) {
                boolean hidden = incident.hideInfo();
                return new Incident(
                        incident.incidentId(), incident.latitude(), incident.longitude(), incident.location(),
                        hidden ? null : incident.licensePlate(), hidden ? null : incident.imageId(),
                        incident.injurySeverity(), incident.driverPresent(),
                        hidden ? null : incident.driverInformation(), hidden ? null : incident.extraComment(),
                        incident.vehicleMake(), incident.vehicleModel(), incident.vehicleColor(),
                        incident.vehicleYear(), incident.createdTime(), incident.hideInfo(),
                        incident.additionalComments());
        }

}
