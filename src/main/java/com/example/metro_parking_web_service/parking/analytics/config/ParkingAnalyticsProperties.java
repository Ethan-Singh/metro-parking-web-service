/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("external-server.parking.analytics")
public record ParkingAnalyticsProperties(Double almostFullThreshold) {}
