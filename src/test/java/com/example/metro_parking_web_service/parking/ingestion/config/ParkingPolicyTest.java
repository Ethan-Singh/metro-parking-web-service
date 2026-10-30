/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.ingestion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.metro_parking_web_service.parking.domain.model.Parking;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ParkingPolicyTest {

    @Autowired private ParkingPolicy parkingPolicy;

    @Test
    void shouldBindConfigurationProperties() {
        assertThat(parkingPolicy.getBackfillWindow()).isEqualTo(31);
        assertThat(parkingPolicy.getDisabledFacilities()).contains(1, 2);
    }

    @Test
    void isFacilityAllowed_shouldReturnFalse_whenFacilityDisabled() {
        Parking parking = parking(1);

        assertFalse(parkingPolicy.isParkingFacilityAllowed(parking));
    }

    @Test
    void isFacilityAllowed_shouldReturnTrue_whenFacilityEnabled() {
        Parking parking = parking(99);

        assertTrue(parkingPolicy.isParkingFacilityAllowed(parking));
    }

    @Test
    void clampFromDate_shouldClamp_whenBeforeMinimumAllowed() {
        LocalDate from = LocalDate.now().minusDays(100);
        LocalDate expected = LocalDate.now(ParkingPolicy.SYDNEY_ZONE).minusDays(30);
        LocalDate result = parkingPolicy.clampFromDate(from);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void clampFromDate_shouldNotClamp_whenWithinWindow() {
        LocalDate from = LocalDate.now(ParkingPolicy.SYDNEY_ZONE).minusDays(10);
        LocalDate result = parkingPolicy.clampFromDate(from);

        assertThat(result).isEqualTo(from);
    }

    @Test
    void clampToDate_shouldClamp_whenAfterToday() {
        LocalDate to = LocalDate.now(ParkingPolicy.SYDNEY_ZONE).plusDays(10);
        LocalDate result = parkingPolicy.clampToDate(to);

        assertThat(result).isEqualTo(LocalDate.now(ParkingPolicy.SYDNEY_ZONE));
    }

    @Test
    void clampToDate_shouldNotClamp_whenBeforeOrEqualToday() {
        LocalDate to = LocalDate.now(ParkingPolicy.SYDNEY_ZONE).minusDays(1);
        LocalDate result = parkingPolicy.clampToDate(to);

        assertThat(result).isEqualTo(to);
    }

    private Parking parking(int facilityId) {
        LocalDate date = LocalDate.now(ParkingPolicy.SYDNEY_ZONE);
        Instant timestamp = Instant.now();
        return new Parking(facilityId, "A", 10, 5, timestamp, date, 0);
    }
}
