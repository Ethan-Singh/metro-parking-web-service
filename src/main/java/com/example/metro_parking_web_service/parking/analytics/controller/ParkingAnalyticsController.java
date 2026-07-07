/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.controller;

import com.example.metro_parking_web_service.parking.analytics.dto.Granularity;
import com.example.metro_parking_web_service.parking.analytics.dto.ParkingHistoryResponse;
import com.example.metro_parking_web_service.parking.analytics.dto.ParkingOverviewResponse;
import com.example.metro_parking_web_service.parking.analytics.service.ParkingAnalyticsService;
import java.time.Duration;
import java.time.LocalDate;
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
public class ParkingAnalyticsController {

    private final ParkingAnalyticsService analyticsService;

    /**
     * Dashboard — all facilities current snapshot.
     * GET /api/v1/parking
     */
    @GetMapping
    public ResponseEntity<List<ParkingOverviewResponse>> getAllOverviews() {
        return ResponseEntity.ok(analyticsService.getAllOverviews());
    }

    /**
     * Single facility current snapshot.
     * GET /api/v1/parking/{slug}/overview
     */
    @GetMapping("/{slug}/overview")
    public ResponseEntity<ParkingOverviewResponse> getOverview(@PathVariable String slug) {
        return ResponseEntity.ok(analyticsService.getOverview(slug));
    }

    /**
     * Historical graph data.
     * GET /api/v1/parking/{slug}/history?from=2025-06-07&to=2025-06-07&granularity=HOURLY
     */
    @GetMapping("/{slug}/history")
    public ResponseEntity<ParkingHistoryResponse> getHistory(
            @PathVariable String slug,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "HOURLY") Granularity granularity) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(analyticsService.getHistory(slug, from, to, granularity));
    }
}
