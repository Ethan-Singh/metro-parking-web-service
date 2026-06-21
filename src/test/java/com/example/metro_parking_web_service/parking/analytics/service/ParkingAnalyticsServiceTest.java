/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.metro_parking_web_service.parking.analytics.dto.*;
import com.example.metro_parking_web_service.parking.analytics.mapper.ParkingDataPointMapper;
import com.example.metro_parking_web_service.parking.analytics.repository.ParkingAnalyticsRepository;
import com.example.metro_parking_web_service.parking.client.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingAnalyticsServiceTest {

    @Mock private ParkingAnalyticsRepository analyticsRepository;
    @Mock private ParkingSlugService slugService;
    @Mock private ParkingDataPointMapper dataPointMapper;
    @Mock private ParkingPolicy parkingPolicy;

    @InjectMocks private ParkingAnalyticsService service;

    @Test
    void shouldReturnTenMinuteHistory() {
        when(slugService.facilityIdFromSlug("abc")).thenReturn(1);
        when(parkingPolicy.getBackfillWindow()).thenReturn(1000);

        LocalDate from = LocalDate.of(2025, 6, 1);
        LocalDate to = LocalDate.of(2025, 6, 2);

        ParkingDocument doc = new ParkingDocument();

        when(analyticsRepository.findTenMinuteAveragesByFacilityAndRange(
                        eq(1), eq(from.atStartOfDay()), eq(to.plusDays(1).atStartOfDay())))
                .thenReturn(List.of(doc));

        DataPoint mapped = new DataPoint(LocalDateTime.now(), 10, 90, 0.1);
        when(dataPointMapper.toDataPoint(doc)).thenReturn(mapped);

        ParkingHistoryResponse result = service.getHistory("abc", from, to, Granularity.TEN_MINUTE);

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

        ParkingHistoryResponse result = service.getHistory("abc", from, to, Granularity.HOURLY);

        assertThat(result.dataPoints()).hasSize(1);
    }

    @Test
    void shouldReturnDailyHistory() {
        when(slugService.facilityIdFromSlug("abc")).thenReturn(1);

        LocalDate from = LocalDate.of(2025, 6, 1);
        LocalDate to = LocalDate.of(2025, 6, 7);

        DailySummaryAggregate agg = new DailySummaryAggregate(LocalDateTime.now(), 100, 40, 60, 10);

        when(analyticsRepository.findDailySummary(anyInt(), any(), any())).thenReturn(List.of(agg));

        DataPoint mapped = new DataPoint(LocalDateTime.now(), 40, 60, 0.4);
        when(dataPointMapper.toDataPoint(agg)).thenReturn(mapped);

        ParkingHistoryResponse result = service.getHistory("abc", from, to, Granularity.DAILY);

        assertThat(result.dataPoints()).hasSize(1);
    }

    @Test
    void shouldClampFromDateWhenOutsideBackfillWindow() {
        String slug = "test-slug";

        when(slugService.facilityIdFromSlug(slug)).thenReturn(1);
        when(parkingPolicy.getBackfillWindow()).thenReturn(4);

        LocalDate now = LocalDate.now();
        LocalDate from = now.minusWeeks(10);
        LocalDate to = now;

        when(analyticsRepository.findHourlyAveragesByFacilityAndRange(eq(1), any(), any()))
                .thenReturn(List.of());

        ParkingHistoryResponse result = service.getHistory(slug, from, to, Granularity.HOURLY);

        assertThat(result).isNotNull();
    }
}
