/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("external-server.parking")
public record ParkingProperties(String baseUrl, String api, String apikey) {}
