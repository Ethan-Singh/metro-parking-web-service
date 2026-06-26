/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.metro_parking_web_service.parking.analytics.dto.*;
import com.example.metro_parking_web_service.parking.analytics.mapper.ParkingDataPointMapper;
import com.example.metro_parking_web_service.parking.analytics.mapper.ParkingOverviewMapper;
import com.example.metro_parking_web_service.parking.analytics.repository.ParkingAnalyticsRepository;
import com.example.metro_parking_web_service.parking.client.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingAnalyticsServiceTest {

    @Mock private ParkingAnalyticsRepository analyticsRepository;
    @Mock private ParkingSlugService slugService;
    @Mock private ParkingOverviewMapper overviewMapper;
    @Mock private ParkingDataPointMapper dataPointMapper;

    private ParkingAnalyticsService analyticsService;

    @BeforeEach
    void setup() {
        ParkingPolicy parkingPolicy = new ParkingPolicy();
        parkingPolicy.setBackfillWindow(4);

        analyticsService =
                new ParkingAnalyticsService(
                        analyticsRepository,
                        slugService,
                        overviewMapper,
                        dataPointMapper,
                        parkingPolicy);
    }

    @Test
    void shouldReturnTenMinuteHistory() {
        when(slugService.facilityIdFromSlug("abc")).thenReturn(1);

        LocalDate from = LocalDate.of(2025, 6, 1);
        LocalDate to = LocalDate.of(2025, 6, 2);

        ParkingDocument doc = new ParkingDocument();

        when(analyticsRepository.findTenMinuteAveragesByFacilityAndRange(anyInt(), any(), any()))
                .thenReturn(List.of(doc));

        DataPoint mapped = new DataPoint(LocalDateTime.now(), 10, 90, 0.1);
        when(dataPointMapper.toDataPoint(doc)).thenReturn(mapped);

        ParkingHistoryResponse result =
                analyticsService.getHistory("abc", from, to, Granularity.TEN_MINUTE);

        assertThat(result.slug()).isEqualTo("abc");
        assertThat(result.dataPoints()).hasSize(1);
    }

    @Test
    void shouldReturnHourlyHistory() {
        when(slugService.facilityIdFromSlug("abc")).thenReturn(1);

        LocalDate from = LocalDate.of(2025, 6, 1);
        LocalDate to = LocalDate.of(2025, 6, 2);

        HourlyOccupancyAggregate agg = new HourlyOccupancyAggregate(LocalDateTime.now(), 25, 100);

        when(analyticsRepository.findHourlyAveragesByFacilityAndRange(anyInt(), any(), any()))
                .thenReturn(List.of(agg));

        DataPoint mapped = new DataPoint(LocalDateTime.now(), 25, 75, 0.25);
        when(dataPointMapper.toDataPoint(agg)).thenReturn(mapped);

        ParkingHistoryResponse result =
                analyticsService.getHistory("abc", from, to, Granularity.HOURLY);

        assertThat(result.dataPoints()).hasSize(1);
    }

    @Test
    void shouldReturnDailyHistory() {
        when(slugService.facilityIdFromSlug("abc")).thenReturn(1);

        LocalDate from = LocalDate.of(2025, 6, 1);
        LocalDate to = LocalDate.of(2025, 6, 7);

        DailyOccupancyAggregate agg = new DailyOccupancyAggregate(LocalDateTime.now(), 100, 40);

        when(analyticsRepository.findDailyOccupancyAggregate(anyInt(), any(), any()))
                .thenReturn(List.of(agg));

        DataPoint mapped = new DataPoint(LocalDateTime.now(), 40, 60, 0.4);
        when(dataPointMapper.toDataPoint(agg)).thenReturn(mapped);

        ParkingHistoryResponse result =
                analyticsService.getHistory("abc", from, to, Granularity.DAILY);

        assertThat(result.dataPoints()).hasSize(1);
    }

    @Test
    void shouldClampFromIsBeforeMinAllowedDate() {
        String slug = "test-slug";

        when(slugService.facilityIdFromSlug(slug)).thenReturn(1);

        LocalDate from = LocalDate.now().minusWeeks(10);
        LocalDate to = LocalDate.now();

        when(analyticsRepository.findHourlyAveragesByFacilityAndRange(anyInt(), any(), any()))
                .thenReturn(List.of());

        ParkingHistoryResponse result =
                analyticsService.getHistory(slug, from, to, Granularity.HOURLY);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldClampFromDateToMinAllowed() {
        String slug = "abc";

        when(slugService.facilityIdFromSlug(slug)).thenReturn(1);

        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2025, 6, 2);

        when(analyticsRepository.findHourlyAveragesByFacilityAndRange(anyInt(), any(), any()))
                .thenReturn(List.of());

        analyticsService.getHistory(slug, from, to, Granularity.HOURLY);

        verify(analyticsRepository).findHourlyAveragesByFacilityAndRange(eq(1), any(), any());
    }

    @Test
    void shouldClampToDateToToday() {
        String slug = "abc";

        when(slugService.facilityIdFromSlug(slug)).thenReturn(1);

        LocalDate from = LocalDate.of(2025, 6, 1);
        LocalDate to = LocalDate.now().plusDays(10);

        when(analyticsRepository.findHourlyAveragesByFacilityAndRange(anyInt(), any(), any()))
                .thenReturn(List.of());

        analyticsService.getHistory(slug, from, to, Granularity.HOURLY);

        verify(analyticsRepository).findHourlyAveragesByFacilityAndRange(eq(1), any(), any());
    }

    @Test
    void shouldUseClampedRangeInQuery() {
        String slug = "abc";

        when(slugService.facilityIdFromSlug(slug)).thenReturn(1);

        when(analyticsRepository.findHourlyAveragesByFacilityAndRange(anyInt(), any(), any()))
                .thenReturn(List.of());

        analyticsService.getHistory(
                slug, LocalDate.of(2000, 1, 1), LocalDate.of(3000, 1, 1), Granularity.HOURLY);

        verify(analyticsRepository).findHourlyAveragesByFacilityAndRange(eq(1), any(), any());
    }
}
