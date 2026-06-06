/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.client.document.ParkingBackfillDocument;
import com.example.metro_parking_web_service.parking.client.dto.Parking;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ParkingPolicy {

    @Value("#{'${external-server.parking.disabled-facilities}'.split(',')}")
    private Set<Integer> DISABLED_FACILITIES;

    @Value("${external-server.parking.backfill-window}")
    LocalDate BACKFILL_WINDOW;

    public boolean isParkingAllowed(Parking parking) {
        return !DISABLED_FACILITIES.contains(parking.facilityId());
    }

    public boolean isBeforeBackfillWindow(ParkingBackfillDocument parkingBackfillDocument) {
        LocalDate last = parkingBackfillDocument.getLastProcessedDate();
        return last != null && !last.isAfter(BACKFILL_WINDOW);
    }
}
