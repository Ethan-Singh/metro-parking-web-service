/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResilienceConfig {

    @Bean
    public CircuitBreaker parkingCircuitBreaker() {
        return CircuitBreaker.ofDefaults("parkingClient");
    }

    @Bean
    public Retry parkingRetry() {
        return Retry.ofDefaults("parkingClient");
    }
}
