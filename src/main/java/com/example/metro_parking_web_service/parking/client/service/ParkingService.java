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
    private Set<Integer> DISABLED_FACILITIES;

    private boolean isActiveFacility(Parking parking) {
        return !DISABLED_FACILITIES.contains(parking.facilityId());
    }

    private ParkingDocument toParkingDocument(Parking parking) {
        ParkingDocument parkingDocument = parkingDocumentMapper.toParkingDocument(parking);

        parkingDocument.setId(
                parkingIdStrategy.generateId(parking.facilityId(), parking.sourceTimestamp()));

        return parkingDocument;
    }

    private void parkingListSave(List<Parking> parkingList) {
        List<ParkingDocument> parkingDocumentList =
                parkingList.stream().map(this::toParkingDocument).toList();

        parkingRepository.saveAll(parkingDocumentList);
    }

    public void parkingSyncAll() {
        List<Parking> parkingList =
                getFullList().stream()
                        .map(parkingResponseMapper::toParking)
                        .filter(this::isActiveFacility)
                        .toList();

        parkingListSave(parkingList);
    }

    public void parkingBackfillAll() {
        getFullList().stream()
                .map(parkingResponseMapper::toParking)
                .filter(this::isActiveFacility)
                .map(Parking::facilityId)
                .collect(Collectors.toSet())
                .forEach(this::backfillFacility);
    }

    private void backfillFacility(int facilityId) {
        ParkingBackfillDocument parkingBackfillDocument =
                parkingBackfillRepository
                        .findById(facilityId)
                        .orElseGet(() -> createInitialParkingBackfillState(facilityId));

        if (parkingBackfillDocument.isBackfillComplete()
                && !parkingBackfillDocument.isBackfillInProgress()) {
            return;
        }

        parkingBackfillDocument.setBackfillInProgress(true);
        parkingBackfillRepository.save(parkingBackfillDocument);

        try {
            LocalDate lastProcessedDate =
                    parkingBackfillDocument.getLastProcessedDate() != null
                            ? parkingBackfillDocument.getLastProcessedDate()
                            : LocalDate.now();

            LocalDate day = lastProcessedDate.minusDays(1);

            while (true) {
                sleep5Seconds();

                List<ParkingResponse> parkingResponses = getHistory(facilityId, day);

                if (parkingResponses.isEmpty()) {
                    parkingBackfillDocument.setBackfillComplete(true);
                    parkingBackfillDocument.setBackfillUntilDate(day);
                    parkingBackfillDocument.setUpdatedAt(Instant.now());
                    parkingBackfillRepository.save(parkingBackfillDocument);
                    break;
                }

                List<Parking> parkingList =
                        parkingResponses.stream().map(parkingResponseMapper::toParking).toList();

                parkingListSave(parkingList);

                parkingBackfillDocument.setLastProcessedDate(day);
                parkingBackfillDocument.setUpdatedAt(Instant.now());
                parkingBackfillRepository.save(parkingBackfillDocument);

                day = day.minusDays(1);
            }

        } finally {
            parkingBackfillDocument.setBackfillInProgress(false);
            parkingBackfillDocument.setUpdatedAt(Instant.now());
            parkingBackfillRepository.save(parkingBackfillDocument);
        }
    }

    private ParkingBackfillDocument createInitialParkingBackfillState(int facilityId) {
        ParkingBackfillDocument parkingBackfillDocument = new ParkingBackfillDocument();

        parkingBackfillDocument.setFacilityId(facilityId);
        parkingBackfillDocument.setBackfillComplete(false);
        parkingBackfillDocument.setBackfillInProgress(false);
        parkingBackfillDocument.setLastProcessedDate(null);
        parkingBackfillDocument.setUpdatedAt(Instant.now());

        return parkingBackfillRepository.save(parkingBackfillDocument);
    }

    private List<ParkingResponse> getFullList() {
        return parkingRestClient
                .get()
                .uri("/full-list")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    private List<ParkingResponse> getHistory(int facilityId, LocalDate eventDate) {
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

    private void sleep5Seconds() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
