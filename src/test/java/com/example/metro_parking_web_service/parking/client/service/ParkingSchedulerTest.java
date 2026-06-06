/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingSchedulerTest {

    @Mock private ParkingService parkingService;
    @Mock private ParkingSnapshot parkingSnapshot;

    @InjectMocks private ParkingScheduler scheduler;

    @Test
    void sync_shouldCallService() throws Exception {
        when(parkingSnapshot.getParkingList()).thenReturn(List.of());

        Method method = ParkingScheduler.class.getDeclaredMethod("sync");
        method.setAccessible(true);
        method.invoke(scheduler);

        verify(parkingSnapshot).refresh();
        verify(parkingSnapshot).getParkingList();
        verify(parkingService).syncAll(anyList());
    }

    @Test
    void backfill_shouldCallService() throws Exception {
        when(parkingSnapshot.getFacilityIds()).thenReturn(List.of(1, 2, 3));

        Method method = ParkingScheduler.class.getDeclaredMethod("backfill");
        method.setAccessible(true);
        method.invoke(scheduler);

        verify(parkingSnapshot).getFacilityIds();
        verify(parkingService).backfillAll(anyList());
    }
}
