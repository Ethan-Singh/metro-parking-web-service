/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ParkingIdStrategyTest {

    private final ParkingIdStrategy strategy = new ParkingIdStrategy();

    @Test
    void generateId_shouldCombineFacilityAndTimestamp() {
        LocalDateTime ts = LocalDateTime.of(2025, 1, 1, 12, 30);
        String result = strategy.generateId(123, ts);

        assertEquals(result, "123_" + ts);
    }
}
