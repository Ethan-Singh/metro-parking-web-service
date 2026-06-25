/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingSchedulerTest {

    @Mock private ParkingSnapshot parkingSnapshot;
    @Mock private ParkingBackfillService parkingBackfillService;

    @InjectMocks private ParkingScheduler parkingScheduler;

    @Test
    void sync_shouldRefreshSnapshotThenBackfill() throws Exception {
        invoke("sync");

        verify(parkingSnapshot).refresh();
        verify(parkingBackfillService).backfill();
    }

    @Test
    void sync_shouldCleanupBackfill() throws Exception {
        invoke("cleanup");

        verify(parkingBackfillService).cleanup();
    }

    private void invoke(String methodName) throws Exception {
        Method method = ParkingScheduler.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(parkingScheduler);
    }
}
