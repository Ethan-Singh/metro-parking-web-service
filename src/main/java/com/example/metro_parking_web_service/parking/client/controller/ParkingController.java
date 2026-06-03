/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.controller;

import com.example.metro_parking_web_service.parking.client.service.ParkingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/parking")
@RequiredArgsConstructor
class ParkingController {

    private final ParkingService parkingService;

    @GetMapping("/list")
    public ResponseEntity<String> parkingList(HttpServletRequest request) {
        return ResponseEntity.ok().body("List");
    }

    @GetMapping("/history")
    public ResponseEntity<String> parkingHistory(HttpServletRequest request) {
        return ResponseEntity.ok().body("List");
    }
}
