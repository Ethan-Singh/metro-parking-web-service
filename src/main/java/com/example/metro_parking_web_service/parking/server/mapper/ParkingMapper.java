/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.server.mapper;

import com.example.metro_parking_web_service.parking.client.dto.Parking;
import com.example.metro_parking_web_service.parking.server.dto.response.ParkingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ParkingMapper {
    @Mapping(target = "facilityId", expression = "java(Integer.parseInt(response.facilityId()))")
    @Mapping(target = "spots", expression = "java(Integer.parseInt(response.spots()))")
    @Mapping(
            target = "occupancy",
            expression = "java(Integer.parseInt(response.occupancy().total()))")
    Parking toParking(ParkingResponse response);
}
