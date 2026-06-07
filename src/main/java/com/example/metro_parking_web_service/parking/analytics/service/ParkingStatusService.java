/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.service;

import com.example.metro_parking_web_service.parking.analytics.config.ParkingAnalyticsProperties;
import com.example.metro_parking_web_service.parking.analytics.dto.AvailabilityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParkingStatusService {

    private final ParkingAnalyticsProperties analyticsProperties;

    public AvailabilityStatus resolveStatus(int available, int spots) {
        if (available < 1) return AvailabilityStatus.FULL;
        double availableRate = spots > 0 ? (double) available / spots : 0.0;
        if (availableRate < analyticsProperties.getAlmostFullThreshold()) {
            return AvailabilityStatus.ALMOST_FULL;
        }
        return AvailabilityStatus.AVAILABLE;
    }

    public String resolveStatusLabel(AvailabilityStatus status) {
        return switch (status) {
            case FULL -> "Full";
            case ALMOST_FULL -> "Almost Full";
            case AVAILABLE -> "Available";
        };
    }
}
