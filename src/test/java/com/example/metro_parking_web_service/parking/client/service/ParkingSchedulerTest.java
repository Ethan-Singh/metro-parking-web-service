/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingSchedulerTest {

    @Mock private ParkingSnapshot parkingSnapshot;
    @Mock private ParkingIngestService parkingIngestService;
    @Mock private ParkingBackfillService parkingBackfillService;

    @InjectMocks private ParkingScheduler parkingScheduler;

    @Test
    void sync_shouldRefreshSnapshotThenIngest() throws Exception {
        when(parkingSnapshot.getResponses()).thenReturn(List.of());

        invoke("sync");

        verify(parkingSnapshot).refresh();
        verify(parkingIngestService).ingest(anyList());
    }

    @Test
    void backfill_shouldDelegateToBackfillService() throws Exception {
        ParkingResponse response = org.mockito.Mockito.mock(ParkingResponse.class);
        when(parkingSnapshot.getResponses()).thenReturn(List.of(response));

        invoke("backfill");

        verify(parkingBackfillService).backfillNext(anyList());
    }

    private void invoke(String methodName) throws Exception {
        Method method = ParkingScheduler.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(parkingScheduler);
    }
}
