/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("providers.tfnsw.parking.security")
public record SecurityProperties(List<String> allowedOrigins, RateLimit rateLimit) {
    public record RateLimit(int capacity, int refillTokens, int refillPeriodSeconds) {}
}
