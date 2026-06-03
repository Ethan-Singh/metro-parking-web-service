/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.client.dto.Parking;
import com.example.metro_parking_web_service.parking.client.dto.ParkingApiMapper;
import com.example.metro_parking_web_service.parking.server.dto.ParkingHistoryRequest;
import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class ParkingService {

    private final RestClient parkingRestClient;
    private final ParkingApiMapper parkingApiMapper;

    public List<Parking> parkingList() {
        List<ParkingResponse> parkingResponses =
                parkingRestClient
                        .get()
                        .uri("/full-list")
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

        assert parkingResponses != null;

        return parkingResponses.stream().map(parkingApiMapper::toParking).toList();
    }

    public List<Parking> parkingHistory(ParkingHistoryRequest parkingHistoryRequest) {
        List<ParkingResponse> parkingResponses =
                parkingRestClient
                        .get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder
                                                .path("/history")
                                                .queryParam(
                                                        "facility",
                                                        parkingHistoryRequest.facilityId())
                                                .queryParam(
                                                        "eventdate",
                                                        parkingHistoryRequest.eventDate())
                                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});

        assert parkingResponses != null;

        return parkingResponses.stream().map(parkingApiMapper::toParking).toList();
    }
}
