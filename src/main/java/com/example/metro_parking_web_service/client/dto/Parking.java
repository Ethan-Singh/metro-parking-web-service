package com.example.metro_parking_web_service.client.dto;

public record Parking (
        int facilityId,
        String facilityName,
        int spots,
        int occupancy
){
}
