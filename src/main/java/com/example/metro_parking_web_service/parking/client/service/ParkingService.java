/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.server.dto.response.ParkingResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class ParkingService {

    private final RestClient parkingRestClient;

    public List<ParkingResponse> parkingList() {
        return parkingRestClient
                .get()
                .uri( "/full-list")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }
}
