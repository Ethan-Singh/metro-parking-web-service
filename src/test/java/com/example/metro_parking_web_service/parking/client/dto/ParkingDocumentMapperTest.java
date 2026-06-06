/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ParkingDocumentMapperTest {

    private final ParkingDocumentMapper mapper = Mappers.getMapper(ParkingDocumentMapper.class);

    @Test
    void toParkingDocument_shouldMapFields() {
        LocalDateTime sourceTimestamp = LocalDateTime.of(2025, 1, 1, 10, 15);
        Parking parking = new Parking(1, "Kolombo Creek", 500, 200, sourceTimestamp);
        ParkingDocument parkingDocument = mapper.toParkingDocument(parking);

        assertEquals(1, parkingDocument.getFacilityId());
        assertEquals("Kolombo Creek", parkingDocument.getFacilityName());
        assertEquals(500, parkingDocument.getSpots());
        assertEquals(200, parkingDocument.getOccupancy());
        assertEquals(sourceTimestamp, parkingDocument.getSourceTimestamp());
        assertNull(parkingDocument.getId());
        assertNull(parkingDocument.getCreatedAt());
    }
}
