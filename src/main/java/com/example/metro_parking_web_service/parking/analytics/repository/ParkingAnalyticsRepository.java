/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.repository;

import com.example.metro_parking_web_service.parking.analytics.dto.DailyOccupancyAggregate;
import com.example.metro_parking_web_service.parking.analytics.dto.HourlyOccupancyAggregate;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import com.example.metro_parking_web_service.parking.client.service.ParkingSnapshot;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ParkingAnalyticsRepository {

    private final MongoTemplate mongoTemplate;
    private final ParkingSnapshot parkingSnapshot;

    public ParkingDocument findLatestByFacilityId(int facilityId) {
        var query =
                new org.springframework.data.mongodb.core.query.Query(
                                Criteria.where("facilityId").is(facilityId))
                        .with(Sort.by(Sort.Direction.DESC, "sourceTimestamp"))
                        .limit(1);

        return mongoTemplate.findOne(query, ParkingDocument.class);
    }

    public List<ParkingDocument> findAllLatest() {
        return parkingSnapshot.getFacilityIds().stream()
                .map(this::findLatestByFacilityId)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ParkingDocument::getFacilityName))
                .toList();
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
