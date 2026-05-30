package com.example.metro_parking_web_service.parking.client.controller;

import com.example.metro_parking_web_service.parking.client.service.ParkingService;
import com.example.metro_parking_web_service.parking.server.dto.response.Parking;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RestController
@RequestMapping("/parking")
@RequiredArgsConstructor
class ParkingController {

    private final ParkingService parkingService;

    @GetMapping("/list")
    public ResponseEntity<List<Parking>> parkingList(HttpServletRequest request) {
        List<Parking> result = parkingService.parkingList();
        return ResponseEntity.ok()
                .body(result);
    }

}
