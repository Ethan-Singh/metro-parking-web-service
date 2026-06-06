/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.client.dto.Parking;
import com.example.metro_parking_web_service.parking.client.dto.ParkingResponseMapper;
import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ParkingSnapshot {

    private final ParkingClient parkingClient;
    private final ParkingResponseMapper parkingResponseMapper;

    @Getter private volatile List<Parking> parkingList = List.of();

    @Getter private volatile List<Integer> facilityIds = List.of();

    public void refresh() {
        List<ParkingResponse> responses =
                Objects.requireNonNullElse(parkingClient.fetchFullList(), List.of());

        if (responses.isEmpty()) {
            this.facilityIds = List.of();
            this.parkingList = List.of();
            return;
        }

        this.facilityIds =
                responses.stream()
                        .map(ParkingResponse::facilityId)
                        .filter(Objects::nonNull)
                        .map(Integer::parseInt)
                        .distinct()
                        .sorted()
                        .toList();

        this.parkingList =
                responses.stream()
                        .map(parkingResponseMapper::toParking)
                        .filter(Objects::nonNull)
                        .toList();
    }
}
