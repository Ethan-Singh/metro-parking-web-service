/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.server.scheduling;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
class ParkingSync {
    private final Instant time = Instant.now();

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    public void parkingSync() {

        log.info("ParkingSync start: {}", time.toString());
    }
}
