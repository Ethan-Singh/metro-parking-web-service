/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.server.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.metro_parking_web_service.parking.client.time.SydneyTimeMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CarParkApiRecordMapperTest {

    private final CarParkApiRecordMapper mapper =
            new CarParkApiRecordMapper(new SydneyTimeMapper());

    @Test
    void mapsProviderSydneyTimeToUtcAndTenMinuteBucket() {
        CarParkApiRecord record =
                new CarParkApiRecord(
                        "42",
                        "Central Station",
                        "100",
                        new CarParkApiRecord.Occupancy("61"),
                        LocalDateTime.of(2026, 10, 26, 9, 19));

        var result = mapper.toParking(record);

        assertThat(result.facilityId()).isEqualTo(42);
        assertThat(result.sydneyDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(result.sourceTimestamp()).isEqualTo(Instant.parse("2026-10-25T22:19:00Z"));
        assertThat(result.bucketIndex()).isEqualTo(55);
    }

    @Test
    void skipsMalformedProviderRecords() {
        CarParkApiRecord record =
                new CarParkApiRecord(
                        "not-an-id",
                        "Central Station",
                        "100",
                        new CarParkApiRecord.Occupancy("61"),
                        LocalDateTime.of(2026, 10, 26, 9, 0));

        assertThat(mapper.toParking(record)).isNull();
    }
}
