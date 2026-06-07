/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ParkingSnapshot {

    private final ParkingClient parkingClient;

    @Getter private volatile List<ParkingResponse> responses = List.of();

    public void refresh() {
        List<ParkingResponse> fetched = parkingClient.fetchFullList();
        this.responses = Objects.requireNonNullElse(fetched, List.of());
        log.info("snapshot.refresh count={}", this.responses.size());
    }
}
