/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void shouldHandleIllegalArgumentException() throws Exception {
        mockMvc.perform(get("/api/v1/parking/illegal"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request parameters"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldHandleMissingServletRequestParameterException() throws Exception {
        mockMvc.perform(get("/api/v1/parking/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required parameter: value"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldHandleMethodArgumentTypeMismatchException() throws Exception {
        mockMvc.perform(get("/api/v1/parking/number").param("value", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid parameter format: value"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldHandleGenericException() throws Exception {
        mockMvc.perform(get("/api/v1/parking/generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}

@RestController
@Profile("test")
class GlobalExceptionHandlerTestController {
    @GetMapping("/api/v1/parking/illegal")
    public void illegal() {
        throw new IllegalArgumentException("bad input");
    }

    @GetMapping("/api/v1/parking/generic")
    public void generic() throws Exception {
        throw new Exception("boom");
    }

    @GetMapping("/api/v1/parking/required")
    public void required(@RequestParam String value) {}

    @GetMapping("/api/v1/parking/number")
    public void number(@RequestParam Integer value) {}
}
