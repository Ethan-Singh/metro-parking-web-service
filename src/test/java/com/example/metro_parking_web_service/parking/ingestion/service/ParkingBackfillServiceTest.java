/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.ingestion.service;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.parking.ingestion.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.ingestion.document.ParkingDay;
import com.example.metro_parking_web_service.parking.ingestion.repository.ParkingDayRepository;
import com.example.metro_parking_web_service.parking.provider.service.CarParkApiClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingBackfillServiceTest {

    @Mock private ParkingPolicy policy;
    @Mock private CarParkApiClient client;
    @Mock private ParkingDayIngestService ingestService;
    @Mock private ParkingDayRepository repository;
    @Mock private ParkingFacilityCatalog catalog;
    @InjectMocks private ParkingBackfillService service;

    @Test
    void everyFifthSlotBackfillsYesterdayAndLiveRefreshCoversEveryFacility() {
        LocalDate today = LocalDate.now(ParkingPolicy.SYDNEY_ZONE);
        when(catalog.getFacilityIds()).thenReturn(List.of(42));
        when(catalog.getFacilityName(42)).thenReturn("Central Station");
        when(policy.getEarliestBackfillDate()).thenReturn(today.minusDays(1));
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(ParkingDay.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(client.fetchHistory(42, today.minusDays(1))).thenReturn(List.of());

        service.refreshLiveParking();
        for (int slot = 0; slot < 5; slot++) service.syncHistory();

        verify(client, times(1)).fetchLive(42);
        verify(client).fetchHistory(42, today.minusDays(1));
        verify(ingestService, times(2)).ingest(anyList());
    }
}
