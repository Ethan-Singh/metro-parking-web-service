/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.dto;

import java.time.LocalDateTime;

public record Parking(
        String facilityId,
        String facilityName,
        String spots,
        String occupancy,
        LocalDateTime messageDate) {}
