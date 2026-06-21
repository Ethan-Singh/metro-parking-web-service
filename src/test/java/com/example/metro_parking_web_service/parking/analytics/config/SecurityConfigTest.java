/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired private MockMvc mockMvc;

    @Autowired ParkingSecurityProperties props;

    @Test
    void shouldAllowParkingEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/parking/test")).andExpect(status().isOk());
    }

    @Test
    void shouldAllowActuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void shouldDenyOtherEndpoints() throws Exception {
        mockMvc.perform(get("/blocked")).andExpect(status().isForbidden());
    }

    @Test
    void shouldApplyCorsConfiguration() throws Exception {
        mockMvc.perform(get("/api/v1/parking/test").header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    @Test
    void debug() {
        System.out.println(props.allowedOrigins());
    }
}

@RestController
@Profile("test")
class SecurityConfigTestController {

    @GetMapping("/api/v1/parking/test")
    public String parking() {
        return "ok";
    }

    @GetMapping("/actuator/health")
    public String health() {
        return "ok";
    }

    @GetMapping("/blocked")
    public String blocked() {
        return "ok";
    }
}
