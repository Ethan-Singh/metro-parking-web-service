/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ParkingController.class)
@ActiveProfiles("test")
class ParkingControllerTest {

    @Autowired private MockMvc mockMvc;

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
