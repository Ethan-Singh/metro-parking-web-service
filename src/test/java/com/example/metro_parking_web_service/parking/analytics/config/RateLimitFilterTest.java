/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.metro_parking_web_service.parking.analytics.controller.ParkingAnalyticsController;
import com.example.metro_parking_web_service.parking.analytics.service.ParkingAnalyticsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ParkingAnalyticsController.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RateLimitFilterTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ParkingAnalyticsService analyticsService;
    @MockitoBean private ParkingSecurityProperties securityProperties;

    @TestConfiguration
    static class Config {
        @Bean
        RateLimitFilter rateLimitFilter(ParkingSecurityProperties props) {
            return new RateLimitFilter(props);
        }
    }

    @Test
    void should_allow_requests_under_limit() throws Exception {

        var rateLimit = new ParkingSecurityProperties.RateLimit(10, 10, 60);
        when(securityProperties.rateLimit()).thenReturn(rateLimit);

        when(analyticsService.getAllOverviews()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/parking")).andExpect(status().isOk());
    }

    @Test
    void should_block_requests_when_rate_limited() throws Exception {

        var rateLimit = new ParkingSecurityProperties.RateLimit(1, 1, 60);
        when(securityProperties.rateLimit()).thenReturn(rateLimit);

        when(analyticsService.getAllOverviews()).thenReturn(List.of());

        // First request = allowed
        mockMvc.perform(get("/api/v1/parking")).andExpect(status().isOk());

        // Second request = blocked (same IP bucket)
        mockMvc.perform(get("/api/v1/parking"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().json("{\"error\":\"Too many requests\"}"));
    }
}
