/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.parking.api.dto.DailyParkingMetricsPayload;
import com.example.metro_parking_web_service.parking.api.dto.ParkingHistoryPayload;
import com.example.metro_parking_web_service.parking.api.mapper.ApiTimestampMapper;
import com.example.metro_parking_web_service.parking.api.mapper.LiveCarParkResponseMapper;
import com.example.metro_parking_web_service.parking.api.repository.ParkingAnalyticsRepository;
import com.example.metro_parking_web_service.parking.domain.time.SydneyTimeMapper;
import com.example.metro_parking_web_service.parking.ingestion.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.ingestion.document.ParkingDay;
import com.github.benmanes.caffeine.cache.Cache;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyParkingMetricsTest {

    @Mock private ParkingAnalyticsRepository repository;
    @Mock private ParkingSlugService slugService;
    @Mock private LiveCarParkResponseMapper liveMapper;
    @Mock private Cache<String, ParkingHistoryPayload> historyCache;

    private ParkingQueryService service;

    @BeforeEach
    void setUp() {
        ParkingPolicy policy = new ParkingPolicy();
        policy.setBackfillWindow(31);
        service =
                new ParkingQueryService(
                        repository,
                        slugService,
                        liveMapper,
                        policy,
                        new SydneyTimeMapper(),
                        new ApiTimestampMapper(),
                        historyCache);
    }

    @Test
    void returnsSydneyDailyMetricsAndNullsForMissingDates() {
        LocalDate today = LocalDate.now(ZoneId.of("Australia/Sydney"));
        LocalDate yesterday = today.minusDays(1);
        when(slugService.facilityIdFromSlug("central-station")).thenReturn(42);
        when(repository.findByFacilityAndDateRange(42, yesterday, today))
                .thenReturn(List.of(day(yesterday)));

        DailyParkingMetricsPayload result =
                service.getDailyMetrics("central-station", yesterday, today);

        assertThat(result.from()).isEqualTo(yesterday.toString());
        assertThat(result.to()).isEqualTo(today.toString());
        assertThat(result.timeZone()).isEqualTo("Australia/Sydney");
        assertThat(result.days()).hasSize(2);
        assertThat(result.days().getFirst())
                .isEqualTo(
                        new DailyParkingMetricsPayload.DailyMetric(
                                yesterday.toString(),
                                60,
                                0,
                                epoch(yesterday, 7, 40),
                                epoch(yesterday, 7, 50),
                                epoch(yesterday, 8, 0)));
        assertThat(result.days().getLast())
                .isEqualTo(
                        new DailyParkingMetricsPayload.DailyMetric(
                                today.toString(), null, null, null, null, null));
    }

    @Test
    void leavesExpectedFreeNullWhenClosestSampleIsOutsideTwentyMinutes() {
        LocalDate yesterday = LocalDate.now(ParkingPolicy.SYDNEY_ZONE).minusDays(1);
        when(slugService.facilityIdFromSlug("central-station")).thenReturn(42);
        when(repository.findByFacilityAndDateRange(42, yesterday, yesterday))
                .thenReturn(
                        List.of(
                                dayWithSamples(
                                        yesterday, List.of(sample(yesterday, 7, 39, 50, 100)))));

        DailyParkingMetricsPayload result =
                service.getDailyMetrics("central-station", yesterday, yesterday);

        assertThat(result.days().getFirst().expectedFreeAt7am()).isNull();
    }

    @Test
    void capsDailyMetricsToMostRecentThreeDates() {
        LocalDate today = LocalDate.now(ParkingPolicy.SYDNEY_ZONE);
        LocalDate effectiveFrom = today.minusDays(2);
        when(slugService.facilityIdFromSlug("central-station")).thenReturn(42);
        when(repository.findByFacilityAndDateRange(42, effectiveFrom, today)).thenReturn(List.of());

        DailyParkingMetricsPayload result =
                service.getDailyMetrics("central-station", today.minusDays(10), today);

        assertThat(result.from()).isEqualTo(effectiveFrom.toString());
        assertThat(result.days()).hasSize(3);
        assertThat(result.days().getFirst().date()).isEqualTo(effectiveFrom.toString());
        assertThat(result.days().getLast().date()).isEqualTo(today.toString());
    }

    private ParkingDay day(LocalDate date) {
        return dayWithSamples(
                date,
                List.of(
                        sample(date, 6, 50, 40, 100),
                        sample(date, 7, 40, 50, 100),
                        sample(date, 7, 50, 90, 100),
                        sample(date, 8, 0, 105, 100)));
    }

    private ParkingDay dayWithSamples(LocalDate date, List<ParkingDay.ParkingSample> samples) {
        ParkingDay day = new ParkingDay();
        day.setFacilityId(42);
        day.setFacilityName("Central Station");
        day.setSydneyDate(date.toString());
        day.setSamples(samples);
        return day;
    }

    private ParkingDay.ParkingSample sample(
            LocalDate date, int hour, int minute, int occupancy, int capacity) {
        return new ParkingDay.ParkingSample(
                0,
                date.atTime(hour, minute).atZone(ParkingPolicy.SYDNEY_ZONE).toInstant(),
                occupancy,
                capacity);
    }

    private long epoch(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute)
                .atZone(ParkingPolicy.SYDNEY_ZONE)
                .toInstant()
                .toEpochMilli();
    }
}
