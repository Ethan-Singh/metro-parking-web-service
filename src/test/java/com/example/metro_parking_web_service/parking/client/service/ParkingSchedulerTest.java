/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import static org.mockito.Mockito.verify;

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
    void sync_shouldRefreshSnapshotThenBackfill() {
        parkingScheduler.sync();

        verify(parkingSnapshot).refresh();
        verify(parkingBackfillService).backfill();
    }

    @Test
    void cleanup_shouldCallBackfillCleanup() {
        parkingScheduler.cleanup();

        verify(parkingBackfillService).cleanup();
    }
}
