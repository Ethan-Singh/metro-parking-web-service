/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class ParkingClient {

    private final RestClient restClient;

    public List<ParkingResponse> fetchFullList() {
        return restClient
                .get()
                .uri("/full-list")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

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
}
