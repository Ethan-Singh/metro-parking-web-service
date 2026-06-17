/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import com.example.metro_parking_web_service.parking.server.service.ParkingClient;
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

    private final ParkingClient client;
    private final ParkingIngestService parkingIngestService;

    @Getter private volatile List<ParkingResponse> responses = List.of();

    public void refresh() {
        List<ParkingResponse> parkings = client.fetchFullList();
        if (parkings == null) {
            this.responses = List.of();
            log.warn(
                    "event=parking_snapshot_refresh decision=defaulted reason=null_response"
                            + " storedSize=0");
            return;
        }

        this.responses = parkings;
        log.info(
                "event=parking_snapshot_refresh decision=success storedSize={} sourceSize={}",
                parkings.size(),
                parkings.size());

        parkingIngestService.ingest(this.getResponses());
    }

    public List<Integer> getFacilityIds() {
        return responses.stream()
                .map(ParkingResponse::facilityId)
                .filter(Objects::nonNull)
                .map(Integer::parseInt)
                .distinct()
                .sorted()
                .toList();
    }
}
