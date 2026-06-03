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
class ParkingSync {
    ParkingService parkingService;

    @Scheduled(fixedRate = 5000, timeUnit = TimeUnit.SECONDS)
    private void parkingSync() {
        parkingService.sync();
    }
}
