package com.example.metro_parking_web_service.parking.analytics.mapper;

import com.example.metro_parking_web_service.parking.analytics.dto.AvailabilityStatus;
import com.example.metro_parking_web_service.parking.analytics.dto.ParkingOverviewResponse;
import com.example.metro_parking_web_service.parking.analytics.service.ParkingStatusService;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import com.example.metro_parking_web_service.parking.client.dto.Parking;
import com.example.metro_parking_web_service.parking.client.service.ParkingSlugService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.example.metro_parking_web_service.parking.analytics.service.ParkingAnalyticsLabels.APPROXIMATION;

@Component
@RequiredArgsConstructor
public class ParkingOverviewMapper {

    private final ParkingStatusService statusService;
    private final ParkingSlugService slugService;

    public ParkingOverviewResponse toOverview(ParkingDocument document) {
        int available = Math.max(0, document.getSpots() - document.getOccupancy());
        double rate = document.getSpots() > 0 ? (double) document.getOccupancy() / document.getSpots() : 0.0;

        AvailabilityStatus status = statusService.resolveStatus(available, document.getSpots());
        String statusLabel = statusService.resolveStatusLabel(status);
        String slug = slugService.toSlug(document.getFacilityName());
        String ariaLabel =
                String.format(
                        "%s, %d of %d spots available, status: %s",
                        document.getFacilityName(), available, document.getSpots(), statusLabel);

        return new ParkingOverviewResponse(
                slug,
                document.getFacilityName(),
                document.getSpots(),
                document.getOccupancy(),
                available,
                rate,
                status,
                statusLabel,
                APPROXIMATION,
                document.getSourceTimestamp(),
                ariaLabel);
    }
}
