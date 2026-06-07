/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.service;

import com.example.metro_parking_web_service.parking.analytics.config.ParkingAnalyticsProperties;
import com.example.metro_parking_web_service.parking.analytics.dto.AvailabilityStatus;
import com.example.metro_parking_web_service.parking.analytics.dto.DailySummaryPoint;
import com.example.metro_parking_web_service.parking.analytics.dto.DataPoint;
import com.example.metro_parking_web_service.parking.analytics.dto.Granularity;
import com.example.metro_parking_web_service.parking.analytics.dto.ParkingHistoryResponse;
import com.example.metro_parking_web_service.parking.analytics.dto.ParkingOverviewResponse;
import com.example.metro_parking_web_service.parking.analytics.dto.ParkingPredictionResponse;
import com.example.metro_parking_web_service.parking.analytics.dto.PredictionPoint;
import com.example.metro_parking_web_service.parking.analytics.mapper.ParkingOverviewMapper;
import com.example.metro_parking_web_service.parking.analytics.repository.ParkingAnalyticsRepository;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import com.example.metro_parking_web_service.parking.client.service.ParkingSlugService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.example.metro_parking_web_service.parking.analytics.service.ParkingAnalyticsLabels.APPROXIMATION;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingAnalyticsService {

    private final ParkingAnalyticsRepository analyticsRepository;
    private final ParkingAnalyticsProperties analyticsProperties;
    private final ParkingSlugService slugService;
    private final ParkingStatusService statusService;
    private final ParkingOverviewMapper overviewMapper;

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
            case RAW_10MIN -> {
                List<ParkingDocument> raw =
                        analyticsRepository.findRawByFacilityAndDate(facilityId, date);
                yield new ParkingHistoryResponse(
                        slug, date, granularity, raw.stream().map(this::toDataPoint).toList());
            }
            case HOURLY -> {
                List<DataPoint> hourly =
                        analyticsRepository.findHourlyAveragesByFacilityAndDate(facilityId, date);
                yield new ParkingHistoryResponse(slug, date, granularity, hourly);
            }
            case DAILY -> {
                List<DailySummaryPoint> daily =
                        analyticsRepository.findDailySummary(facilityId, 30);
                // Return as DataPoint list for consistent shape; daily detail in DailySummaryPoint
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

    public ParkingPredictionResponse getPrediction(String slug) {
        int facilityId = slugService.facilityIdFromSlug(slug);
        int weeks = analyticsProperties.getPredictionWeeks();
        LocalDateTime since = LocalDateTime.now().minusWeeks(weeks);

        List<PredictionPoint> predictions = new ArrayList<>();
        LocalDateTime cursor = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);

        // Predict next 14 days hourly
        for (int day = 0; day < 14; day++) {
            for (int hour = 0; hour < 24; hour++) {
                LocalDateTime slot = cursor.plusDays(day).withHour(hour);
                int dow = slot.getDayOfWeek().getValue(); // 1=Mon..7=Sun

                List<ParkingDocument> historical =
                        analyticsRepository.findByFacilityAndWeekdayAndHourInRange(
                                facilityId, dow, hour, since);

                if (historical.isEmpty()) {
                    continue;
                }

                int spots = historical.getFirst().getSpots();
                double avgOccupancy =
                        historical.stream()
                                .mapToInt(ParkingDocument::getOccupancy)
                                .average()
                                .orElse(0);

                int predictedOccupancy = (int) Math.round(avgOccupancy);
                int predictedAvailable = Math.max(0, spots - predictedOccupancy);
                double confidence = Math.min(1.0, historical.size() / (double) weeks);

                AvailabilityStatus status = statusService.resolveStatus(predictedAvailable, spots);

                predictions.add(
                        new PredictionPoint(
                                slot,
                                predictedOccupancy,
                                predictedAvailable,
                                confidence,
                                status,
                                statusService.resolveStatusLabel(status)));
            }
        }

        return new ParkingPredictionResponse(slug, predictions);
    }

    private DataPoint toDataPoint(ParkingDocument document) {
        int available = Math.max(0, document.getSpots() - document.getOccupancy());
        double rate = document.getSpots() > 0 ? (double) document.getOccupancy() / document.getSpots() : 0.0;
        return new DataPoint(document.getSourceTimestamp(), document.getOccupancy(), available, rate);
    }
}
