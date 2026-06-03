/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.repository;

import com.example.metro_parking_web_service.parking.client.document.ParkingBackfillDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParkingBackfillRepository
        extends MongoRepository<ParkingBackfillDocument, Integer> {}
