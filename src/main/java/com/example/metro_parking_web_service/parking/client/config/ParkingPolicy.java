/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.config;

import com.example.metro_parking_web_service.parking.client.document.ParkingBackfillDocument;
import com.example.metro_parking_web_service.parking.client.dto.Parking;
import java.time.LocalDate;
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

    public boolean isParkingAllowed(Parking parking) {
        return !disabledFacilities.contains(parking.facilityId());
    }

    private LocalDate backfillStartDate() {
        return LocalDate.now().minusWeeks(backfillWindow);
    }

    public boolean isOutsideBackfillWindow(ParkingBackfillDocument parkingBackfillDocument) {
        LocalDate last = parkingBackfillDocument.getLastProcessedDate();
        return last != null && last.isBefore(backfillStartDate());
    }
}
