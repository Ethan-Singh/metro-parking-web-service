/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.client.document.ParkingBackfillDocument;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import com.example.metro_parking_web_service.parking.client.dto.Parking;
import com.example.metro_parking_web_service.parking.client.dto.ParkingDocumentMapper;
import com.example.metro_parking_web_service.parking.client.dto.ParkingIdStrategy;
import com.example.metro_parking_web_service.parking.client.dto.ParkingResponseMapper;
import com.example.metro_parking_web_service.parking.client.repository.ParkingBackfillRepository;
import com.example.metro_parking_web_service.parking.client.repository.ParkingRepository;
import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingService {

    private final RestClient parkingRestClient;
    private final ParkingResponseMapper parkingResponseMapper;
    private final ParkingDocumentMapper parkingDocumentMapper;
    private final ParkingRepository parkingRepository;
    private final ParkingBackfillRepository parkingBackfillRepository;
    private final ParkingIdStrategy parkingIdStrategy;

    @Value("#{'${external-server.parking.disabled-facilities}'.split(',')}")
    Set<Integer> DISABLED_FACILITIES;

    @Value("${external-server.parking.backfill-start-date}")
    LocalDate BACKFILL_START_DATE;

    public void parkingSyncAll() {
        saveAll(fetchActiveFullList());
    }

    public void parkingBackfillAll() {
        fetchActiveFullList().stream()
                .map(Parking::facilityId)
                .collect(Collectors.toSet())
                .forEach(facilityId -> {
                    backfillFacility(facilityId);
                    sleep();
                });
    }

    void backfillFacility(int facilityId) {
        ParkingBackfillDocument state = getOrCreateBackfillState(facilityId);
        if (state.isBackfillComplete() || isOutOfRange(state)) {
            return;
        }

        LocalDate day = determineNextDay(state);
        List<Parking> parkingList = fetchHistory(facilityId, day);
        if (parkingList.isEmpty()) {
            markBackfillComplete(state, day);
            log.info("Backfill complete for facility {} on {}", facilityId, day);
        } else {
            saveAll(parkingList);
            markProgress(state, day);
            log.info(
                    "Imported {} records for facility {} on {}",
                    parkingList.size(),
                    facilityId,
                    day);
        }
    }

    private ParkingBackfillDocument getOrCreateBackfillState(int facilityId) {
        return parkingBackfillRepository
                .findById(facilityId)
                .orElseGet(
                        () -> {
                            ParkingBackfillDocument state = new ParkingBackfillDocument();
                            state.setFacilityId(facilityId);
                            state.setBackfillComplete(false);
                            state.setUpdatedAt(Instant.now());
                            return parkingBackfillRepository.save(state);
                        });
    }

    private boolean isOutOfRange(ParkingBackfillDocument state) {
        LocalDate last = state.getLastProcessedDate();
        return last != null && !last.isAfter(BACKFILL_START_DATE);
    }

    private LocalDate determineNextDay(ParkingBackfillDocument state) {
        if (state.getLastProcessedDate() == null) {
            return LocalDate.now();
        }
        return state.getLastProcessedDate().minusDays(1);
    }

    private void markProgress(ParkingBackfillDocument state, LocalDate day) {
        state.setLastProcessedDate(day);
        state.setUpdatedAt(Instant.now());
        parkingBackfillRepository.save(state);
    }

    private void markBackfillComplete(ParkingBackfillDocument state, LocalDate day) {
        state.setBackfillComplete(true);
        state.setBackfillUntilDate(day);
        state.setUpdatedAt(Instant.now());
        parkingBackfillRepository.save(state);
    }

    private List<Parking> fetchActiveFullList() {
        return fetchFullList().stream()
                .map(parkingResponseMapper::toParking)
                .filter(p -> !DISABLED_FACILITIES.contains(p.facilityId()))
                .toList();
    }

    private void saveAll(List<Parking> parkingList) {
        List<ParkingDocument> documents =
                parkingList.stream().map(this::toParkingDocument).toList();
        parkingRepository.saveAll(documents);
    }

    private ParkingDocument toParkingDocument(Parking parking) {
        ParkingDocument doc = parkingDocumentMapper.toParkingDocument(parking);
        doc.setId(parkingIdStrategy.generateId(parking.facilityId(), parking.sourceTimestamp()));
        return doc;
    }

    private List<ParkingResponse> fetchFullList() {
        return parkingRestClient
                .get()
                .uri("/full-list")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    private List<Parking> fetchHistory(int facilityId, LocalDate eventDate) {
        List<ParkingResponse> responses =
                parkingRestClient
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

        assert responses != null;
        return responses.stream().map(parkingResponseMapper::toParking).toList();
    }

    private void sleep() {
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
