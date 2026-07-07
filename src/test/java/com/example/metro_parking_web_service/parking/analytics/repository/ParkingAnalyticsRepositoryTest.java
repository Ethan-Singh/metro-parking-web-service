/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.parking.analytics.dto.DailyOccupancyAggregate;
import com.example.metro_parking_web_service.parking.analytics.dto.HourlyOccupancyAggregate;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import com.example.metro_parking_web_service.parking.client.service.ParkingSnapshot;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Mock private ParkingSnapshot parkingSnapshot;

    @Mock private AggregationResults<ParkingDocument> parkingAggResults;

    @Mock private AggregationResults<HourlyOccupancyAggregate> hourlyAggResults;

    @Mock private AggregationResults<DailyOccupancyAggregate> dailyAggResults;

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
        ParkingDocument doc = new ParkingDocument();
        doc.setFacilityId(1);
        doc.setFacilityName("Ashfield");

        when(parkingSnapshot.getFacilityIds()).thenReturn(List.of(1));

        when(mongoTemplate.findOne(any(Query.class), eq(ParkingDocument.class))).thenReturn(doc);

        List<ParkingDocument> result = repository.findAllLatest();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getFacilityId()).isEqualTo(1);
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

        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = LocalDate.now().plusDays(1).atStartOfDay();

        List<HourlyOccupancyAggregate> result =
                repository.findHourlyAveragesByFacilityAndRange(1, start, end);

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldFindDailyOccupancyAggregate() {
        when(mongoTemplate.aggregate(
                        any(Aggregation.class),
                        eq("parkingDocument"),
                        eq(DailyOccupancyAggregate.class)))
                .thenReturn(dailyAggResults);

        when(dailyAggResults.getMappedResults())
                .thenReturn(List.of(new DailyOccupancyAggregate(null, 100, 20)));

        LocalDateTime start = LocalDate.now().minusDays(7).atStartOfDay();
        LocalDateTime end = LocalDate.now().atStartOfDay();

        List<DailyOccupancyAggregate> result =
                repository.findDailyOccupancyAggregate(1, start, end);

        assertThat(result).hasSize(1);
    }
}
