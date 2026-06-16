/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.repository;

import com.example.metro_parking_web_service.parking.analytics.document.ParkingPredictionCacheDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParkingPredictionCacheRepository
        extends MongoRepository<ParkingPredictionCacheDocument, String> {}
