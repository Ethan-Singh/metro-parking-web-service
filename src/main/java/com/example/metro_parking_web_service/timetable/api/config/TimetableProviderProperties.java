/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("providers.tfnsw.timetable")
public record TimetableProviderProperties(Api api, boolean enabled) {

    public record Api(String sydneyTrains, String metro, String replacementBus, String pcbc) {}
}
