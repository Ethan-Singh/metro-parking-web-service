/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.parking.analytics.dto.HistoryResolution;
import com.example.metro_parking_web_service.parking.analytics.dto.ParkingHistoryPayload;
import com.example.metro_parking_web_service.parking.analytics.mapper.ApiTimestampMapper;
import com.example.metro_parking_web_service.parking.analytics.mapper.LiveCarParkResponseMapper;
import com.example.metro_parking_web_service.parking.analytics.repository.ParkingAnalyticsRepository;
import com.example.metro_parking_web_service.parking.client.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.client.document.ParkingDay;
import com.example.metro_parking_web_service.parking.client.time.SydneyTimeMapper;
import com.github.benmanes.caffeine.cache.Cache;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingQueryServiceTest {

    @Mock private ParkingAnalyticsRepository repository;
    @Mock private ParkingSlugService slugService;
    @Mock private LiveCarParkResponseMapper liveMapper;
    @Mock private Cache<String, ParkingHistoryPayload> historyCache;

    private ParkingPolicy policy;
    private ParkingQueryService service;

    @BeforeEach
    void setUp() {
        policy = new ParkingPolicy();
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
    void historyUsesEpochMillisAndCompactTuples() {
        when(slugService.facilityIdFromSlug("central-station")).thenReturn(42);
        LocalDate date = LocalDate.now(ParkingPolicy.SYDNEY_ZONE).minusDays(1);
        when(repository.findByFacilityAndDateRange(42, date, date))
                .thenReturn(List.of(day(date, 55, "2026-10-25T22:19:00Z", 55, 100)));

        ParkingHistoryPayload payload =
                service.getHistoryPayload("central-station", date, date, HistoryResolution.AUTO);

        assertThat(payload.resolution()).isEqualTo(HistoryResolution.TEN_MINUTE);
        assertThat(payload.points()).hasSize(1);
        assertThat(payload.points().getFirst())
                .containsExactly(
                        new SydneyTimeMapper().bucketTime(date, 55).toEpochMilli(), 0.55, 45);
        verify(historyCache)
                .put(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(payload));
    }

    @Test
    void rangeContainingTodayBypassesHistoryCache() {
        when(slugService.facilityIdFromSlug("central-station")).thenReturn(42);
        LocalDate today = LocalDate.now(ParkingPolicy.SYDNEY_ZONE);
        when(repository.findByFacilityAndDateRange(42, today, today)).thenReturn(List.of());

        service.getHistoryPayload("central-station", today, today, HistoryResolution.AUTO);

        verify(historyCache, never()).getIfPresent(org.mockito.ArgumentMatchers.anyString());
        verify(historyCache, never())
                .put(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(ParkingHistoryPayload.class));
    }

    @Test
    void rejectsReversedDateRange() {
        LocalDate today = LocalDate.now(ParkingPolicy.SYDNEY_ZONE);

        assertThatThrownBy(
                        () ->
                                service.getHistoryPayload(
                                        "central-station",
                                        today,
                                        today.minusDays(1),
                                        HistoryResolution.AUTO))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never())
                .findByFacilityAndDateRange(
                        anyInt(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    private ParkingDay day(
            LocalDate date, int bucketIndex, String sourceTime, int occupancy, int capacity) {
        ParkingDay day = new ParkingDay();
        day.setFacilityId(42);
        day.setFacilityName("Central Station");
        day.setSydneyDate(date.toString());
        day.setSamples(
                List.of(
                        new ParkingDay.ParkingSample(
                                bucketIndex, Instant.parse(sourceTime), occupancy, capacity)));
        return day;
    }
}
