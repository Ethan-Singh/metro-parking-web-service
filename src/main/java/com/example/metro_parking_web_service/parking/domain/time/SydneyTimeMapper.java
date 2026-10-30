/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.domain.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class SydneyTimeMapper {

    public static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");

    public int bucketIndex(LocalDate date, Instant sourceTime) {
        Instant start = date.atStartOfDay(SYDNEY).toInstant();
        long elapsedMinutes = ChronoUnit.MINUTES.between(start, sourceTime);
        return Math.toIntExact(Math.floorDiv(elapsedMinutes, 10));
    }

    public Instant bucketTime(LocalDate date, int bucketIndex) {
        return date.atStartOfDay(SYDNEY).toInstant().plus(bucketIndex * 10L, ChronoUnit.MINUTES);
    }
}
