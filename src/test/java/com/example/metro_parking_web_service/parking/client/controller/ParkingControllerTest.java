/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.metro_parking_web_service.parking.analytics.config.ParkingSecurityProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ParkingController.class)
@ActiveProfiles("test")
class ParkingControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ParkingSecurityProperties securityProperties;

    @BeforeEach
    void setup() {
        when(securityProperties.getAllowedOrigins()).thenReturn(List.of("http://localhost:3000"));
    }

    @Test
    void parkingList_returnsHello() throws Exception {

        mockMvc.perform(get("/parking/list"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello"));
    }

    @Test
    void parkingHistory_returnsWorld() throws Exception {

        mockMvc.perform(get("/parking/history"))
                .andExpect(status().isOk())
                .andExpect(content().string("World"));
    }
}
