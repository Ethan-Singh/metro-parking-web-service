package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.server.dto.response.Parking;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ParkingService {

    public List<Parking> parkingList() {
        return List.of(new Parking(1, "Test", 1, 1, Instant.now()));
    }
}
