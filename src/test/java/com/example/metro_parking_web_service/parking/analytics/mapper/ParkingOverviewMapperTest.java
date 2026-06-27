/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.parking.analytics.dto.Availability;
import com.example.metro_parking_web_service.parking.analytics.dto.ParkingOverviewResponse;
import com.example.metro_parking_web_service.parking.analytics.service.ParkingSlugService;
import com.example.metro_parking_web_service.parking.analytics.service.ParkingStatusService;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingOverviewMapperTest {

    @Mock private ParkingStatusService statusService;
    @Mock private ParkingSlugService slugService;

    @InjectMocks private ParkingOverviewMapper mapper;

    @Test
    void shouldMapOverviewCorrectly() {
        LocalDateTime ts = LocalDateTime.of(2026, 1, 1, 10, 0);

        ParkingDocument doc = new ParkingDocument();
        doc.setFacilityName("Central Station");
        doc.setSpots(100);
        doc.setOccupancy(30);
        doc.setSourceTimestamp(ts);

        when(slugService.toSlug("Central Station")).thenReturn("central-station");

        when(statusService.resolveStatus(70, 100)).thenReturn(Availability.AVAILABLE);

        when(statusService.resolveStatusLabel(Availability.AVAILABLE)).thenReturn("Available");

        ParkingOverviewResponse result = mapper.toOverview(doc);

        assertThat(result.slug()).isEqualTo("central-station");
        assertThat(result.facilityName()).isEqualTo("Central Station");
        assertThat(result.spots()).isEqualTo(100);
        assertThat(result.occupancy()).isEqualTo(30);
        assertThat(result.available()).isEqualTo(70);
        assertThat(result.occupancyRate()).isEqualTo(0.3);
        assertThat(result.availability()).isEqualTo(Availability.AVAILABLE);
        assertThat(result.timestamp()).isEqualTo(ts);
        assertThat(result.ariaLabel())
                .isEqualTo("Central Station, 70 of 100 spots available, availability: Available");
    }

    @Test
    void shouldHandleZeroSpotsGracefully() {
        LocalDateTime ts = LocalDateTime.of(2026, 1, 1, 10, 0);

        ParkingDocument doc = new ParkingDocument();
        doc.setFacilityName("Empty Lot");
        doc.setSpots(0);
        doc.setOccupancy(10);
        doc.setSourceTimestamp(ts);

        when(slugService.toSlug("Empty Lot")).thenReturn("empty-lot");

        when(statusService.resolveStatus(0, 0)).thenReturn(Availability.FULL);

        when(statusService.resolveStatusLabel(Availability.FULL)).thenReturn("Full");

        ParkingOverviewResponse result = mapper.toOverview(doc);

        assertThat(result.available()).isEqualTo(0);
        assertThat(result.occupancyRate()).isEqualTo(0.0);
    }
}
