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

    @InjectMocks private ParkingBackfillService parkingBackfillService;

    // ------------------------------------------------------------
    // backfillNext
    // ------------------------------------------------------------

    @Test
    void backfillNext_shouldSkipWhenNull() {
        parkingBackfillService.backfillNext(null);

        verifyNoInteractions(parkingBackfillRepository);
        verifyNoInteractions(parkingClient);
    }

    @Test
    void backfillNext_shouldSkipWhenEmpty() {
        parkingBackfillService.backfillNext(List.of());

        verifyNoInteractions(parkingBackfillRepository);
        verifyNoInteractions(parkingClient);
    }

    @Test
    void backfillNext_shouldAdvanceCursorAfterProcessing() {
        ParkingResponse response = mockResponse("1");
        ParkingBackfillDocument state = completeState();

        when(parkingBackfillCursorRepository.findById("cursor")).thenReturn(Optional.empty());
        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(state));

        parkingBackfillService.backfillNext(List.of(response));

        verify(parkingBackfillCursorRepository).save(any(ParkingBackfillCursorDocument.class));
    }

    @Test
    void backfillNext_shouldPickNextFacilityAfterCursor() {
        ParkingResponse r1 = mockResponse("1");
        ParkingResponse r2 = mockResponse("2");

        ParkingBackfillCursorDocument cursor = new ParkingBackfillCursorDocument();
        cursor.setLastFacilityId(1);

        ParkingBackfillDocument state = completeState();

        when(parkingBackfillCursorRepository.findById("cursor")).thenReturn(Optional.of(cursor));
        when(parkingBackfillRepository.findById(2)).thenReturn(Optional.of(state));

        parkingBackfillService.backfillNext(List.of(r1, r2));

        // facility 2 should be picked (first id > lastId of 1)
        verify(parkingBackfillRepository).findById(2);
        verify(parkingBackfillRepository, never()).findById(1);
    }

    @Test
    void backfillNext_shouldWrapAroundWhenCursorExceedsAllIds() {
        ParkingResponse r1 = mockResponse("1");
        ParkingResponse r2 = mockResponse("2");

        ParkingBackfillCursorDocument cursor = new ParkingBackfillCursorDocument();
        cursor.setLastFacilityId(99); // beyond all facility ids

        ParkingBackfillDocument state = completeState();

        when(parkingBackfillCursorRepository.findById("cursor")).thenReturn(Optional.of(cursor));
        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(state));

        parkingBackfillService.backfillNext(List.of(r1, r2));

        // should wrap to first facility (id=1)
        verify(parkingBackfillRepository).findById(1);
    }

    // ------------------------------------------------------------
    // backfillFacility
    // ------------------------------------------------------------

    @Test
    void backfillFacility_shouldSkipWhenComplete() {
        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(completeState()));

        parkingBackfillService.backfillFacility(1);

        verifyNoInteractions(parkingClient);
        verifyNoInteractions(parkingIngestService);
    }

    @Test
    void backfillFacility_shouldSkipWhenOutsideWindow() {
        ParkingBackfillDocument state = new ParkingBackfillDocument();
        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(state));
        when(parkingPolicy.isOutsideBackfillWindow(state)).thenReturn(true);

        parkingBackfillService.backfillFacility(1);

        verifyNoInteractions(parkingClient);
        verifyNoInteractions(parkingIngestService);
    }

    @Test
    void backfillFacility_shouldCreateStateWhenMissing() {
        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.empty());
        when(parkingClient.fetchHistory(anyInt(), any())).thenReturn(Collections.emptyList());

        parkingBackfillService.backfillFacility(1);

        verify(parkingBackfillRepository).save(any(ParkingBackfillDocument.class));
    }

    @Test
    void backfillFacility_shouldIngestAndAdvanceWhenHistoryFound() {
        ParkingBackfillDocument state = new ParkingBackfillDocument();
        state.setLastProcessedDate(LocalDate.of(2025, 1, 2));

        ParkingResponse response = mockResponse("1");

        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(state));
        when(parkingClient.fetchHistory(eq(1), eq(LocalDate.of(2025, 1, 1))))
                .thenReturn(List.of(response));

        parkingBackfillService.backfillFacility(1);

        verify(parkingIngestService).ingest(List.of(response));
        verify(parkingBackfillRepository).save(any());
    }

    @Test
    void backfillFacility_shouldAdvanceDateEvenWhenHistoryEmpty() {
        ParkingBackfillDocument state = new ParkingBackfillDocument();
        state.setLastProcessedDate(LocalDate.of(2025, 1, 2));

        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(state));
        when(parkingClient.fetchHistory(anyInt(), any())).thenReturn(Collections.emptyList());

        parkingBackfillService.backfillFacility(1);

        verify(parkingIngestService, never()).ingest(any());
        verify(parkingBackfillRepository)
                .save(
                        argThat(
                                saved ->
                                        LocalDate.of(2025, 1, 1)
                                                .equals(saved.getLastProcessedDate())));
    }

    @Test
    void backfillFacility_shouldUseTodayWhenLastProcessedIsNull() {
        ParkingBackfillDocument state = new ParkingBackfillDocument();

        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(state));
        when(parkingClient.fetchHistory(eq(1), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        parkingBackfillService.backfillFacility(1);

        verify(parkingClient).fetchHistory(eq(1), eq(LocalDate.now()));
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private ParkingBackfillDocument completeState() {
        ParkingBackfillDocument state = new ParkingBackfillDocument();
        state.setComplete(true);
        return state;
    }

    private ParkingResponse mockResponse(String facilityId) {
        LocalDateTime messageDate = LocalDateTime.of(2025, 1, 1, 10, 15);
        return new ParkingResponse(
                facilityId,
                "Kolombo Creek",
                "500",
                new ParkingResponse.Occupancy("200"),
                messageDate);
    }
}
