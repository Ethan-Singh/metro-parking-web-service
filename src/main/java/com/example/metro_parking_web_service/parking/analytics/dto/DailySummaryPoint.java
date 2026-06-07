/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DailySummaryPoint(
        LocalDateTime timestamp,
        int occupancy,
        int available,
        double occupancyRate,
        int peakOccupancy,
        int minOccupancy,
        double avgOccupancyRate,
        LocalDateTime typicalPeakTime) {}
