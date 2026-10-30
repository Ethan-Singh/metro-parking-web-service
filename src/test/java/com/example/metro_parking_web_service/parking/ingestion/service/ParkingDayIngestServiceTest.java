/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.parking.domain.model.Parking;
import com.example.metro_parking_web_service.parking.ingestion.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.ingestion.document.ParkingDay;
import com.example.metro_parking_web_service.parking.ingestion.repository.ParkingDayRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingDayIngestServiceTest {

    @Mock private ParkingPolicy parkingPolicy;
    @Mock private ParkingDayRepository repository;
    @InjectMocks private ParkingDayIngestService service;

    @Test
    void savesOneDailyDocumentAndKeepsNewestSampleInBucket() {
        when(parkingPolicy.isParkingFacilityAllowed(any(Parking.class))).thenReturn(true);
        when(repository.findByFacilityIdAndSydneyDate(42, "2026-10-26"))
                .thenReturn(Optional.empty());
        Parking older = observation("2026-10-25T22:11:00Z", 11, 70);
        Parking newer = observation("2026-10-25T22:19:00Z", 19, 65);
        service.ingestMapped(java.util.List.of(older, newer));

        ArgumentCaptor<ParkingDay> captor = ArgumentCaptor.forClass(ParkingDay.class);
        verify(repository).save(captor.capture());
        ParkingDay saved = captor.getValue();
        assertThat(saved.getFacilityId()).isEqualTo(42);
        assertThat(saved.getSydneyDate()).isEqualTo("2026-10-26");
        assertThat(saved.getSamples()).hasSize(1);
        assertThat(saved.getSamples().getFirst().sourceTime())
                .isEqualTo(Instant.parse("2026-10-25T22:19:00Z"));
        assertThat(saved.getSamples().getFirst().occupancy()).isEqualTo(65);
    }

    @Test
    void ignoresExcludedFacilities() {
        when(parkingPolicy.isParkingFacilityAllowed(any(Parking.class))).thenReturn(false);

        service.ingestMapped(java.util.List.of(observation("2026-10-25T22:19:00Z", 19, 65)));

        verify(repository, never()).save(any());
    }

    private Parking observation(String timestamp, int bucket, int occupancy) {
        return new Parking(
                42,
                "Central Station",
                100,
                occupancy,
                Instant.parse(timestamp),
                LocalDate.of(2026, 10, 26),
                55);
    }
}
