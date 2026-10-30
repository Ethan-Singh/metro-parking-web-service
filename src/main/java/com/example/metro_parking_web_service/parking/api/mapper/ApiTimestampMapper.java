/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.api.mapper;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ApiTimestampMapper {

    public long toEpochMillis(Instant instant) {
        return instant.toEpochMilli();
    }
}
