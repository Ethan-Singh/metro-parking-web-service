/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class ParkingClient {

    private final RestClient restClient;
    private final CircuitBreaker parkingCircuitBreaker;
    private final Retry parkingRetry;
    private final RateLimiter parkingRateLimiter;

    public List<ParkingResponse> fetchFullList() {

        Supplier<List<ParkingResponse>> supplier =
                () ->
                        restClient
                                .get()
                                .uri("/full-list")
                                .retrieve()
                                .body(new ParameterizedTypeReference<>() {});

        return Decorators.ofSupplier(supplier)
                .withCircuitBreaker(parkingCircuitBreaker)
                .withRetry(parkingRetry)
                .withRateLimiter(parkingRateLimiter)
                .withFallback(List.of(Exception.class), t -> List.of())
                .decorate()
                .get();
    }

    public List<ParkingResponse> fetchHistory(int facilityId, LocalDate eventDate) {

        Supplier<List<ParkingResponse>> supplier =
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
                .withRetry(parkingRetry)
                .withRateLimiter(parkingRateLimiter)
                .withFallback(List.of(Exception.class), t -> List.of())
                .decorate()
                .get();
    }

    private List<ParkingResponse> fetchFullListFallback(Throwable t) {
        log.error("parkingClient.fetchFullList.fallback reason={}", t.getMessage());
        return List.of();
    }

    private List<ParkingResponse> fetchHistoryFallback(
            int facilityId, LocalDate eventDate, Throwable t) {
        log.error(
                "parkingClient.fetchHistory.fallback facilityId={} date={} reason={}",
                facilityId,
                eventDate,
                t.getMessage());
        return List.of();
    }
}
