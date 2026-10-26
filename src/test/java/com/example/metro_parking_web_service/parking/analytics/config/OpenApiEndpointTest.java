/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.metro_parking_web_service.parking.analytics.dto.DailyParkingMetricsPayload;
import com.example.metro_parking_web_service.parking.analytics.service.ParkingQueryService;
import com.example.metro_parking_web_service.parking.client.config.ParkingPolicy;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiEndpointTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ParkingQueryService parkingQueryService;

    @Test
    void servesGeneratedOpenApiDocumentForInteractiveTesting() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/parking/{slug}/history")))
                .andExpect(
                        content().string(containsString("/api/v1/parking/{slug}/metrics/daily")));
    }

    @Test
    void returnsDailyMetricsAndDoesNotCacheRangesContainingToday() throws Exception {
        LocalDate today = LocalDate.now(ParkingPolicy.SYDNEY_ZONE);
        LocalDate yesterday = today.minusDays(1);
        when(parkingQueryService.getDailyMetrics("central-station", yesterday, today))
                .thenReturn(
                        new DailyParkingMetricsPayload(
                                yesterday.toString(),
                                today.toString(),
                                "Australia/Sydney",
                                List.of(
                                        new DailyParkingMetricsPayload.DailyMetric(
                                                yesterday.toString(), 42, null, null, null, null),
                                        new DailyParkingMetricsPayload.DailyMetric(
                                                today.toString(), null, null, null, null, null))));

        mockMvc.perform(
                        get("/api/v1/parking/central-station/metrics/daily")
                                .queryParam("from", yesterday.toString())
                                .queryParam("to", today.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.timeZone").value("Australia/Sydney"))
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].expectedFreeAt7am").value(42))
                .andExpect(
                        jsonPath("$.days[0].expectedFreeAt8am")
                                .value(org.hamcrest.Matchers.nullValue()));
    }
}
