/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.dto;

import java.time.LocalDateTime;

public record DailySummaryAggregate(
        LocalDateTime timestamp,
        int spots,
        double avgOccupancy,
        int peakOccupancy,
        int minOccupancy) {}
