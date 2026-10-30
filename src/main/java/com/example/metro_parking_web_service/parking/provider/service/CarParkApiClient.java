/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.provider.service;

import com.example.metro_parking_web_service.parking.provider.dto.CarParkApiRecord;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
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

    private static final long MINIMUM_REQUEST_INTERVAL_NANOS = 334_000_000L;

    private final RestClient restClient;
    private final CircuitBreaker parkingCircuitBreaker;
    private final Retry parkingRetry;
    private long nextProviderRequestNanos;

    public Map<String, String> fetchFacilities() {
        Supplier<Map<String, String>> supplier =
                () -> {
                    awaitProviderRequestSlot();
                    return restClient
                            .get()
                            .uri("")
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});
                };

        return Decorators.ofSupplier(supplier)
                .withCircuitBreaker(parkingCircuitBreaker)
                .withFallback(List.of(Exception.class), t -> Map.of())
                .decorate()
                .get();
    }

    public List<CarParkApiRecord> fetchHistory(int facilityId, LocalDate eventDate) {
        Supplier<List<CarParkApiRecord>> supplier =
                () -> {
                    awaitProviderRequestSlot();
                    return restClient
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
                };

        return Decorators.ofSupplier(supplier)
                .withCircuitBreaker(parkingCircuitBreaker)
                .withRetry(parkingRetry)
                .decorate()
                .get();
    }

    public CarParkApiRecord fetchLive(int facilityId) {
        Supplier<CarParkApiRecord> supplier =
                () -> {
                    awaitProviderRequestSlot();
                    return restClient
                            .get()
                            .uri(
                                    uriBuilder ->
                                            uriBuilder
                                                    .path("")
                                                    .queryParam("facility", facilityId)
                                                    .build())
                            .retrieve()
                            .body(CarParkApiRecord.class);
                };

        return Decorators.ofSupplier(supplier)
                .withCircuitBreaker(parkingCircuitBreaker)
                .withRetry(parkingRetry)
                .decorate()
                .get();
    }

    private void awaitProviderRequestSlot() {
        synchronized (this) {
            long waitNanos = nextProviderRequestNanos - System.nanoTime();
            if (waitNanos > 0) LockSupport.parkNanos(waitNanos);
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Interrupted while pacing TfNSW parking request");
            }
            nextProviderRequestNanos = System.nanoTime() + MINIMUM_REQUEST_INTERVAL_NANOS;
        }
    }
}
