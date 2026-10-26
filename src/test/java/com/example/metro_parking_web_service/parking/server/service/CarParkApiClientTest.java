/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CarParkApiClientTest {

    @Test
    void fetchHistoryCallsHistoryWithFacilityAndEventDate() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://example.test/carpark");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CarParkApiClient client =
                new CarParkApiClient(
                        builder.build(),
                        CircuitBreaker.ofDefaults("test-carpark"),
                        Retry.ofDefaults("test-carpark"));

        server.expect(
                        requestTo(
                                "http://example.test/carpark/history?facility=42&eventdate=2026-10-26"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.fetchHistory(42, LocalDate.of(2026, 10, 26))).isEmpty();

        server.verify();
    }
}
