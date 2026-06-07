/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.LocalDate;
import java.util.List;
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

    @CircuitBreaker(name = "parkingClient", fallbackMethod = "fetchFullListFallback")
    @Retry(name = "parkingClient")
    @RateLimiter(name = "parkingClient")
    public List<ParkingResponse> fetchFullList() {
        return restClient
                .get()
                .uri("/full-list")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @CircuitBreaker(name = "parkingClient", fallbackMethod = "fetchHistoryFallback")
    @Retry(name = "parkingClient")
    @RateLimiter(name = "parkingClient")
    public List<ParkingResponse> fetchHistory(int facilityId, LocalDate eventDate) {
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
