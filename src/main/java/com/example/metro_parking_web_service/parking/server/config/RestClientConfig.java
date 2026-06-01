/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean("parkingRestClient")
    RestClient parkingRestClient(ParkingProperties parkingProperties) {
        return RestClient.builder()
                .baseUrl(parkingProperties.getBaseUrl() + parkingProperties.getApi())
                .defaultHeader(HttpHeaders.AUTHORIZATION, parkingProperties.getApikey())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
