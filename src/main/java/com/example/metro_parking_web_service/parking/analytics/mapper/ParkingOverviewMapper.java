/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.mapper;

import com.example.metro_parking_web_service.parking.analytics.dto.Availability;
import com.example.metro_parking_web_service.parking.analytics.dto.ParkingOverviewResponse;
import com.example.metro_parking_web_service.parking.analytics.service.ParkingSlugService;
import com.example.metro_parking_web_service.parking.analytics.service.ParkingStatusService;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ParkingOverviewMapper {

    private final ParkingStatusService statusService;
    private final ParkingSlugService slugService;

    public ParkingOverviewResponse toOverview(ParkingDocument document) {
        int available = Math.max(0, document.getSpots() - document.getOccupancy());
        double occupancyRate =
                document.getSpots() > 0
                        ? (double) document.getOccupancy() / document.getSpots()
                        : 0.0;

        Availability availability = statusService.resolveStatus(available, document.getSpots());
        String slug = slugService.toSlug(document.getFacilityName());
        String ariaLabel =
                String.format(
                        "%s, %d of %d spots available",
                        document.getFacilityName(), available, document.getSpots());

        return new ParkingOverviewResponse(
                slug,
                document.getFacilityName(),
                document.getSpots(),
                document.getOccupancy(),
                available,
                occupancyRate,
                availability,
                document.getSourceTimestamp(),
                ariaLabel);
    }
}
