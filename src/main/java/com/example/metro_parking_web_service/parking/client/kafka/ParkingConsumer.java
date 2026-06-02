/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.kafka;

import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import com.example.metro_parking_web_service.parking.client.repository.ParkingRepository;
import com.example.metro_parking_web_service.parking.server.mapper.ParkingEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class ParkingConsumer {

    private final ParkingRepository parkingRepository;
    private final ParkingEventMapper parkingEventMapper;

    @KafkaListener(topics = "parking-events", groupId = "parking-storage")
    public void parkingConsume(ParkingEvent parkingEvent) {
        ParkingDocument parkingDocument = parkingEventMapper.toDocument(parkingEvent);

        parkingRepository.save(parkingDocument);
    }
}
