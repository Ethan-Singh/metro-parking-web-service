/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.config;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class TimetableProviderClientConfig {

    @Bean("timetableProviderRestClient")
    RestClient timetableProviderRestClient(TfnswConnectionProperties connection) {
        RestClient.Builder builder = RestClient.builder();
        if (connection.baseUrl() != null && !connection.baseUrl().isBlank()) {
            builder.baseUrl(connection.baseUrl());
        }
        if (connection.apikey() != null && !connection.apikey().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, connection.apikey());
        }
        return builder.build();
    }
}
