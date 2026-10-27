/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.parking.client.document.ParkingDay;
import com.example.metro_parking_web_service.parking.client.repository.ParkingDayRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ParkingSlugServiceTest {

    @Mock private ParkingDayRepository parkingDayRepository;

    @Test
    void refreshesSlugIndexWhenFacilityDocumentAppearsAfterInitialLookup() {
        ParkingSlugService service = new ParkingSlugService(parkingDayRepository);
        ParkingDay ashfield = new ParkingDay();
        ashfield.setFacilityId(486);
        ashfield.setFacilityName("Park&Ride - Ashfield");
        ashfield.setSydneyDate("2026-10-27");
        ParkingDay gordon = new ParkingDay();
        gordon.setFacilityId(6);
        gordon.setFacilityName("Park&Ride - Gordon Henry St (north)");
        gordon.setSydneyDate("2026-10-27");

        when(parkingDayRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(gordon), List.of(gordon, ashfield));

        assertThat(service.facilityIdFromSlug("park-ride-gordon-henry-st-north")).isEqualTo(6);
        assertThat(service.facilityIdFromSlug("park-ride-ashfield")).isEqualTo(486);
    }
}
