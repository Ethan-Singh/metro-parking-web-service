/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.kafka;

import com.example.metro_parking_web_service.parking.client.dto.Parking;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class ParkingProducer {

    private final KafkaTemplate<String, ParkingEvent> kafkaTemplate;

    public void publishParkingEvent(Parking parking) {
        ParkingEvent parkingEvent =
                new ParkingEvent(
                        parking.facilityId(),
                        parking.facilityName(),
                        parking.spots(),
                        parking.occupancy(),
                        parking.messageDate(),
                        UUID.randomUUID(),
                        Instant.now());

        kafkaTemplate.send("parking-events", parkingEvent.facilityId(), parkingEvent);
    }
}
