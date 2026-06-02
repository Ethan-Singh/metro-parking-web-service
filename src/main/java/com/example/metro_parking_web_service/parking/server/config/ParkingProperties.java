/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.server.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("external-server.parking")
@RequiredArgsConstructor
@Data
public class ParkingProperties {

    @NotBlank private final String baseUrl;
    @NotBlank private final String api;
    @NotBlank private final String apikey;
}
