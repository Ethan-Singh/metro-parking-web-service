/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.dto;

import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ParkingDocumentMapper {

    ParkingDocument toParkingDocument(Parking parking);
}
