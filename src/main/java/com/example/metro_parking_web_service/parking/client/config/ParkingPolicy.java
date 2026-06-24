/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.config;

import com.example.metro_parking_web_service.parking.client.document.ParkingBackfillDocument;
import com.example.metro_parking_web_service.parking.client.dto.Parking;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private Set<Integer> disabledFacilities;
    private int backfillWindow;

    public LocalDate minAllowedDate() {
        return LocalDate.now().minusWeeks(backfillWindow);
    }

    public boolean isParkingAllowed(Parking parking) {
        return !disabledFacilities.contains(parking.facilityId());
    }

    public boolean isBeforeMinAllowedDate(ParkingBackfillDocument parkingBackfillDocument) {
        LocalDate last = parkingBackfillDocument.getLastProcessedDate();
        return last != null && last.isBefore(minAllowedDate());
    }

    public LocalDate clampFromDate(LocalDate from) {
        LocalDate min = minAllowedDate();
        return from.isBefore(min) ? min : from;
    }

    public LocalDate clampToDate(LocalDate to) {
        LocalDate now = LocalDate.now();
        return to.isAfter(now) ? now : to;
    }

    public LocalDateTime clampEndInclusiveDate(LocalDate to) {
        return clampToDate(to).plusDays(1).atStartOfDay().minusNanos(1);
    }
}
