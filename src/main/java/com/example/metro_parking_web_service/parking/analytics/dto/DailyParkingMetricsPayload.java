/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.dto;

import java.util.List;

public record DailyParkingMetricsPayload(
        String from, String to, String timeZone, List<DailyMetric> days) {

    public record DailyMetric(
            String date,
            Integer expectedFreeAt7am,
            Integer expectedFreeAt8am,
            Long crossed50AtMs,
            Long crossed90AtMs,
            Long fullAtMs) {}
}
