/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.provider.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("providers.tfnsw.parking")
public record CarParkApiProperties(String api) {}
