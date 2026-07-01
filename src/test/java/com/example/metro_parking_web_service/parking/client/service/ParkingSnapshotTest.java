/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import com.example.metro_parking_web_service.parking.server.service.ParkingClient;
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
    @Mock private ParkingIngestService parkingIngestService;

    @InjectMocks private ParkingSnapshot parkingSnapshot;

    @Test
    void refresh_shouldStoreResponsesAndIngest() {
        ParkingResponse r1 = mock(ParkingResponse.class);
        ParkingResponse r2 = mock(ParkingResponse.class);

        when(parkingClient.fetchFullList()).thenReturn(List.of(r1, r2));

        parkingSnapshot.refresh();

        assertEquals(2, parkingSnapshot.getResponses().size());
        verify(parkingIngestService).ingest(List.of(r1, r2));
    }

    @Test
    void refresh_shouldHandleEmptyResponse() {
        when(parkingClient.fetchFullList()).thenReturn(Collections.emptyList());

        parkingSnapshot.refresh();

        assertTrue(parkingSnapshot.getResponses().isEmpty());
        verify(parkingIngestService).ingest(Collections.emptyList());
    }

    @Test
    void refresh_shouldHandleNullResponse() {
        when(parkingClient.fetchFullList()).thenReturn(null);

        parkingSnapshot.refresh();

        assertTrue(parkingSnapshot.getResponses().isEmpty());
        verify(parkingIngestService, never()).ingest(any());
    }
}
