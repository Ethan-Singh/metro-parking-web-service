/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.parking.analytics.dto.DailySummaryAggregate;
import com.example.metro_parking_web_service.parking.analytics.dto.HourlyOccupancyAggregate;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class ParkingAnalyticsRepositoryTest {

    @Mock private MongoTemplate mongoTemplate;

    @Mock private AggregationResults<ParkingDocument> parkingAggResults;

    @Mock private AggregationResults<HourlyOccupancyAggregate> hourlyAggResults;

    @Mock private AggregationResults<DailySummaryAggregate> dailyAggResults;

    @InjectMocks private ParkingAnalyticsRepository repository;

    @Test
    void shouldFindLatestByFacilityId() {
        ParkingDocument doc = new ParkingDocument();
        doc.setFacilityId(1);

        when(mongoTemplate.findOne(any(Query.class), eq(ParkingDocument.class))).thenReturn(doc);

        ParkingDocument result = repository.findLatestByFacilityId(1);

        assertThat(result.getFacilityId()).isEqualTo(1);
    }

    @Test
    void shouldFindAllLatest() {
        when(mongoTemplate.aggregate(
                        any(Aggregation.class), eq("parkingDocument"), eq(ParkingDocument.class)))
                .thenReturn(parkingAggResults);

        when(parkingAggResults.getMappedResults()).thenReturn(List.of(new ParkingDocument()));

        List<ParkingDocument> result = repository.findAllLatest();

        assertThat(result).isNotNull();
    }

    @Test
    void shouldFindHourlyAggregates() {
        when(mongoTemplate.aggregate(
                        any(Aggregation.class),
                        eq("parkingDocument"),
                        eq(HourlyOccupancyAggregate.class)))
                .thenReturn(hourlyAggResults);

        when(hourlyAggResults.getMappedResults())
                .thenReturn(List.of(new HourlyOccupancyAggregate(null, 10, 100)));

        List<HourlyOccupancyAggregate> result =
                repository.findHourlyAveragesByFacilityAndDate(1, LocalDate.now());

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldFindDailySummary() {
        when(mongoTemplate.aggregate(
                        any(Aggregation.class),
                        eq("parkingDocument"),
                        eq(DailySummaryAggregate.class)))
                .thenReturn(dailyAggResults);

        when(dailyAggResults.getMappedResults())
                .thenReturn(List.of(new DailySummaryAggregate(null, 100, 20, 30, 10)));

        List<DailySummaryAggregate> result = repository.findDailySummary(1, 7);

        assertThat(result).hasSize(1);
    }
}
