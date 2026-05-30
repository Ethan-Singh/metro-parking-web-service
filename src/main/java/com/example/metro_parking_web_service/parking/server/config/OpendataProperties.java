/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.server.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("external-server.carpark")
@Validated
@Data
@RequiredArgsConstructor
public class OpendataProperties {

    @NotBlank private String baseUrl;
    @NotBlank private String apikey;
}
