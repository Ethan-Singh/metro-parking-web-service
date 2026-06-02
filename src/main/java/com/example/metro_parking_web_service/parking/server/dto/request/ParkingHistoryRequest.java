/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.server.dto.request;

import java.time.LocalDate;

public record ParkingHistoryRequest(int facilityId, LocalDate eventDate) {}
