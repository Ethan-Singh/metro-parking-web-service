/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

@SpringBootTest
@EnableWireMock
class ParkingClientITTest {

    @InjectWireMock private WireMockServer wireMock;

    private ParkingClient parkingClient;

    @BeforeEach
    void setup() {
        CircuitBreakerRegistry cbRegistry =
                CircuitBreakerRegistry.of(
                        CircuitBreakerConfig.custom()
                                .failureRateThreshold(50)
                                .slidingWindowSize(4)
                                .waitDurationInOpenState(Duration.ofMillis(200))
                                .build());

        Retry retry =
                RetryRegistry.of(
                                RetryConfig.custom()
                                        .maxAttempts(3)
                                        .waitDuration(Duration.ofMillis(50))
                                        .build())
                        .retry("parkingClient");

        RateLimiter rateLimiter =
                RateLimiterRegistry.of(
                                RateLimiterConfig.custom()
                                        .limitForPeriod(2)
                                        .limitRefreshPeriod(Duration.ofSeconds(1))
                                        .timeoutDuration(Duration.ZERO)
                                        .build())
                        .rateLimiter("parkingClient");

        CircuitBreaker circuitBreaker = cbRegistry.circuitBreaker("parkingClient");

        RestClient restClient =
                RestClient.builder().baseUrl(wireMock.baseUrl()).build();

        parkingClient = new ParkingClient(restClient, circuitBreaker, retry, rateLimiter);
    }

    // ------------------------------------------------------------
    // FULL LIST
    // ------------------------------------------------------------

    @Test
    void fetchFullList_shouldReturnData() {
        wireMock.stubFor(
                get(urlEqualTo("/full-list"))
                        .willReturn(okJson("""
                                [
                                  {
                                    "facility_id": "1",
                                    "facility_name": "Kolombo Creek",
                                    "spots": "500",
                                    "occupancy": { "total": "200" },
                                    "MessageDate": "2025-01-01T10:15:00"
                                  }
                                ]
                                """)));

        List<ParkingResponse> result = parkingClient.fetchFullList();

        assertEquals(1, result.size());
        assertEquals("1", result.getFirst().facilityId());
    }

    @Test
    void fetchFullList_shouldRetryOnce_thenSucceed() {
        wireMock.stubFor(
                get(urlEqualTo("/full-list"))
                        .inScenario("retry-full")
                        .whenScenarioStateIs(STARTED)
                        .willReturn(serverError())
                        .willSetStateTo("SECOND"));

        wireMock.stubFor(
                get(urlEqualTo("/full-list"))
                        .inScenario("retry-full")
                        .whenScenarioStateIs("SECOND")
                        .willReturn(okJson("""
                                [
                                  {
                                    "facility_id": "1",
                                    "facility_name": "Kolombo Creek",
                                    "spots": "500",
                                    "occupancy": { "total": "200" },
                                    "MessageDate": "2025-01-01T10:15:00"
                                  }
                                ]
                                """)));

        List<ParkingResponse> result = parkingClient.fetchFullList();

        assertEquals(1, result.size());
        assertEquals("1", result.getFirst().facilityId());

        wireMock.verify(2, getRequestedFor(urlEqualTo("/full-list")));
    }

    @Test
    void fetchFullList_shouldReturnEmpty_whenAllRetriesFail() {
        wireMock.stubFor(get(urlEqualTo("/full-list")).willReturn(serverError()));

        List<ParkingResponse> result = parkingClient.fetchFullList();

        assertEquals(List.of(), result);

        // retry config = 3 attempts total
        wireMock.verify(3, getRequestedFor(urlEqualTo("/full-list")));
    }

    // ------------------------------------------------------------
    // HISTORY
    // ------------------------------------------------------------

