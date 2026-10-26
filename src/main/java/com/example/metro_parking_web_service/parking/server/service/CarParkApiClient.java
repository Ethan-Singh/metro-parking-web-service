/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.server.service;

import com.example.metro_parking_web_service.parking.server.dto.CarParkApiRecord;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class CarParkApiClient {

    private final RestClient restClient;
    private final CircuitBreaker parkingCircuitBreaker;
    private final Retry parkingRetry;

    public Map<String, String> fetchFacilities() {
        Supplier<Map<String, String>> supplier =
                () ->
                        restClient
                                .get()
                                .uri("")
                                .retrieve()
                                .body(new ParameterizedTypeReference<>() {});

        return Decorators.ofSupplier(supplier)
                .withCircuitBreaker(parkingCircuitBreaker)
                .withFallback(List.of(Exception.class), t -> Map.of())
                .decorate()
                .get();
    }

    public List<CarParkApiRecord> fetchHistory(int facilityId, LocalDate eventDate) {
        Supplier<List<CarParkApiRecord>> supplier =
                () ->
                        restClient
                                .get()
                                .uri(
                                        uriBuilder ->
                                                uriBuilder
                                                        .path("/history")
                                                        .queryParam("facility", facilityId)
                                                        .queryParam("eventdate", eventDate)
                                                        .build())
                                .retrieve()
                                .body(new ParameterizedTypeReference<>() {});

        return Decorators.ofSupplier(supplier)
                .withCircuitBreaker(parkingCircuitBreaker)
                .decorate()
                .get();
    }
}
