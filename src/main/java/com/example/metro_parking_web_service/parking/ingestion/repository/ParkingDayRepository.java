/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.ingestion.repository;

import com.example.metro_parking_web_service.parking.ingestion.document.ParkingDay;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ParkingDayRepository extends MongoRepository<ParkingDay, String> {

    Optional<ParkingDay> findByFacilityIdAndSydneyDate(int facilityId, String sydneyDate);

    @Query(
            value = "{'sydneyDate': {'$gte': ?0, '$lte': ?1}}",
            fields =
                    "{'_id': 1, 'facilityId': 1, 'sydneyDate': 1, 'backfillState': 1, "
                            + "'retryAfter': 1, 'leaseUntil': 1}")
    List<ParkingDay> findBackfillMetadataBetweenDates(String from, String to);

    @Aggregation(
            pipeline = {
                "{ $match: { facilityId: { $in: ?0 }, 'samples.0': { $exists: true } } }",
                "{ $sort: { facilityId: -1, sydneyDate: -1 } }",
                "{ $group: { _id: '$facilityId', latest: { $first: '$$ROOT' } } }",
                "{ $replaceRoot: { newRoot: '$latest' } }"
            })
    List<ParkingDay> findLatestPopulatedDaysByFacilityIds(List<Integer> facilityIds);

    @Query("{'facilityId': ?0, 'sydneyDate': {'$gte': ?1, '$lte': ?2}}")
    List<ParkingDay> findByFacilityIdAndSydneyDateBetweenOrderBySydneyDateAsc(
            int facilityId, String from, String to);

    long deleteBySydneyDateLessThan(String sydneyDate);
}
