/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import com.example.metro_parking_web_service.parking.client.document.ParkingSyncDocument;
import com.example.metro_parking_web_service.parking.client.dto.ParkingDocumentMapper;
import com.example.metro_parking_web_service.parking.client.dto.ParkingResponseMapper;
import com.example.metro_parking_web_service.parking.client.repository.ParkingRepository;
import com.example.metro_parking_web_service.parking.client.repository.ParkingSyncRepository;
import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class ParkingService {

    private final RestClient parkingRestClient;
    private final ParkingResponseMapper parkingResponseMapper;
    private final ParkingDocumentMapper parkingDocumentMapper;
    private final ParkingRepository parkingRepository;
    private final ParkingSyncRepository parkingSyncRepository;

    public void sync() {
        List<ParkingResponse> fullList = fetchFullList();

        Set<String> facilityIds =
                fullList.stream().map(ParkingResponse::facilityId).collect(Collectors.toSet());

        for (String facilityId : facilityIds) {
            syncFacility(facilityId);
        }
    }

    private void syncFacility(String facilityId) {
        ParkingSyncDocument state =
                parkingSyncRepository
                        .findById(facilityId)
                        .orElseGet(() -> createInitialState(facilityId));

        if (state.isBackfillInProgress()) {
            runBackfill(state);
            return;
        }

        if (shouldBackfill(state)) {
            runBackfill(state);
        } else {
            runLiveFetch(state);
        }
    }

    private ParkingSyncDocument createInitialState(String facilityId) {
        ParkingSyncDocument state = new ParkingSyncDocument();

        state.setFacilityId(facilityId);
        state.setBackfillComplete(false);
        state.setBackfillInProgress(false);
        state.setLastProcessedDate(LocalDate.now());
        state.setUpdatedAt(Instant.now());

        return parkingSyncRepository.save(state);
    }

    private boolean shouldBackfill(ParkingSyncDocument state) {
        return !state.isBackfillComplete();
    }

    private void runBackfill(ParkingSyncDocument state) {
        state.setBackfillInProgress(true);
        parkingSyncRepository.save(state);

        try {
            LocalDate lastProcessedDate =
                    state.getLastProcessedDate() != null
                            ? state.getLastProcessedDate()
                            : LocalDate.now();

            LocalDate day = lastProcessedDate.minusDays(1);

            while (true) {

                sleep5Seconds();

                List<ParkingResponse> responses = fetchHistory(state.getFacilityId(), day);

                if (responses.isEmpty()) {

                    state.setBackfillComplete(true);
                    state.setBackfillUntilDate(day);
                    state.setUpdatedAt(Instant.now());

                    parkingSyncRepository.save(state);
                    break;
                }

                ingest(responses);

                state.setLastProcessedDate(day);
                state.setUpdatedAt(Instant.now());

                parkingSyncRepository.save(state);

                day = day.minusDays(1);
            }

        } finally {
            state.setBackfillInProgress(false);
            state.setUpdatedAt(Instant.now());
            parkingSyncRepository.save(state);
        }
    }

    private void runLiveFetch(ParkingSyncDocument state) {
        if (state.isBackfillInProgress()) {
            return;
        }

        sleep5Seconds();

        List<ParkingResponse> responses = fetchFullListForFacility(state.getFacilityId());

        ingest(responses);
    }

    private List<ParkingResponse> fetchFullList() {
        return parkingRestClient
                .get()
                .uri("/full-list")
                .retrieve()
                .body(new ParameterizedTypeReference<List<ParkingResponse>>() {});
    }

    private List<ParkingResponse> fetchFullListForFacility(String facilityId) {
        return parkingRestClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/full-list")
                                        .queryParam("facility", facilityId)
                                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<ParkingResponse>>() {});
    }

    private List<ParkingResponse> fetchHistory(String facilityId, LocalDate eventDate) {
        return parkingRestClient
                .get()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/history")
                                        .queryParam("facility", facilityId)
                                        .queryParam("eventdate", eventDate)
                                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<ParkingResponse>>() {});
    }

    private void ingest(List<ParkingResponse> responses) {
        List<ParkingDocument> documents =
                responses.stream()
                        .map(parkingResponseMapper::toParking)
                        .map(parkingDocumentMapper::toParkingDocument)
                        .toList();

        parkingRepository.saveAll(documents);
    }

    private void sleep5Seconds() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
