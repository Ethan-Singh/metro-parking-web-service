/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.parking.ingestion.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.provider.service.CarParkApiClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingFacilityCatalogTest {

    @Mock private CarParkApiClient client;
    @Mock private ParkingPolicy policy;
    @InjectMocks private ParkingFacilityCatalog catalog;

    @Test
    void refreshParsesSortsAndExcludesFacilities() {
        when(client.fetchFacilities())
                .thenReturn(Map.of("42", "Central Station", "7", "Harbour Parking", "bad", "X"));
        when(policy.isFacilityAllowed(42)).thenReturn(true);
        when(policy.isFacilityAllowed(7)).thenReturn(false);

        assertThat(catalog.refresh()).isTrue();
        assertThat(catalog.getFacilityIds()).containsExactly(42);
        assertThat(catalog.getFacilityName(42)).isEqualTo("Central Station");
        assertThat(catalog.facilityIdForSlug("central-station")).isEqualTo(42);
    }

    @Test
    void emptyRefreshKeepsLastKnownCatalog() {
        when(client.fetchFacilities()).thenReturn(Map.of("42", "Central Station"), Map.of());
        when(policy.isFacilityAllowed(42)).thenReturn(true);

        assertThat(catalog.refresh()).isTrue();
        assertThat(catalog.refresh()).isFalse();
        assertThat(catalog.getFacilityIds()).containsExactly(42);
    }
}
