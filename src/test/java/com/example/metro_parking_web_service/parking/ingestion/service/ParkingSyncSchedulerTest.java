/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.ingestion.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ParkingSyncSchedulerTest {

    @Mock private ParkingFacilityCatalog catalog;
    @Mock private ParkingBackfillService backfillService;
    @InjectMocks private ParkingSyncScheduler scheduler;

    @Test
    void dueSlotRefreshesCatalogAndNextSlotSyncsHistory() {
        when(catalog.refresh()).thenReturn(true);

        scheduler.syncHistory();
        ReflectionTestUtils.setField(
                scheduler, "facilityRefreshDue", Instant.now().plusSeconds(60));
        scheduler.syncHistory();

        verify(catalog).refresh();
        verify(backfillService).syncHistory();
    }

    @Test
    void cleanupDelegatesToBackfillService() {
        scheduler.cleanup();

        verify(backfillService).cleanup();
    }
}
