/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
class ParkingScheduler {

    private final ParkingSnapshot parkingSnapshot;
    private final ParkingIngestService parkingIngestService;
    private final ParkingBackfillService parkingBackfillService;

    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    void sync() {
        parkingSnapshot.refresh();
        parkingIngestService.ingest(parkingSnapshot.getResponses());
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    void backfill() {
        List<Integer> facilityIds = parkingSnapshot.getFacilityIds();
        parkingBackfillService.backfillNext(facilityIds);
    }
}
