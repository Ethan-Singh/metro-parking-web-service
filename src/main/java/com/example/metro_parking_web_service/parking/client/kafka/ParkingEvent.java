/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.kafka;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record ParkingEvent(
        String facilityId,
        String facilityName,
        String spots,
        String occupancy,
        LocalDateTime messageDate,
        UUID eventId,
        Instant timestamp) {}
