/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.mapper;

import com.example.metro_parking_web_service.parking.analytics.dto.DailySummaryAggregate;
import com.example.metro_parking_web_service.parking.analytics.dto.DataPoint;
import com.example.metro_parking_web_service.parking.analytics.dto.HourlyOccupancyAggregate;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ParkingDataPointMapper {

    public DataPoint toDataPoint(ParkingDocument document) {
        int available = Math.max(0, document.getSpots() - document.getOccupancy());
        double rate =
                document.getSpots() > 0
                        ? (double) document.getOccupancy() / document.getSpots()
                        : 0.0;
        return new DataPoint(
                document.getSourceTimestamp(), document.getOccupancy(), available, rate);
    }

    public DataPoint toDataPoint(HourlyOccupancyAggregate point) {
        int occupancy = (int) Math.round(point.occupancy());
        int available = Math.max(0, point.spots() - occupancy);
        double occupancyRate = point.spots() > 0 ? (double) occupancy / point.spots() : 0.0;

        return new DataPoint(point.timestamp(), occupancy, available, occupancyRate);
    }

    public DataPoint toDataPoint(DailySummaryAggregate point) {
        int occupancy = (int) Math.round(point.avgOccupancy());
        int available = Math.max(0, point.spots() - occupancy);
        double occupancyRate = point.spots() > 0 ? (double) occupancy / point.spots() : 0.0;

        return new DataPoint(point.timestamp(), occupancy, available, occupancyRate);
    }
}
