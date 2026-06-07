/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.dto;

import java.time.LocalDateTime;

public record PredictionPoint(
        LocalDateTime datetime,
        int predictedOccupancy,
        int predictedAvailable,
        double confidence,
        AvailabilityStatus predictedStatus,
        String statusLabel) {}
