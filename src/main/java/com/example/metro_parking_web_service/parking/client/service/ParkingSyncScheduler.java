/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Profile("!test")
class ParkingSyncScheduler {

    private static final Duration FACILITY_REFRESH_INTERVAL = Duration.ofHours(6);
    private static final Duration FACILITY_RETRY_INTERVAL = Duration.ofMinutes(1);

    private final ParkingFacilityCatalog parkingSnapshot;
    private final ParkingBackfillService parkingBackfillService;
    private volatile Instant facilityRefreshDue = Instant.EPOCH;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    void syncHistory() {
        Instant now = Instant.now();
        if (!now.isBefore(facilityRefreshDue)) {
            boolean refreshed = parkingSnapshot.refresh();
            facilityRefreshDue =
                    now.plus(refreshed ? FACILITY_REFRESH_INTERVAL : FACILITY_RETRY_INTERVAL);
            return;
        }
        parkingBackfillService.syncHistory();
    }

    @Scheduled(cron = "0 0 3 * * *")
    void cleanup() {
        parkingBackfillService.cleanup();
    }
}
