/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("external-server.parking.security")
public record ParkingSecurityProperties(List<String> allowedOrigins, RateLimit rateLimit) {
    public record RateLimit(int capacity, int refillTokens, int refillPeriodSeconds) {}
}
