/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.service;

import com.example.metro_parking_web_service.parking.analytics.dto.*;
import com.example.metro_parking_web_service.parking.analytics.mapper.ParkingDataPointMapper;
import com.example.metro_parking_web_service.parking.analytics.mapper.ParkingOverviewMapper;
import com.example.metro_parking_web_service.parking.analytics.repository.ParkingAnalyticsRepository;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import java.time.LocalDate;
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

    public ParkingHistoryResponse getHistory(String slug, LocalDate date, Granularity granularity) {
        int facilityId = slugService.facilityIdFromSlug(slug);

        return switch (granularity) {
            case TEN_MINUTE -> {
                List<ParkingDocument> raw =
                        analyticsRepository.findTenMinuteAveragesByFacilityAndDate(
                                facilityId, date);
                yield new ParkingHistoryResponse(
                        slug,
                        date,
                        granularity,
                        raw.stream().map(dataPointMapper::toDataPoint).toList());
            }
            case HOURLY -> {
                List<DataPoint> hourly =
                        analyticsRepository
                                .findHourlyAveragesByFacilityAndDate(facilityId, date)
                                .stream()
                                .map(dataPointMapper::toDataPoint)
                                .toList();

                yield new ParkingHistoryResponse(slug, date, granularity, hourly);
            }
            case DAILY -> {
                List<DataPoint> daily =
                        analyticsRepository.findDailySummary(facilityId, 30).stream()
                                .map(dataPointMapper::toDataPoint)
                                .toList();
                List<DataPoint> points =
                        daily.stream()
                                .map(
                                        d ->
                                                new DataPoint(
                                                        d.timestamp(),
                                                        d.occupancy(),
                                                        d.available(),
                                                        d.occupancyRate()))
                                .toList();
                yield new ParkingHistoryResponse(slug, date, granularity, points);
            }
        };
    }
}
