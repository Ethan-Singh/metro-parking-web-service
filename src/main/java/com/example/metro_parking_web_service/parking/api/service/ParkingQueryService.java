/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.api.service;

import com.example.metro_parking_web_service.parking.api.dto.DailyParkingMetricsPayload;
import com.example.metro_parking_web_service.parking.api.dto.DailyParkingMetricsPayload.DailyMetric;
import com.example.metro_parking_web_service.parking.api.dto.HistoryResolution;
import com.example.metro_parking_web_service.parking.api.dto.LiveCarParkResponse;
import com.example.metro_parking_web_service.parking.api.dto.ParkingHistoryPayload;
import com.example.metro_parking_web_service.parking.api.mapper.ApiTimestampMapper;
import com.example.metro_parking_web_service.parking.api.mapper.LiveCarParkResponseMapper;
import com.example.metro_parking_web_service.parking.api.repository.ParkingAnalyticsRepository;
import com.example.metro_parking_web_service.parking.domain.time.SydneyTimeMapper;
import com.example.metro_parking_web_service.parking.ingestion.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.ingestion.document.ParkingDay;
import com.example.metro_parking_web_service.parking.ingestion.document.ParkingDay.ParkingSample;
import com.github.benmanes.caffeine.cache.Cache;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingQueryService {

    private static final int MAX_POINTS = 200;
    private static final int MAX_DAILY_METRICS_DAYS = 3;
    private static final Duration EXPECTED_FREE_WINDOW = Duration.ofMinutes(20);

    private final ParkingAnalyticsRepository analyticsRepository;
    private final ParkingSlugService slugService;
    private final LiveCarParkResponseMapper liveMapper;
    private final ParkingPolicy parkingPolicy;
    private final SydneyTimeMapper sydneyTimeMapper;
    private final ApiTimestampMapper timestampMapper;
    private final Cache<String, ParkingHistoryPayload> historyCache;

    public List<LiveCarParkResponse> getAllLiveCarParks() {
        return analyticsRepository.findAllLatest().stream()
                .map(day -> liveMapper.toResponse(day, latestSample(day)))
                .toList();
    }

    public LiveCarParkResponse getLiveCarPark(String slug) {
        int facilityId = slugService.facilityIdFromSlug(slug);
        ParkingDay day = analyticsRepository.findLatestByFacilityId(facilityId);
        if (day == null) throw new IllegalArgumentException("No data found for facility: " + slug);
        return liveMapper.toResponse(day, latestSample(day));
    }

    public DailyParkingMetricsPayload getDailyMetrics(
            String slug, LocalDate requestedFrom, LocalDate requestedTo) {
        if (requestedFrom == null || requestedTo == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (requestedFrom.isAfter(requestedTo)) {
            throw new IllegalArgumentException("from must be on or before to");
        }

        LocalDate today = LocalDate.now(ParkingPolicy.SYDNEY_ZONE);
        LocalDate from = parkingPolicy.clampFromDate(requestedFrom);
        LocalDate to = parkingPolicy.clampToDate(requestedTo);
        to = to.isAfter(today) ? today : to;
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Requested date range is outside history");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAILY_METRICS_DAYS) {
            from = to.minusDays(MAX_DAILY_METRICS_DAYS - 1L);
        }

        int facilityId = slugService.facilityIdFromSlug(slug);
        Map<LocalDate, ParkingDay> byDate = new HashMap<>();
        for (ParkingDay day :
                analyticsRepository.findByFacilityAndDateRange(facilityId, from, to)) {
            byDate.put(LocalDate.parse(day.getSydneyDate()), day);
        }

        List<DailyMetric> metrics = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            metrics.add(metricsForDate(date, byDate.get(date)));
        }
        return new DailyParkingMetricsPayload(
                from.toString(), to.toString(), SydneyTimeMapper.SYDNEY.getId(), metrics);
    }

    private DailyMetric metricsForDate(LocalDate date, ParkingDay day) {
        if (day == null || day.getSamples() == null || day.getSamples().isEmpty()) {
            return new DailyMetric(date.toString(), null, null, null, null, null);
        }

        List<ParkingSample> samples =
                day.getSamples().stream()
                        .filter(
                                sample ->
                                        sample.sourceTime()
                                                .atZone(ParkingPolicy.SYDNEY_ZONE)
                                                .toLocalDate()
                                                .equals(date))
                        .sorted(Comparator.comparing(ParkingSample::sourceTime))
                        .toList();

        Integer expectedFreeAt7am = expectedFreeAt(samples, date, LocalTime.of(7, 0));
        Integer expectedFreeAt8am = expectedFreeAt(samples, date, LocalTime.of(8, 0));
        Long crossed50AtMs = firstAtOrAbove(samples, 0.50);
        Long crossed90AtMs = firstAtOrAbove(samples, 0.90);
        Long fullAtMs = firstAtOrAbove(samples, 1.00);

        return new DailyMetric(
                date.toString(),
                expectedFreeAt7am,
                expectedFreeAt8am,
                crossed50AtMs,
                crossed90AtMs,
                fullAtMs);
    }

    private Integer expectedFreeAt(List<ParkingSample> samples, LocalDate date, LocalTime target) {
        Instant targetTime = date.atTime(target).atZone(ParkingPolicy.SYDNEY_ZONE).toInstant();
        ParkingSample closest = null;
        Duration closestDistance = null;
        for (ParkingSample sample : samples) {
            Duration distance = Duration.between(targetTime, sample.sourceTime()).abs();
            if (distance.compareTo(EXPECTED_FREE_WINDOW) > 0) continue;
            if (closestDistance == null
                    || distance.compareTo(closestDistance) < 0
                    || (distance.equals(closestDistance)
                            && sample.sourceTime().isBefore(closest.sourceTime()))) {
                closest = sample;
                closestDistance = distance;
            }
        }
        return closest == null ? null : Math.max(0, closest.capacity() - closest.occupancy());
    }

    private Long firstAtOrAbove(List<ParkingSample> samples, double threshold) {
        return samples.stream()
                .filter(sample -> sample.capacity() > 0)
                .filter(sample -> (double) sample.occupancy() / sample.capacity() >= threshold)
                .map(sample -> timestampMapper.toEpochMillis(sample.sourceTime()))
                .findFirst()
                .orElse(null);
    }

    public ParkingHistoryPayload getHistoryPayload(
            String slug,
            LocalDate requestedFrom,
            LocalDate requestedTo,
            HistoryResolution requestedResolution) {
        if (requestedFrom == null || requestedTo == null || requestedResolution == null) {
            throw new IllegalArgumentException("from, to, and resolution are required");
        }
        if (requestedFrom.isAfter(requestedTo)) {
            throw new IllegalArgumentException("from must be on or before to");
        }

        LocalDate today = LocalDate.now(ParkingPolicy.SYDNEY_ZONE);
        LocalDate from = parkingPolicy.clampFromDate(requestedFrom);
        LocalDate to = parkingPolicy.clampToDate(requestedTo);
        to = to.isAfter(today) ? today : to;
        if (from.isAfter(to))
            throw new IllegalArgumentException("Requested date range is outside history");

        int spanDays = Math.toIntExact(ChronoUnit.DAYS.between(from, to) + 1);
        if (spanDays > 31) from = to.minusDays(30);
        spanDays = Math.toIntExact(ChronoUnit.DAYS.between(from, to) + 1);

        HistoryResolution resolution = resolveResolution(requestedResolution, spanDays);
        int facilityId = slugService.facilityIdFromSlug(slug);
        String cacheKey = facilityId + "|" + from + "|" + to + "|" + resolution;
        boolean includesToday = to.equals(today);
        if (!includesToday) {
            ParkingHistoryPayload cached = historyCache.getIfPresent(cacheKey);
            if (cached != null) return cached;
        }

        Map<Instant, BucketAccumulator> buckets = new TreeMap<>();
        List<ParkingDay> days =
                analyticsRepository.findByFacilityAndDateRange(facilityId, from, to);
        for (ParkingDay day : days) {
            LocalDate date = LocalDate.parse(day.getSydneyDate());
            if (day.getSamples() == null) continue;
            for (ParkingSample sample : day.getSamples()) {
                Instant bucketTime = bucketStart(date, sample.bucketIndex(), resolution);
                buckets.computeIfAbsent(bucketTime, ignored -> new BucketAccumulator())
                        .add(sample.occupancy(), sample.capacity());
            }
        }

        List<List<Number>> points = new ArrayList<>(buckets.size());
        buckets.forEach(
                (time, value) -> {
                    double averageOccupancy = value.occupancySum / value.count;
                    double averageCapacity = value.capacitySum / value.count;
                    double rate = averageCapacity > 0 ? averageOccupancy / averageCapacity : 0;
                    int available =
                            Math.max(0, (int) Math.round(averageCapacity - averageOccupancy));
                    List<Number> point = new ArrayList<>(3);
                    point.add(timestampMapper.toEpochMillis(time));
                    point.add(Math.round(rate * 10_000.0) / 10_000.0);
                    point.add(available);
                    points.add(point);
                });

        ParkingHistoryPayload result =
                new ParkingHistoryPayload(from.toString(), to.toString(), resolution, points);
        if (!includesToday) historyCache.put(cacheKey, result);
        return result;
    }

    private HistoryResolution resolveResolution(HistoryResolution requested, int spanDays) {
        if (requested == HistoryResolution.AUTO) {
            if (spanDays <= 1) return HistoryResolution.TEN_MINUTE;
            if (spanDays <= 7) return HistoryResolution.ONE_HOUR;
            if (spanDays <= 14) return HistoryResolution.SIX_HOUR;
            return HistoryResolution.TWENTY_FOUR_HOUR;
        }

        HistoryResolution resolved = requested;
        while (maximumPointEstimate(spanDays, resolved) > MAX_POINTS) {
            resolved = coarser(resolved);
        }
        return resolved;
    }

    private int maximumPointEstimate(int days, HistoryResolution resolution) {
        return switch (resolution) {
            case AUTO -> Integer.MAX_VALUE;
            case TEN_MINUTE -> days * 150;
            case ONE_HOUR -> days * 25;
            case SIX_HOUR -> days * 5;
            case TWELVE_HOUR -> days * 3;
            case TWENTY_FOUR_HOUR -> days;
        };
    }

    private HistoryResolution coarser(HistoryResolution resolution) {
        return switch (resolution) {
            case AUTO, TEN_MINUTE -> HistoryResolution.ONE_HOUR;
            case ONE_HOUR -> HistoryResolution.SIX_HOUR;
            case SIX_HOUR -> HistoryResolution.TWELVE_HOUR;
            case TWELVE_HOUR -> HistoryResolution.TWENTY_FOUR_HOUR;
            case TWENTY_FOUR_HOUR -> HistoryResolution.TWENTY_FOUR_HOUR;
        };
    }

    private Instant bucketStart(LocalDate date, int bucketIndex, HistoryResolution resolution) {
        Instant tenMinute = sydneyTimeMapper.bucketTime(date, bucketIndex);
        ZonedDateTime local = tenMinute.atZone(ParkingPolicy.SYDNEY_ZONE);
        return switch (resolution) {
            case TEN_MINUTE -> tenMinute;
            case ONE_HOUR -> local.withMinute(0).withSecond(0).withNano(0).toInstant();
            case SIX_HOUR ->
                    local.withHour((local.getHour() / 6) * 6)
                            .withMinute(0)
                            .withSecond(0)
                            .withNano(0)
                            .toInstant();
            case TWELVE_HOUR ->
                    local.withHour((local.getHour() / 12) * 12)
                            .withMinute(0)
                            .withSecond(0)
                            .withNano(0)
                            .toInstant();
            case TWENTY_FOUR_HOUR -> date.atStartOfDay(ParkingPolicy.SYDNEY_ZONE).toInstant();
            case AUTO ->
                    throw new IllegalStateException("AUTO must resolve to a concrete interval");
        };
    }

    private ParkingSample latestSample(ParkingDay day) {
        return day.getSamples().stream()
                .max(Comparator.comparing(ParkingSample::sourceTime))
                .orElseThrow();
    }

    private static final class BucketAccumulator {
        private double occupancySum;
        private double capacitySum;
        private int count;

        void add(int occupancy, int capacity) {
            occupancySum += occupancy;
            capacitySum += capacity;
            count++;
        }
    }
}
