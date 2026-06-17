/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.server.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.metro_parking_web_service.parking.client.dto.Parking;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ParkingResponseMapperTest {

    private final ParkingResponseMapper mapper = Mappers.getMapper(ParkingResponseMapper.class);

    @Test
    void toParking_shouldMapFields() {
        LocalDateTime messageDate = LocalDateTime.of(2025, 1, 1, 10, 15);
        ParkingResponse response =
                new ParkingResponse(
                        "123",
                        "Kolombo Creek",
                        "500",
                        new ParkingResponse.Occupancy("200"),
                        messageDate);
        Parking parking = mapper.toParking(response);

        assertEquals(123, parking.facilityId());
        assertEquals("Kolombo Creek", parking.facilityName());
        assertEquals(500, parking.spots());
        assertEquals(200, parking.occupancy());
        assertEquals(messageDate, parking.sourceTimestamp());
    }
}
