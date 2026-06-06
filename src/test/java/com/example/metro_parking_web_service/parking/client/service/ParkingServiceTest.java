/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import static org.mockito.Mockito.*;

import com.example.metro_parking_web_service.parking.client.document.ParkingBackfillDocument;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import com.example.metro_parking_web_service.parking.client.dto.Parking;
import com.example.metro_parking_web_service.parking.client.dto.ParkingDocumentMapper;
import com.example.metro_parking_web_service.parking.client.dto.ParkingIdStrategy;
import com.example.metro_parking_web_service.parking.client.dto.ParkingResponseMapper;
import com.example.metro_parking_web_service.parking.client.repository.ParkingBackfillRepository;
import com.example.metro_parking_web_service.parking.client.repository.ParkingRepository;
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
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ParkingServiceTest {

    @Mock private ParkingClient parkingClient;
    @Mock private ParkingResponseMapper parkingResponseMapper;
    @Mock private ParkingDocumentMapper parkingDocumentMapper;
    @Mock private ParkingIdStrategy parkingIdStrategy;
    @Mock private ParkingRepository parkingRepository;
    @Mock private ParkingBackfillRepository parkingBackfillRepository;
    @Mock private ParkingPolicy parkingPolicy;

    @InjectMocks private ParkingService parkingService;

    // ------------------------------------------------------------
    // syncAll
    // ------------------------------------------------------------

    @Test
    void syncAll_shouldSaveFilteredResults() {
        ParkingResponse parkingResponse = mock(ParkingResponse.class);
        Parking parking = new Parking(1, "A", 10, 5, LocalDateTime.now());

        when(parkingClient.fetchFullList()).thenReturn(List.of(parkingResponse));
        when(parkingResponseMapper.toParking(parkingResponse)).thenReturn(parking);
        when(parkingPolicy.isParkingAllowed(parking)).thenReturn(true);
        when(parkingDocumentMapper.toParkingDocument(parking)).thenReturn(new ParkingDocument());
        when(parkingIdStrategy.generateId(anyInt(), any())).thenReturn("id-1");

        parkingService.syncAll();

        verify(parkingRepository).saveAll(anyList());
    }

    @Test
    void syncAll_shouldRespectPolicyFiltering() {
        ParkingResponse parkingResponse = mock(ParkingResponse.class);
        Parking parking = new Parking(1, "A", 10, 5, LocalDateTime.now());

        when(parkingClient.fetchFullList()).thenReturn(List.of(parkingResponse));
        when(parkingResponseMapper.toParking(parkingResponse)).thenReturn(parking);
        when(parkingPolicy.isParkingAllowed(parking)).thenReturn(false);

        parkingService.syncAll();

        verify(parkingRepository, never()).saveAll(any());
    }

    // ------------------------------------------------------------
    // backfillAll
    // ------------------------------------------------------------

    @Test
    void backfillAll_shouldIterateFacilityIds() {
        ParkingResponse parkingResponse = mock(ParkingResponse.class);
        when(parkingResponse.facilityId()).thenReturn("1");

        when(parkingClient.fetchFullList()).thenReturn(List.of(parkingResponse));

        ParkingBackfillDocument backfillDocument = new ParkingBackfillDocument();

        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(backfillDocument));
        when(parkingClient.fetchHistory(anyInt(), any())).thenReturn(Collections.emptyList());

        parkingService.backfillAll();

        verify(parkingBackfillRepository, atLeastOnce()).save(any());
    }

    // ------------------------------------------------------------
    // backfillFacility
    // ------------------------------------------------------------

    @Test
    void backfillFacility_shouldExitWhenComplete() {
        ParkingBackfillDocument backfillDocument = new ParkingBackfillDocument();
        backfillDocument.setBackfillComplete(true);

        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(backfillDocument));

        parkingService.backfillFacility(1);

        verifyNoInteractions(parkingClient);
        verifyNoInteractions(parkingRepository);
    }

    @Test
    void backfillFacility_shouldExitWhenPolicySaysBeforeBackfillWindow() {
        ParkingBackfillDocument backfillDocument = new ParkingBackfillDocument();

        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(backfillDocument));
        when(parkingPolicy.isBeforeBackfillWindow(backfillDocument)).thenReturn(true);

        parkingService.backfillFacility(1);

        verifyNoInteractions(parkingClient);
        verifyNoInteractions(parkingRepository);
    }

    @Test
    void backfillFacility_shouldCreateStateWhenMissing() {
        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.empty());
        when(parkingBackfillRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(parkingClient.fetchHistory(anyInt(), any())).thenReturn(Collections.emptyList());

        parkingService.backfillFacility(1);

        verify(parkingBackfillRepository).save(any(ParkingBackfillDocument.class));
    }

    @Test
    void backfillFacility_shouldMarkCompleteWhenEmptyHistory() {
        ParkingBackfillDocument backfillDocument = new ParkingBackfillDocument();
        backfillDocument.setLastProcessedDate(LocalDate.of(2025, 1, 2));

        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(backfillDocument));
        when(parkingClient.fetchHistory(anyInt(), any())).thenReturn(Collections.emptyList());

        parkingService.backfillFacility(1);

        verify(parkingBackfillRepository)
                .save(argThat(s -> s.isBackfillComplete() && s.getBackfillUntilDate() != null));
        verify(parkingRepository, never()).saveAll(any());
    }

    @Test
    void backfillFacility_shouldSaveDataAndAdvanceProgress() {
        ParkingBackfillDocument backfillDocument = new ParkingBackfillDocument();
        backfillDocument.setLastProcessedDate(LocalDate.of(2025, 1, 2));

        ParkingResponse response = mock(ParkingResponse.class);
        Parking parking = new Parking(1, "A", 10, 5, LocalDateTime.now());

        when(parkingBackfillRepository.findById(1)).thenReturn(Optional.of(backfillDocument));
        when(parkingClient.fetchHistory(anyInt(), any())).thenReturn(List.of(response));
        when(parkingResponseMapper.toParking(response)).thenReturn(parking);
        when(parkingPolicy.isParkingAllowed(parking)).thenReturn(true);
        when(parkingDocumentMapper.toParkingDocument(parking)).thenReturn(new ParkingDocument());
        when(parkingIdStrategy.generateId(anyInt(), any())).thenReturn("id");

        parkingService.backfillFacility(1);

        verify(parkingRepository).saveAll(anyList());
        verify(parkingBackfillRepository).save(argThat(s -> s.getLastProcessedDate() != null));
    }
}
