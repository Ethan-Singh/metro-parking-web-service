/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class ParkingScheduler {

    private final ParkingService parkingService;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    private void sync() {
        log.info("Sync started");
        parkingService.parkingSyncAll();
        log.info("Sync complete");
    }

    @Scheduled(cron = "0 0 2 * * *")
    private void backfill() {
        log.info("Backfill started");
        parkingService.parkingBackfillAll();
        log.info("Backfill complete");
    }
}
