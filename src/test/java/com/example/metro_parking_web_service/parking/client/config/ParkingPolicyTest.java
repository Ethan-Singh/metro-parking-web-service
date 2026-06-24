/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.metro_parking_web_service.parking.client.dto.Parking;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ParkingPolicyTest {

    private ParkingPolicy parkingPolicy;

    @BeforeEach
    void setup() {
        parkingPolicy = new ParkingPolicy();
        ReflectionTestUtils.setField(parkingPolicy, "disabledFacilities", Set.of(1, 2));
        ReflectionTestUtils.setField(parkingPolicy, "backfillWindow", 4);
    }

    @Test
    void isParkingAllowed_shouldReturnFalse_whenFacilityDisabled() {
        Parking parking = new Parking(1, "A", 10, 5, LocalDateTime.now());

        assertFalse(parkingPolicy.isParkingAllowed(parking));
    }

    @Test
    void isParkingAllowed_shouldReturnTrue_whenFacilityAllowed() {
        Parking parking = new Parking(99, "A", 10, 5, LocalDateTime.now());

        assertTrue(parkingPolicy.isParkingAllowed(parking));
    }

    @Test
    void clampFromDate_shouldClamp_whenBeforeMinAllowed() {
        LocalDate from = LocalDate.now().minusWeeks(10);

        LocalDate result = parkingPolicy.clampFromDate(from);

        assertThat(result).isEqualTo(LocalDate.now().minusWeeks(4));
    }

    @Test
    void clampFromDate_shouldNotClamp_whenWithinWindow() {
        LocalDate from = LocalDate.now().minusWeeks(2);

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
