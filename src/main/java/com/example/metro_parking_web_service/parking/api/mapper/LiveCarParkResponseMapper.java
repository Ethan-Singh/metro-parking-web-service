/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.api.mapper;

import com.example.metro_parking_web_service.parking.api.dto.Availability;
import com.example.metro_parking_web_service.parking.api.dto.LiveCarParkResponse;
import com.example.metro_parking_web_service.parking.api.service.ParkingSlugService;
import com.example.metro_parking_web_service.parking.api.service.ParkingStatusService;
import com.example.metro_parking_web_service.parking.ingestion.document.ParkingDay;
import com.example.metro_parking_web_service.parking.ingestion.document.ParkingDay.ParkingSample;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LiveCarParkResponseMapper {

    private final ParkingStatusService statusService;
    private final ParkingSlugService slugService;
    private final ApiTimestampMapper timestampMapper;

    public LiveCarParkResponse toResponse(ParkingDay day, ParkingSample sample) {
        int available = Math.max(0, sample.capacity() - sample.occupancy());
        double occupancyRate =
                sample.capacity() > 0 ? (double) sample.occupancy() / sample.capacity() : 0;
        Availability availability = statusService.resolveStatus(available, sample.capacity());
        String ariaLabel =
                String.format(
                        "%s, %d of %d spots available",
                        day.getFacilityName(), available, sample.capacity());

        return new LiveCarParkResponse(
                slugService.toSlug(day.getFacilityName()),
                day.getFacilityName(),
                sample.capacity(),
                sample.occupancy(),
                available,
                occupancyRate,
                availability,
                timestampMapper.toEpochMillis(sample.sourceTime()),
                ariaLabel);
    }
}
