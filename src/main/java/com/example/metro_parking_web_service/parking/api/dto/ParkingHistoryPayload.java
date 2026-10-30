/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.api.dto;

import java.util.List;

public record ParkingHistoryPayload(
        String from, String to, HistoryResolution resolution, List<List<Number>> points) {}
