/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.parking.ingestion.service.ParkingFacilityCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingSlugServiceTest {

    @Mock private ParkingFacilityCatalog parkingCatalog;

    @Test
    void resolvesSlugFromCurrentCatalogSnapshot() {
        ParkingSlugService service = new ParkingSlugService(parkingCatalog);
        when(parkingCatalog.facilityIdForSlug("park-ride-ashfield")).thenReturn(null, 486);

        assertThatThrownBy(() -> service.facilityIdFromSlug("park-ride-ashfield"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown facility slug");
        assertThat(service.facilityIdFromSlug("park-ride-ashfield")).isEqualTo(486);
    }
}
