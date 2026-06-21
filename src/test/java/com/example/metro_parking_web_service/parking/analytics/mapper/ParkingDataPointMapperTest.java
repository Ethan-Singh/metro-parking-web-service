/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.metro_parking_web_service.parking.analytics.dto.DailySummaryAggregate;
import com.example.metro_parking_web_service.parking.analytics.dto.DataPoint;
import com.example.metro_parking_web_service.parking.analytics.dto.HourlyOccupancyAggregate;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ParkingDataPointMapperTest {

    private final ParkingDataPointMapper mapper = new ParkingDataPointMapper();

    private ParkingDocument parkingDocument(int spots, int occupancy, LocalDateTime timestamp) {
        ParkingDocument document = new ParkingDocument();
        document.setSpots(spots);
        document.setOccupancy(occupancy);
        document.setSourceTimestamp(timestamp);
        return document;
    }

    @Test
    void shouldMapParkingDocument() {
        LocalDateTime timestamp = LocalDateTime.now();

        DataPoint result = mapper.toDataPoint(parkingDocument(100, 25, timestamp));

        assertThat(result.timestamp()).isEqualTo(timestamp);
        assertThat(result.occupancy()).isEqualTo(25);
        assertThat(result.available()).isEqualTo(75);
        assertThat(result.occupancyRate()).isEqualTo(0.25);
    }

    @Test
    void shouldHandleZeroSpotsForParkingDocument() {
        LocalDateTime timestamp = LocalDateTime.now();

        DataPoint result = mapper.toDataPoint(parkingDocument(0, 10, timestamp));

        assertThat(result.available()).isEqualTo(0);
        assertThat(result.occupancyRate()).isEqualTo(0.0);
    }

    @Test
    void shouldNotReturnNegativeAvailableForParkingDocument() {
        LocalDateTime timestamp = LocalDateTime.now();

        DataPoint result = mapper.toDataPoint(parkingDocument(50, 75, timestamp));

        assertThat(result.available()).isEqualTo(0);
    }

    @Test
    void shouldMapHourlyOccupancyAggregate() {
        LocalDateTime timestamp = LocalDateTime.now();

        HourlyOccupancyAggregate aggregate = new HourlyOccupancyAggregate(timestamp, 25, 100);

        DataPoint result = mapper.toDataPoint(aggregate);

        assertThat(result.timestamp()).isEqualTo(timestamp);
        assertThat(result.occupancy()).isEqualTo(25);
        assertThat(result.available()).isEqualTo(75);
        assertThat(result.occupancyRate()).isEqualTo(0.25);
    }

    @Test
    void shouldMapDailySummaryAggregate() {
        LocalDateTime timestamp = LocalDateTime.now();

        DailySummaryAggregate aggregate = new DailySummaryAggregate(timestamp, 100, 42.4, 80, 10);

        DataPoint result = mapper.toDataPoint(aggregate);

        assertThat(result.timestamp()).isEqualTo(timestamp);
        assertThat(result.occupancy()).isEqualTo(42);
        assertThat(result.available()).isEqualTo(58);
        assertThat(result.occupancyRate()).isEqualTo(0.42);
    }

    @Test
    void shouldHandleZeroSpotsForAggregates() {
        LocalDateTime timestamp = LocalDateTime.now();

        HourlyOccupancyAggregate aggregate = new HourlyOccupancyAggregate(timestamp, 10, 0);

        DataPoint result = mapper.toDataPoint(aggregate);

        assertThat(result.available()).isEqualTo(0);
        assertThat(result.occupancyRate()).isEqualTo(0.0);
    }
}
