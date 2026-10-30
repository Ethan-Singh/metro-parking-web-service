/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.api.repository;

import com.example.metro_parking_web_service.parking.ingestion.document.ParkingDay;
import com.example.metro_parking_web_service.parking.ingestion.repository.ParkingDayRepository;
import com.example.metro_parking_web_service.parking.ingestion.service.ParkingFacilityCatalog;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ParkingAnalyticsRepository {

    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ParkingDayRepository parkingDayRepository;
    private final ParkingFacilityCatalog parkingSnapshot;

    public ParkingDay findLatestByFacilityId(int facilityId) {
        return parkingDayRepository
                .findLatestPopulatedDaysByFacilityIds(List.of(facilityId))
                .stream()
                .findFirst()
                .orElse(null);
    }

    public List<ParkingDay> findAllLatest() {
        List<Integer> facilityIds = parkingSnapshot.getFacilityIds();
        if (facilityIds.isEmpty()) return List.of();
        return parkingDayRepository.findLatestPopulatedDaysByFacilityIds(facilityIds).stream()
                .sorted(Comparator.comparing(ParkingDay::getFacilityName))
                .toList();
    }

    public List<ParkingDay> findByFacilityAndDateRange(
            int facilityId, LocalDate from, LocalDate to) {
        return parkingDayRepository.findByFacilityIdAndSydneyDateBetweenOrderBySydneyDateAsc(
                facilityId, DATE_KEY.format(from), DATE_KEY.format(to));
    }
}
