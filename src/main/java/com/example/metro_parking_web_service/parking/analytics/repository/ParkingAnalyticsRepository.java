/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.repository;

import com.example.metro_parking_web_service.parking.client.document.ParkingDay;
import com.example.metro_parking_web_service.parking.client.repository.ParkingDayRepository;
import com.example.metro_parking_web_service.parking.client.service.ParkingFacilityCatalog;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ParkingAnalyticsRepository {

    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ParkingDayRepository parkingDayRepository;
    private final ParkingFacilityCatalog parkingSnapshot;

    public ParkingDay findLatestByFacilityId(int facilityId) {
        return parkingDayRepository.findByFacilityIdOrderBySydneyDateDesc(facilityId).stream()
                .filter(day -> day.getSamples() != null && !day.getSamples().isEmpty())
                .max(Comparator.comparing(this::latestSourceTime))
                .orElse(null);
    }

    public List<ParkingDay> findAllLatest() {
        return parkingSnapshot.getFacilityIds().stream()
                .map(this::findLatestByFacilityId)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ParkingDay::getFacilityName))
                .toList();
    }

    public List<ParkingDay> findByFacilityAndDateRange(
            int facilityId, LocalDate from, LocalDate to) {
        return parkingDayRepository.findByFacilityIdAndSydneyDateBetweenOrderBySydneyDateAsc(
                facilityId, DATE_KEY.format(from), DATE_KEY.format(to));
    }

    private java.time.Instant latestSourceTime(ParkingDay day) {
        return day.getSamples().stream()
                .map(ParkingDay.ParkingSample::sourceTime)
                .max(Comparator.naturalOrder())
                .orElse(java.time.Instant.MIN);
    }
}
