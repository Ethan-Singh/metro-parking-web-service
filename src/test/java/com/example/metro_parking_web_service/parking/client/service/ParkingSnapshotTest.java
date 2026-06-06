/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.parking.client.dto.Parking;
import com.example.metro_parking_web_service.parking.client.dto.ParkingResponseMapper;
import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingSnapshotTest {

    @Mock private ParkingClient parkingClient;
    @Mock private ParkingResponseMapper parkingResponseMapper;

    @InjectMocks private ParkingSnapshot parkingSnapshot;

    @Test
    void refresh_shouldPopulateFacilityIds() {
        ParkingResponse r1 = mock(ParkingResponse.class);
        ParkingResponse r2 = mock(ParkingResponse.class);
        ParkingResponse r3 = mock(ParkingResponse.class);

        when(r1.facilityId()).thenReturn("1");
        when(r2.facilityId()).thenReturn("2");
        when(r3.facilityId()).thenReturn("1");
        when(parkingClient.fetchFullList()).thenReturn(List.of(r1, r2, r3));
        when(parkingResponseMapper.toParking(any())).thenReturn(mock(Parking.class));

        parkingSnapshot.refresh();
        List<Integer> result = parkingSnapshot.getFacilityIds();

        assertEquals(2, result.size());
        assertTrue(result.contains(1));
        assertTrue(result.contains(2));
    }

    @Test
    void refresh_shouldHandleEmptyResponse() {
        when(parkingClient.fetchFullList()).thenReturn(Collections.emptyList());

        parkingSnapshot.refresh();

        assertTrue(parkingSnapshot.getFacilityIds().isEmpty());
        assertTrue(parkingSnapshot.getParkingList().isEmpty());
    }

    @Test
    void refresh_shouldParseFacilityIds() {
        ParkingResponse response = mock(ParkingResponse.class);

        when(response.facilityId()).thenReturn("42");
        when(parkingClient.fetchFullList()).thenReturn(List.of(response));
        when(parkingResponseMapper.toParking(response)).thenReturn(mock(Parking.class));

        parkingSnapshot.refresh();

        assertEquals(List.of(42), parkingSnapshot.getFacilityIds());
    }
}
