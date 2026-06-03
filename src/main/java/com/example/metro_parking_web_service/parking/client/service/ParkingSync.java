/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.client.dto.Parking;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class ParkingSync {

    private final ParkingService parkingService;

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    public void parkingSync() {
        List<Parking> parkingList = parkingService.parkingList();
        System.out.println(parkingList);
    }
}
