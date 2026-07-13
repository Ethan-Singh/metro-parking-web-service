/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import com.example.metro_parking_web_service.parking.analytics.dto.ParkingHistoryResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, ParkingHistoryResponse> historyCache() {
        return Caffeine.newBuilder().maximumSize(100).expireAfterWrite(Duration.ofHours(1)).build();
    }
}
