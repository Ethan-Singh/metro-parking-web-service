/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.service;

import com.example.metro_parking_web_service.parking.analytics.dto.*;
import com.example.metro_parking_web_service.parking.analytics.mapper.ParkingDataPointMapper;
import com.example.metro_parking_web_service.parking.analytics.mapper.ParkingOverviewMapper;
import com.example.metro_parking_web_service.parking.analytics.repository.ParkingAnalyticsRepository;
import com.example.metro_parking_web_service.parking.client.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingAnalyticsService {

    private final ParkingAnalyticsRepository analyticsRepository;
    private final ParkingSlugService slugService;
    private final ParkingOverviewMapper overviewMapper;
    private final ParkingDataPointMapper dataPointMapper;
    private final ParkingPolicy parkingPolicy;

    public List<ParkingOverviewResponse> getAllOverviews() {
        return analyticsRepository.findAllLatest().stream()
                .map(overviewMapper::toOverview)
                .toList();
    }

    public ParkingOverviewResponse getOverview(String slug) {
        int facilityId = slugService.facilityIdFromSlug(slug);
        ParkingDocument document = analyticsRepository.findLatestByFacilityId(facilityId);
        if (document == null) {
            throw new IllegalArgumentException("No data found for facility: " + slug);
        }
        return overviewMapper.toOverview(document);
    }

    public ParkingHistoryResponse getHistory(
            String slug, LocalDate from, LocalDate to, Granularity granularity) {

        from = parkingPolicy.clampFromDate(from);

        LocalDate maxTo = LocalDate.now().minusDays(1);
        to = parkingPolicy.clampToDate(to);
        to = to.isAfter(maxTo) ? maxTo : to;

        if (granularity == Granularity.TEN_MINUTE) {
            if (from.plusDays(7).isBefore(to)) {
                from = to.minusDays(7);
            }
        }

        int facilityId = slugService.facilityIdFromSlug(slug);

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        return switch (granularity) {
            case TEN_MINUTE -> {
                List<ParkingDocument> raw =
                        analyticsRepository.findTenMinuteAveragesByFacilityAndRange(
                                facilityId, start, end);

                yield new ParkingHistoryResponse(
                        slug,
                        from,
                        granularity,
                        raw.stream().map(dataPointMapper::toDataPoint).toList());
            }

            case HOURLY -> {
                List<DataPoint> hourly =
                        analyticsRepository
                                .findHourlyAveragesByFacilityAndRange(facilityId, start, end)
                                .stream()
                                .map(dataPointMapper::toDataPoint)
                                .toList();

                yield new ParkingHistoryResponse(slug, from, granularity, hourly);
            }

            case DAILY -> {
                List<DataPoint> daily =
                        analyticsRepository.findDailySummary(facilityId, start, end).stream()
                                .map(dataPointMapper::toDataPoint)
                                .toList();

                yield new ParkingHistoryResponse(slug, from, granularity, daily);
            }
        };
    }
}
