/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.dto;

public record LiveCarParkResponse(
        String slug,
        String facilityName,
        int spots,
        int occupancy,
        int available,
        double occupancyRate,
        Availability availability,
        long timeMs,
        String ariaLabel) {}
