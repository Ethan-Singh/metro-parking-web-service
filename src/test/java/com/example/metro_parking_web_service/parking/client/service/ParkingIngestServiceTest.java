/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.parking.client.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import com.example.metro_parking_web_service.parking.client.dto.Parking;
import com.example.metro_parking_web_service.parking.client.dto.ParkingDocumentMapper;
import com.example.metro_parking_web_service.parking.client.dto.ParkingIdStrategy;
import com.example.metro_parking_web_service.parking.client.repository.ParkingRepository;
import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import com.example.metro_parking_web_service.parking.server.dto.ParkingResponseMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingIngestServiceTest {

    @Mock private ParkingPolicy parkingPolicy;
    @Mock private ParkingResponseMapper parkingResponseMapper;
    @Mock private ParkingDocumentMapper parkingDocumentMapper;
    @Mock private ParkingIdStrategy parkingIdStrategy;
    @Mock private ParkingRepository parkingRepository;

    @InjectMocks private ParkingIngestService parkingIngestService;

    // ------------------------------------------------------------
    // ingest
    // ------------------------------------------------------------

    @Test
    void ingest_shouldSkipWhenNull() {
        parkingIngestService.ingest(null);

        verifyNoInteractions(parkingRepository);
    }

    @Test
    void ingest_shouldSkipWhenEmpty() {
        parkingIngestService.ingest(List.of());

        verifyNoInteractions(parkingRepository);
    }

    @Test
    void ingest_shouldNotSaveWhenAllFilteredOut() {
        ParkingResponse response = mock(ParkingResponse.class);
        Parking parking = new Parking(1, "A", 10, 5, LocalDateTime.now());

        when(parkingResponseMapper.toParking(response)).thenReturn(parking);
        when(parkingPolicy.isParkingFacilityAllowed(parking)).thenReturn(false);

        parkingIngestService.ingest(List.of(response));

        verifyNoInteractions(parkingRepository);
    }

    @Test
    void ingest_shouldSaveAllowedParkings() {
        ParkingResponse response = mock(ParkingResponse.class);
        Parking parking = new Parking(1, "A", 10, 5, LocalDateTime.now());
        ParkingDocument doc = new ParkingDocument();

        when(parkingResponseMapper.toParking(response)).thenReturn(parking);
        when(parkingPolicy.isParkingFacilityAllowed(parking)).thenReturn(true);
        when(parkingDocumentMapper.toParkingDocument(parking)).thenReturn(doc);
        when(parkingIdStrategy.generateId(anyInt(), any())).thenReturn("id-1");

        parkingIngestService.ingest(List.of(response));

        verify(parkingRepository).saveAll(anyList());
    }

    // ------------------------------------------------------------
    // save
    // ------------------------------------------------------------

    @Test
    void save_shouldNotSaveWhenMapperReturnsNull() {
        Parking parking = new Parking(1, "A", 10, 5, LocalDateTime.now());

        when(parkingDocumentMapper.toParkingDocument(parking)).thenReturn(null);

        parkingIngestService.save(List.of(parking));

        verify(parkingRepository, never()).saveAll(any());
    }

    @Test
    void save_shouldSaveMultipleRecords() {
        Parking p1 = new Parking(1, "A", 10, 5, LocalDateTime.now());
        Parking p2 = new Parking(2, "B", 20, 8, LocalDateTime.now());

        when(parkingDocumentMapper.toParkingDocument(p1)).thenReturn(new ParkingDocument());
        when(parkingDocumentMapper.toParkingDocument(p2)).thenReturn(new ParkingDocument());
        when(parkingIdStrategy.generateId(anyInt(), any())).thenReturn("id");

        parkingIngestService.save(List.of(p1, p2));

        verify(parkingRepository)
                .saveAll(
                        argThat(
                                iterable -> {
                                    List<ParkingDocument> list = new ArrayList<>();
                                    iterable.forEach(list::add);
                                    return list.size() == 2;
                                }));
    }
}
