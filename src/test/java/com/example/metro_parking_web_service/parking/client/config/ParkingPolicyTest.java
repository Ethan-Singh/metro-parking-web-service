/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.metro_parking_web_service.parking.client.dto.Parking;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ParkingPolicyTest {

    @Autowired private ParkingPolicy parkingPolicy;

    @Test
    void shouldBindConfigurationProperties() {
        assertThat(parkingPolicy.getBackfillWindow()).isEqualTo(31);
        assertThat(parkingPolicy.getDisabledFacilities()).contains(1, 2);
    }

    @Test
    void isFacilityAllowed_shouldReturnFalse_whenFacilityDisabled() {
        Parking parking = new Parking(1, "A", 10, 5, LocalDateTime.now());

        assertFalse(parkingPolicy.isParkingFacilityAllowed(parking));
    }

    @Test
    void isFacilityAllowed_shouldReturnTrue_whenFacilityEnabled() {
        Parking parking = new Parking(99, "A", 10, 5, LocalDateTime.now());

        assertTrue(parkingPolicy.isParkingFacilityAllowed(parking));
    }

    @Test
    void clampFromDate_shouldClamp_whenBeforeMinimumAllowed() {
        LocalDate from = LocalDate.now().minusDays(100);
        LocalDate expected = LocalDate.now().minusDays(31);
        LocalDate result = parkingPolicy.clampFromDate(from);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void clampFromDate_shouldNotClamp_whenWithinWindow() {
        LocalDate from = LocalDate.now().minusDays(10);
        LocalDate result = parkingPolicy.clampFromDate(from);

        assertThat(result).isEqualTo(from);
    }

    @Test
    void clampToDate_shouldClamp_whenAfterToday() {
        LocalDate to = LocalDate.now().plusDays(10);
        LocalDate result = parkingPolicy.clampToDate(to);

        assertThat(result).isEqualTo(LocalDate.now());
    }

    @Test
    void clampToDate_shouldNotClamp_whenBeforeOrEqualToday() {
        LocalDate to = LocalDate.now().minusDays(1);
        LocalDate result = parkingPolicy.clampToDate(to);

        assertThat(result).isEqualTo(to);
    }
}
