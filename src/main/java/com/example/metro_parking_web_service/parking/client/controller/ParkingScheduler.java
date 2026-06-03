/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.controller;

import com.example.metro_parking_web_service.parking.client.service.ParkingService;
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
    private void parkingSyncAll() {
        log.info("Parking Sync started");
        parkingService.parkingSyncAll();
        log.info("Parking Sync Complete");
    }

    @Scheduled(cron = "0 0 2 * * *")
    private void parkingBackfillAll() {
        log.info("Parking Backfill started");
        parkingService.parkingBackfillAll();
        log.info("Parking Backfill Complete");
    }
}
