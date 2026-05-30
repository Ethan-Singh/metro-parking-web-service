/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.server.dto.response.Parking;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ParkingService {

    public List<Parking> parkingList() {
        return List.of(new Parking(1, "Test", 1, 1, Instant.now()));
    }
}
