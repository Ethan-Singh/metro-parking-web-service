/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.repository;

import com.example.metro_parking_web_service.parking.analytics.dto.DailyOccupancyAggregate;
import com.example.metro_parking_web_service.parking.analytics.dto.HourlyOccupancyAggregate;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ParkingAnalyticsRepository {

    private final MongoTemplate mongoTemplate;

    public ParkingDocument findLatestByFacilityId(int facilityId) {
        var query =
                new org.springframework.data.mongodb.core.query.Query(
                                Criteria.where("facilityId").is(facilityId))
                        .with(Sort.by(Sort.Direction.DESC, "sourceTimestamp"))
                        .limit(1);

        return mongoTemplate.findOne(query, ParkingDocument.class);
    }

    public List<ParkingDocument> findAllLatest() {
        Aggregation agg =
                Aggregation.newAggregation(
                        Aggregation.sort(Sort.Direction.DESC, "sourceTimestamp"),
                        Aggregation.group("facilityId")
                                .first("facilityId")
                                .as("facilityId")
                                .first("facilityName")
                                .as("facilityName")
                                .first("spots")
                                .as("spots")
                                .first("occupancy")
                                .as("occupancy")
                                .first("sourceTimestamp")
                                .as("sourceTimestamp"),
                        Aggregation.sort(Sort.Direction.ASC, "facilityName"));
        AggregationResults<ParkingDocument> results =
                mongoTemplate.aggregate(agg, "parkingDocument", ParkingDocument.class);
        return results.getMappedResults();
    }

    public List<ParkingDocument> findTenMinuteAveragesByFacilityAndRange(
            int facilityId, LocalDateTime start, LocalDateTime end) {

        var query =
                new org.springframework.data.mongodb.core.query.Query(
                                Criteria.where("facilityId")
                                        .is(facilityId)
                                        .and("sourceTimestamp")
                                        .gte(start)
                                        .lt(end))
                        .with(Sort.by(Sort.Direction.ASC, "sourceTimestamp"));

        return mongoTemplate.find(query, ParkingDocument.class);
    }

    public List<HourlyOccupancyAggregate> findHourlyAveragesByFacilityAndRange(
            int facilityId, LocalDateTime start, LocalDateTime end) {

        Aggregation agg =
                Aggregation.newAggregation(
                        Aggregation.match(
                                Criteria.where("facilityId")
                                        .is(facilityId)
                                        .and("sourceTimestamp")
                                        .gte(start)
                                        .lt(end)),
                        Aggregation.project("occupancy", "spots", "sourceTimestamp")
                                .and(
                                        DateOperators.DateTrunc.truncateValueOf("sourceTimestamp")
                                                .to("hour"))
                                .as("hour"),
                        Aggregation.group("hour")
                                .avg("occupancy")
                                .as("occupancy")
                                .first("spots")
                                .as("spots")
                                .min("sourceTimestamp")
                                .as("timestamp"),
                        Aggregation.sort(Sort.Direction.ASC, "timestamp"));

        return mongoTemplate
                .aggregate(agg, "parkingDocument", HourlyOccupancyAggregate.class)
                .getMappedResults();
    }

    public List<DailyOccupancyAggregate> findDailyOccupancyAggregate(
            int facilityId, LocalDateTime start, LocalDateTime end) {

        Aggregation agg =
                Aggregation.newAggregation(
                        Aggregation.match(
                                Criteria.where("facilityId")
                                        .is(facilityId)
                                        .and("sourceTimestamp")
                                        .gte(start)
                                        .lt(end)),
                        Aggregation.project("occupancy", "spots", "sourceTimestamp")
                                .and(
                                        DateOperators.DateTrunc.truncateValueOf("sourceTimestamp")
                                                .to("day"))
                                .as("day"),
                        Aggregation.group("day")
                                .avg("occupancy")
                                .as("avgOccupancy")
                                .max("occupancy")
                                .as("peakOccupancy")
                                .min("occupancy")
                                .as("minOccupancy")
                                .first("spots")
                                .as("spots")
                                .first("day")
                                .as("timestamp"),
                        Aggregation.sort(Sort.by(Sort.Direction.ASC, "_id")));

        return mongoTemplate
                .aggregate(agg, "parkingDocument", DailyOccupancyAggregate.class)
                .getMappedResults();
    }
}
