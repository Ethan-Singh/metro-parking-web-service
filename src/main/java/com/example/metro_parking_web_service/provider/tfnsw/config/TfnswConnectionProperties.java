/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.provider.tfnsw.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("providers.tfnsw")
public record TfnswConnectionProperties(String baseUrl, String apikey) {}
