/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.config;

import com.example.metro_parking_web_service.parking.client.document.ParkingBackfillDocument;
import com.example.metro_parking_web_service.parking.client.dto.Parking;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "external-server.parking.policy")
@Data
public class ParkingPolicy {

    public static final ZoneId SYDNEY_ZONE = ZoneId.of("Australia/Sydney");

    private Set<Integer> disabledFacilities;
    private int backfillWindow;

    public LocalDate getEarliestBackfillDate() {
        return LocalDate.now(SYDNEY_ZONE).minusDays(backfillWindow);
    }

    public boolean isBeforeBackfillWindow(ParkingBackfillDocument parkingBackfillDocument) {
        LocalDate last = parkingBackfillDocument.getLastProcessedDate();
        return last != null && last.isBefore(getEarliestBackfillDate());
    }

    public LocalDate clampFromDate(LocalDate from) {
        LocalDate min = getEarliestBackfillDate();
        return from.isBefore(min) ? min : from;
    }

    public LocalDate clampToDate(LocalDate to) {
        LocalDate now = LocalDate.now(SYDNEY_ZONE);
        return to.isAfter(now) ? now : to;
    }

    public boolean isParkingFacilityAllowed(Parking parking) {
        return !disabledFacilities.contains(parking.facilityId());
    }
}
