/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("providers.tfnsw.realtime")
public record TfnswRealtimeProperties(Api api, boolean enabled, Duration refreshInterval) {

    public record Api(
            String alertsSydneyTrains,
            String alertsMetro,
            String tripUpdatesSydneyTrains,
            String tripUpdatesMetro,
            String vehiclePositionsSydneyTrains,
            String vehiclePositionsMetro) {}
}
