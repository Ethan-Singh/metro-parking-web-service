/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.metro_parking_web_service.parking.analytics.service.ParkingAnalyticsService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitFilterTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RateLimitFilter rateLimitFilter;

    @MockitoBean private ParkingSecurityProperties securityProperties;
    @MockitoBean private ParkingAnalyticsService analyticsService;

    @BeforeEach
    void setup() {
        rateLimitFilter.clearBuckets();
    }

    @Test
    void should_allow_requests_under_limit() throws Exception {

        when(securityProperties.rateLimit())
                .thenReturn(new ParkingSecurityProperties.RateLimit(10, 10, 60));

        when(analyticsService.getAllOverviews()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/parking")).andExpect(status().isOk());
    }

    @Test
    void should_block_requests_when_rate_limited() throws Exception {

        when(securityProperties.rateLimit())
                .thenReturn(new ParkingSecurityProperties.RateLimit(1, 1, 60));

        when(analyticsService.getAllOverviews()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/parking")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/parking"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().json("{\"error\":\"Too many requests\"}"));
    }
}
