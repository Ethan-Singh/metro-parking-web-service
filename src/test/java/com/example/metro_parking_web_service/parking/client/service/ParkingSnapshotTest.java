/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @InjectMocks private ParkingSnapshot parkingSnapshot;

    @Mock private ParkingIngestService parkingIngestService;

    @Test
    void refresh_shouldStoreResponses() {
        ParkingResponse r1 = mock(ParkingResponse.class);
        ParkingResponse r2 = mock(ParkingResponse.class);
        when(parkingClient.fetchFullList()).thenReturn(List.of(r1, r2));

        parkingSnapshot.refresh();

        assertEquals(2, parkingSnapshot.getResponses().size());
    }

    @Test
    void refresh_shouldHandleEmptyResponse() {
        when(parkingClient.fetchFullList()).thenReturn(Collections.emptyList());

        parkingSnapshot.refresh();

        assertTrue(parkingSnapshot.getResponses().isEmpty());
    }

    @Test
    void refresh_shouldHandleNullResponse() {
        when(parkingClient.fetchFullList()).thenReturn(null);

        parkingSnapshot.refresh();

        assertTrue(parkingSnapshot.getResponses().isEmpty());
    }
}
