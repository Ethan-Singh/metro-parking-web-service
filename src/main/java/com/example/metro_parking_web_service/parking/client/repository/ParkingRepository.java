/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.repository;

import com.example.metro_parking_web_service.parking.server.dto.response.Parking;
import java.util.List;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParkingRepository extends MongoRepository<Parking, UUID> {

    List<Parking> findAll();
}
