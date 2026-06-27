/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.service;

import com.example.metro_parking_web_service.parking.analytics.config.ParkingAnalyticsProperties;
import com.example.metro_parking_web_service.parking.analytics.dto.Availability;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParkingStatusService {

    private final ParkingAnalyticsProperties analyticsProperties;

    public Availability resolveStatus(int available, int spots) {
        if (available < 1) return Availability.FULL;
        double availableRate = spots > 0 ? (double) available / spots : 0.0;
        if (availableRate < analyticsProperties.almostFullThreshold()) {
            return Availability.ALMOST_FULL;
        }
        return Availability.AVAILABLE;
    }
}
