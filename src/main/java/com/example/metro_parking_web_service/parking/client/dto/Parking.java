/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.dto;

import java.time.Instant;

public record Parking(
        int facilityId, String facilityName, int spots, int occupancy, Instant lastUpdated) {}
