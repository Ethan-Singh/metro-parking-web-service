/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record ParkingHistoryResponse(
        String slug, LocalDate date, Granularity granularity, List<DataPoint> dataPoints) {}
