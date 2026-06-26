/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.dto;

import java.time.LocalDateTime;

public record ParkingOverviewResponse(
        String slug,
        String facilityName,
        int spots,
        int occupancy,
        int available,
        double occupancyRate,
        Availability status,
        String statusLabel,
        String approximation,
        LocalDateTime asOf,
        String ariaLabel) {}
