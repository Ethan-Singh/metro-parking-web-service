/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("external-server.parking.analytics")
@RequiredArgsConstructor
@Data
public class ParkingAnalyticsProperties {

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double almostFullThreshold;

    @Min(1)
    private Integer predictionWeeks;

    @Min(1)
    private Integer historyWeeks;

    @Min(1)
    private Integer cachePredictionWeeks;
}
