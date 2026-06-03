/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.dto;

import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ParkingResponseMapper {

    @Mapping(source = "occupancy.total", target = "occupancy")
    @Mapping(source = "messageDate", target = "sourceTimestamp")
    Parking toParking(ParkingResponse response);
}
