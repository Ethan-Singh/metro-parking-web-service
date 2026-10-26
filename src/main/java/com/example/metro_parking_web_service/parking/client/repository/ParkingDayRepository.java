/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.repository;

import com.example.metro_parking_web_service.parking.client.document.ParkingDay;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ParkingDayRepository extends MongoRepository<ParkingDay, String> {

    Optional<ParkingDay> findByFacilityIdAndSydneyDate(int facilityId, String sydneyDate);

    List<ParkingDay> findByFacilityIdOrderBySydneyDateDesc(int facilityId);

    @Query("{'facilityId': ?0, 'sydneyDate': {'$gte': ?1, '$lte': ?2}}")
    List<ParkingDay> findByFacilityIdAndSydneyDateBetweenOrderBySydneyDateAsc(
            int facilityId, String from, String to);

    long deleteBySydneyDateLessThan(String sydneyDate);
}
