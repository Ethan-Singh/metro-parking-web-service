/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.metro_parking_web_service.parking.analytics.config.ParkingAnalyticsProperties;
import com.example.metro_parking_web_service.parking.analytics.dto.Availability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingStatusServiceTest {

    @Mock private ParkingAnalyticsProperties analyticsProperties;

    private ParkingStatusService statusService;

    @BeforeEach
    void setUp() {
        statusService = new ParkingStatusService(analyticsProperties);
    }

    @Test
    void shouldReturnFullWhenNoAvailability() {
        Availability result = statusService.resolveStatus(0, 100);

        assertThat(result).isEqualTo(Availability.FULL);
    }

    @Test
    void shouldReturnAvailableWhenAboveThreshold() {
        when(analyticsProperties.almostFullThreshold()).thenReturn(0.2);

        Availability result = statusService.resolveStatus(50, 100); // 0.5

        assertThat(result).isEqualTo(Availability.AVAILABLE);
    }

    @Test
    void shouldReturnAlmostFullWhenBelowThreshold() {
        when(analyticsProperties.almostFullThreshold()).thenReturn(0.5);

        Availability result = statusService.resolveStatus(20, 100); // 0.2

        assertThat(result).isEqualTo(Availability.ALMOST_FULL);
    }

    @Test
    void shouldHandleZeroSpotsSafely() {
        when(analyticsProperties.almostFullThreshold()).thenReturn(0.5);

        Availability result = statusService.resolveStatus(10, 0);

        assertThat(result).isEqualTo(Availability.ALMOST_FULL);
    }
}
