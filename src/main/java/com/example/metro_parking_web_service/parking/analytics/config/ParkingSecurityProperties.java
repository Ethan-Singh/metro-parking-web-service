/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("external-server.parking.security")
@RequiredArgsConstructor
@Data
public class ParkingSecurityProperties {

    private List<String> allowedOrigins = List.of();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class RateLimit {
        private int capacity = 30;
        private int refillTokens = 30;
        private int refillPeriodSeconds = 60;
    }
}
