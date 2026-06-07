/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import java.util.List;
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
        if (fetched == null) {
            this.responses = List.of();
            log.warn(
                    "event=parking_snapshot_refresh decision=defaulted reason=null_response"
                            + " storedSize=0");
            return;
        }

        this.responses = fetched;
        log.info(
                "event=parking_snapshot_refresh decision=success storedSize={} sourceSize={}",
                fetched.size(),
                fetched.size());
    }
}
