/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.server.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record ParkingResponse(
        @JsonProperty("facility_id") String facilityId,
        @JsonProperty("facility_name") String facilityName,
        String spots,
        Occupancy occupancy,
        @JsonProperty("MessageDate") LocalDateTime messageDate) {}
