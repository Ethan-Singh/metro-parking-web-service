/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.parking.client.document.ParkingBackfillCursorDocument;
import com.example.metro_parking_web_service.parking.client.document.ParkingBackfillDocument;
import com.example.metro_parking_web_service.parking.client.repository.ParkingBackfillCursorRepository;
import com.example.metro_parking_web_service.parking.client.repository.ParkingBackfillRepository;
import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingBackfillServiceTest {

    @Mock private ParkingPolicy parkingPolicy;
    @Mock private ParkingClient parkingClient;
    @Mock private ParkingIngestService parkingIngestService;
    @Mock private ParkingBackfillRepository parkingBackfillRepository;
    @Mock private ParkingBackfillCursorRepository parkingBackfillCursorRepository;
    @Mock private ParkingSnapshot parkingSnapshot;

    @InjectMocks private ParkingBackfillService parkingBackfillService;

    @Test
    void backfill_shouldSkipWhenNull() {
        when(parkingSnapshot.getFacilityIds()).thenReturn(null);

        parkingBackfillService.backfill();

        verifyNoInteractions(parkingBackfillRepository);
        verifyNoInteractions(parkingClient);
    }

    @Test
    void backfill_shouldSkipWhenEmpty() {
        when(parkingSnapshot.getFacilityIds()).thenReturn(List.of());

        parkingBackfillService.backfill();

        verifyNoInteractions(parkingBackfillRepository);
        verifyNoInteractions(parkingClient);
    }

    @Test
    void backfill_shouldAdvanceCursorAfterProcessing() {
        when(parkingSnapshot.getFacilityIds()).thenReturn(List.of(1));

        ParkingBackfillDocument state = fullyProcessedState();

        when(parkingBackfillCursorRepository.findById("cursor")).thenReturn(Optional.empty());
        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(state));

        parkingBackfillService.backfill();

        verify(parkingBackfillCursorRepository).save(any(ParkingBackfillCursorDocument.class));
    }

    @Test
    void backfillNext_shouldPickFacilityAfterCursor() {
        ParkingBackfillCursorDocument cursor = new ParkingBackfillCursorDocument();
        cursor.setLastFacilityId(1);

        when(parkingSnapshot.getFacilityIds()).thenReturn(List.of(1, 2));

        ParkingBackfillDocument state = fullyProcessedState();

        when(parkingBackfillCursorRepository.findById("cursor")).thenReturn(Optional.of(cursor));
        when(parkingBackfillRepository.findById(2)).thenReturn(Optional.of(state));

        parkingBackfillService.backfill();

        verify(parkingBackfillRepository).findById(2);
        verify(parkingBackfillRepository, never()).findById(1);
    }

    @Test
    void backfill_shouldWrapAroundWhenCursorExceedsAllIds() {
        ParkingBackfillCursorDocument cursor = new ParkingBackfillCursorDocument();
        cursor.setLastFacilityId(99);

        when(parkingSnapshot.getFacilityIds()).thenReturn(List.of(1, 2));

        ParkingBackfillDocument state = fullyProcessedState();

        when(parkingBackfillCursorRepository.findById("cursor")).thenReturn(Optional.of(cursor));
        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(state));

        parkingBackfillService.backfill();

        verify(parkingBackfillRepository).findById(1);
    }

    @Test
    void backfillFacility_shouldForwardFillYesterdayWhenNoLastForwardDate() {
        ParkingBackfillDocument state = new ParkingBackfillDocument();
        state.setComplete(true);

        when(parkingSnapshot.getFacilityIds()).thenReturn(List.of(1));
        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(state));

        when(parkingClient.fetchHistory(eq(1), eq(LocalDate.now().minusDays(1))))
                .thenReturn(List.of());

        parkingBackfillService.backfill();

        verify(parkingClient).fetchHistory(eq(1), eq(LocalDate.now().minusDays(1)));
        verify(parkingBackfillRepository)
                .save(
                        argThat(
                                saved ->
                                        LocalDate.now()
                                                .minusDays(1)
                                                .equals(saved.getLastForwardDate())));
    }

    @Test
    void backfillFacility_shouldForwardFillNextDayAfterLastForwardDate() {
        LocalDate twoDaysAgo = LocalDate.now().minusDays(2);
        LocalDate yesterday = LocalDate.now().minusDays(1);

        ParkingBackfillDocument state = new ParkingBackfillDocument();
        state.setComplete(true);
        state.setLastForwardDate(twoDaysAgo);

        when(parkingSnapshot.getFacilityIds()).thenReturn(List.of(1));

        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(state));
        when(parkingClient.fetchHistory(eq(1), eq(yesterday))).thenReturn(List.of());

        parkingBackfillService.backfill();

        verify(parkingClient).fetchHistory(eq(1), eq(yesterday));
    }

    @Test
    void backfillFacility_shouldNotForwardFillWhenLastForwardDateIsYesterday() {
        when(parkingSnapshot.getFacilityIds()).thenReturn(List.of(1));

        ParkingBackfillDocument state = fullyProcessedState();

        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(state));

        parkingBackfillService.backfill();

        verifyNoInteractions(parkingClient);
        verifyNoInteractions(parkingIngestService);
    }

    @Test
    void backfillFacility_shouldSkipBackwardWhenComplete() {
        when(parkingSnapshot.getFacilityIds()).thenReturn(List.of(1));

        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(fullyProcessedState()));

        parkingBackfillService.backfill();

        verifyNoInteractions(parkingClient);
        verifyNoInteractions(parkingIngestService);
    }

    @Test
    void backfillFacility_shouldSkipBackwardWhenOutsideWindow() {
        ParkingBackfillDocument state = stateWithForwardFillCurrent();

        when(parkingSnapshot.getFacilityIds()).thenReturn(List.of(1));
        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(state));
        when(parkingPolicy.isOutsideBackfillWindow(state)).thenReturn(true);

        parkingBackfillService.backfill();

        verifyNoInteractions(parkingClient);
        verifyNoInteractions(parkingIngestService);
    }

    @Test
    void backfillFacility_shouldCreateStateWhenMissing() {
        when(parkingSnapshot.getFacilityIds()).thenReturn(List.of(1));
        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.empty());
        when(parkingClient.fetchHistory(anyInt(), any())).thenReturn(Collections.emptyList());

        parkingBackfillService.backfill();

        verify(parkingBackfillRepository).save(any(ParkingBackfillDocument.class));
    }

    @Test
    void backfillFacility_shouldIngestAndAdvanceBackwardWhenHistoryFound() {
        when(parkingSnapshot.getFacilityIds()).thenReturn(List.of(1));

        ParkingBackfillDocument state = stateWithForwardFillCurrent();
        state.setLastProcessedDate(LocalDate.of(2025, 1, 2));

        ParkingResponse response = mockResponse("1");

        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(state));
        when(parkingClient.fetchHistory(eq(1), eq(LocalDate.of(2025, 1, 1))))
                .thenReturn(List.of(response));

        parkingBackfillService.backfill();

        verify(parkingIngestService).ingest(List.of(response));
    }

    @Test
    void backfillFacility_shouldAdvanceBackwardDateEvenWhenHistoryEmpty() {
        when(parkingSnapshot.getFacilityIds()).thenReturn(List.of(1));

        ParkingBackfillDocument state = stateWithForwardFillCurrent();
        state.setLastProcessedDate(LocalDate.of(2025, 1, 2));

        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(state));
        when(parkingClient.fetchHistory(anyInt(), any())).thenReturn(Collections.emptyList());

        parkingBackfillService.backfill();

        verify(parkingBackfillRepository)
                .save(
                        argThat(
                                saved ->
                                        LocalDate.of(2025, 1, 1)
                                                .equals(saved.getLastProcessedDate())));
    }

    @Test
    void backfillFacility_shouldUseYesterdayWhenLastProcessedIsNull() {
        when(parkingSnapshot.getFacilityIds()).thenReturn(List.of(1));

        ParkingBackfillDocument state = stateWithForwardFillCurrent();

        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(state));
        when(parkingClient.fetchHistory(eq(1), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        parkingBackfillService.backfill();

        verify(parkingClient).fetchHistory(eq(1), eq(LocalDate.now()));
    }

    private ParkingBackfillDocument stateWithForwardFillCurrent() {
        ParkingBackfillDocument state = new ParkingBackfillDocument();
        state.setLastForwardDate(LocalDate.now().minusDays(1));
        return state;
    }

    private ParkingBackfillDocument fullyProcessedState() {
        ParkingBackfillDocument state = new ParkingBackfillDocument();
        state.setComplete(true);
        state.setLastForwardDate(LocalDate.now().minusDays(1));
        return state;
    }

    private ParkingResponse mockResponse(String facilityId) {
        return new ParkingResponse(
                facilityId,
                "Kolombo Creek",
                "500",
                new ParkingResponse.Occupancy("200"),
                LocalDateTime.of(2025, 1, 1, 10, 15));
    }
}
