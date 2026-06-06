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

    @Mock private ParkingService parkingService;
    @InjectMocks private ParkingScheduler scheduler;

    @Test
    void sync_shouldCallService() throws Exception {
        Method method = ParkingScheduler.class.getDeclaredMethod("sync");
        method.setAccessible(true);
        method.invoke(scheduler);

        verify(parkingService).syncAll();
    }

    @Test
    void backfill_shouldCallService() throws Exception {
        Method method = ParkingScheduler.class.getDeclaredMethod("backfill");
        method.setAccessible(true);
        method.invoke(scheduler);

        verify(parkingService).backfillAll();
    }
}
