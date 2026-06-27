/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.mapper;

import com.example.metro_parking_web_service.parking.analytics.dto.DailyOccupancyAggregate;
import com.example.metro_parking_web_service.parking.analytics.dto.DataPoint;
import com.example.metro_parking_web_service.parking.analytics.dto.HourlyOccupancyAggregate;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ParkingDataPointMapper {

    public DataPoint toDataPoint(ParkingDocument document) {
        int spots = document.getSpots();
        double occupancy = document.getOccupancy();

        int available = Math.max(0, spots - (int) occupancy);
        double occupancyRate = spots > 0 ? Math.round((occupancy / spots) * 100.0) / 100.0 : 0.0;

        return new DataPoint(document.getSourceTimestamp(), occupancy, available, occupancyRate);
    }

    public DataPoint toDataPoint(HourlyOccupancyAggregate point) {
        int spots = point.spots();
        double occupancy = point.occupancy();

        int available = Math.max(0, spots - (int) occupancy);
        double occupancyRate = spots > 0 ? Math.round((occupancy / spots) * 100.0) / 100.0 : 0.0;

        return new DataPoint(point.timestamp(), occupancy, available, occupancyRate);
    }

    public DataPoint toDataPoint(DailyOccupancyAggregate point) {
        int spots = point.spots();
        double occupancy = point.avgOccupancy();

        int available = Math.max(0, spots - (int) occupancy);
        double occupancyRate = spots > 0 ? Math.round((occupancy / spots) * 100.0) / 100.0 : 0.0;

        return new DataPoint(point.timestamp(), occupancy, available, occupancyRate);
    }
}
