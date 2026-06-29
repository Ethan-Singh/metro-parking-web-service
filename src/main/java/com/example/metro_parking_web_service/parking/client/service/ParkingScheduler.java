/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

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
    private final ParkingBackfillService parkingBackfillService;

    @Scheduled(fixedDelay = 15, timeUnit = TimeUnit.SECONDS)
    void sync() {
        parkingSnapshot.refresh();
        parkingBackfillService.backfill();
    }

    @Scheduled(cron = "0 0 3 * * *")
    void cleanup() {
        parkingBackfillService.cleanup();
    }
}
