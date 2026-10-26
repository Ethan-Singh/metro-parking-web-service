/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.server.dto;

import com.example.metro_parking_web_service.parking.client.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.client.dto.Parking;
import com.example.metro_parking_web_service.parking.client.time.SydneyTimeMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

@Component
public class CarParkApiRecordMapper {

    private final SydneyTimeMapper sydneyTimeMapper;

    public CarParkApiRecordMapper(SydneyTimeMapper sydneyTimeMapper) {
        this.sydneyTimeMapper = sydneyTimeMapper;
    }

    public Parking toParking(CarParkApiRecord response) {
        if (response == null || response.facilityId() == null || response.messageDate() == null) {
            return null;
        }

        try {
            int facilityId = Integer.parseInt(response.facilityId());
            int capacity = Integer.parseInt(response.spots());
            int occupancy = Integer.parseInt(response.occupancy().total());
            Instant sourceTime = fromProviderSydneyTime(response.messageDate());
            LocalDate sydneyDate = sourceTime.atZone(ParkingPolicy.SYDNEY_ZONE).toLocalDate();
            return new Parking(
                    facilityId,
                    response.facilityName(),
                    capacity,
                    occupancy,
                    sourceTime,
                    sydneyDate,
                    sydneyTimeMapper.bucketIndex(sydneyDate, sourceTime));
        } catch (NullPointerException | NumberFormatException exception) {
            return null;
        }
    }

    private Instant fromProviderSydneyTime(LocalDateTime messageDate) {
        // The provider omits an offset. Java selects the earlier valid offset during a DST overlap.
        ZonedDateTime sydneyTime = messageDate.atZone(ParkingPolicy.SYDNEY_ZONE);
        return sydneyTime.toInstant();
    }
}
