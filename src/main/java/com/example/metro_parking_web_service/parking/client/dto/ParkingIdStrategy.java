/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.dto;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class ParkingIdStrategy {

    public String generateId(int facilityId, LocalDateTime timestamp) {
        return facilityId + "_" + timestamp;
    }
}
