/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.controller;

import com.example.metro_parking_web_service.parking.analytics.dto.DailyParkingMetricsPayload;
import com.example.metro_parking_web_service.parking.analytics.dto.HistoryResolution;
import com.example.metro_parking_web_service.parking.analytics.dto.LiveCarParkResponse;
import com.example.metro_parking_web_service.parking.analytics.dto.ParkingHistoryPayload;
import com.example.metro_parking_web_service.parking.analytics.service.ParkingQueryService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parking")
@RequiredArgsConstructor
public class ParkingController {

    private final ParkingQueryService parkingService;

    @GetMapping
    public ResponseEntity<List<LiveCarParkResponse>> getAllLiveCarParks() {
        return ResponseEntity.ok(parkingService.getAllLiveCarParks());
    }

    @GetMapping("/{slug}/live")
    public ResponseEntity<LiveCarParkResponse> getLiveCarPark(@PathVariable String slug) {
        return ResponseEntity.ok(parkingService.getLiveCarPark(slug));
    }

    @GetMapping("/{slug}/metrics/daily")
    public ResponseEntity<DailyParkingMetricsPayload> getDailyMetrics(
            @PathVariable String slug,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        DailyParkingMetricsPayload metrics = parkingService.getDailyMetrics(slug, from, to);
        CacheControl cacheControl =
                LocalDate.parse(metrics.to()).isBefore(LocalDate.now(ZoneId.of("Australia/Sydney")))
                        ? CacheControl.maxAge(Duration.ofHours(1))
                        : CacheControl.noStore();
        return ResponseEntity.ok().cacheControl(cacheControl).body(metrics);
    }

    @GetMapping("/{slug}/history")
    public ResponseEntity<ParkingHistoryPayload> getHistory(
            @PathVariable String slug,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "AUTO") HistoryResolution resolution) {
        CacheControl cacheControl =
                to.isBefore(LocalDate.now(ZoneId.of("Australia/Sydney")))
                        ? CacheControl.maxAge(Duration.ofHours(1))
                        : CacheControl.noStore();
        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .body(parkingService.getHistoryPayload(slug, from, to, resolution));
    }
}