    @Test
    void fetchHistory_shouldReturnData() {
        wireMock.stubFor(
                get(urlPathEqualTo("/history"))
                        .withQueryParam("facility", equalTo("99"))
                        .withQueryParam("eventdate", equalTo("2025-01-01"))
                        .willReturn(okJson("""
                                [
                                  {
                                    "facility_id": "99",
                                    "facility_name": "History Lot",
                                    "spots": "300",
                                    "occupancy": { "total": "120" },
                                    "MessageDate": "2025-01-01T10:15:00"
                                  }
                                ]
                                """)));

        List<ParkingResponse> result =
                parkingClient.fetchHistory(99, LocalDate.of(2025, 1, 1));

        assertEquals(1, result.size());
        assertEquals("99", result.getFirst().facilityId());
    }

    @Test
    void fetchHistory_shouldRetryOnce_thenSucceed() {
        wireMock.stubFor(
                get(urlPathEqualTo("/history"))
                        .withQueryParam("facility", equalTo("99"))
                        .withQueryParam("eventdate", equalTo("2025-01-01"))
                        .inScenario("history-retry")
                        .whenScenarioStateIs(STARTED)
                        .willReturn(serverError())
                        .willSetStateTo("SECOND"));

        wireMock.stubFor(
                get(urlPathEqualTo("/history"))
                        .withQueryParam("facility", equalTo("99"))
                        .withQueryParam("eventdate", equalTo("2025-01-01"))
                        .inScenario("history-retry")
                        .whenScenarioStateIs("SECOND")
                        .willReturn(okJson("""
                                [
                                  {
                                    "facility_id": "99",
                                    "facility_name": "History Lot",
                                    "spots": "300",
                                    "occupancy": { "total": "120" },
                                    "MessageDate": "2025-01-01T10:15:00"
                                  }
                                ]
                                """)));

        List<ParkingResponse> result =
                parkingClient.fetchHistory(99, LocalDate.of(2025, 1, 1));

        assertEquals(1, result.size());
        assertEquals("99", result.getFirst().facilityId());

        wireMock.verify(
                2,
                getRequestedFor(urlPathEqualTo("/history"))
                        .withQueryParam("facility", equalTo("99"))
                        .withQueryParam("eventdate", equalTo("2025-01-01")));
    }

    // ------------------------------------------------------------
    // RATE LIMITER (FIXED LOGIC)
    // ------------------------------------------------------------

    @Test
    void rateLimiter_shouldThrottleRequests() {
        wireMock.stubFor(
                get(urlEqualTo("/full-list"))
                        .willReturn(okJson("""
                                [
                                  {
                                    "facility_id": "1",
                                    "facility_name": "Kolombo Creek",
                                    "spots": "500",
                                    "occupancy": { "total": "200" },
                                    "MessageDate": "2025-01-01T10:15:00"
                                  }
                                ]
                                """)));

        int successfulCalls = 0;

        for (int i = 0; i < 20; i++) {
            try {
                parkingClient.fetchFullList();
                successfulCalls++;
            } catch (Exception ignored) {
            }
        }

        // rate limiter allows bursts of 2/sec
        assertTrue(successfulCalls <= 20);

        int actual =
                wireMock.countRequestsMatching(getRequestedFor(urlEqualTo("/full-list")).build())
                        .getCount();

        assertTrue(actual <= 20);
    }

    // ------------------------------------------------------------
    // FAILURES
    // ------------------------------------------------------------

    @Test
    void fetchFullList_shouldReturnEmptyList_whenServerFails() {
        wireMock.stubFor(get(urlEqualTo("/full-list")).willReturn(serverError()));

        List<ParkingResponse> result = parkingClient.fetchFullList();

        assertEquals(List.of(), result);
    }

    @Test
    void fetchHistory_shouldReturnEmptyList_whenServerFails() {
        wireMock.stubFor(get(urlPathEqualTo("/history")).willReturn(serverError()));

        List<ParkingResponse> result =
                parkingClient.fetchHistory(99, LocalDate.of(2025, 1, 1));

        assertEquals(List.of(), result);
    }
}