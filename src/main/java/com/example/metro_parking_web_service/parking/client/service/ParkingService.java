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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingService {

    private final ParkingPolicy parkingPolicy;
    private final ParkingClient parkingClient;
    private final ParkingResponseMapper parkingResponseMapper;
    private final ParkingDocumentMapper parkingDocumentMapper;
    private final ParkingIdStrategy parkingIdStrategy;
    private final ParkingRepository parkingRepository;
    private final ParkingBackfillRepository parkingBackfillRepository;

    public void syncAll() {
        List<Parking> parkingList =
                parkingClient.fetchFullList().stream()
                        .map(parkingResponseMapper::toParking)
                        .filter(parkingPolicy::isParkingAllowed)
                        .toList();
        if (parkingList.isEmpty()) {
            return;
        }

        saveParkings(parkingList);
    }

    public void backfillAll() {
        List<Integer> facilityIds =
                parkingClient.fetchFullList().stream()
                        .map(ParkingResponse::facilityId)
                        .map(Integer::parseInt)
                        .distinct()
                        .toList();
        log.info("backfill.all.start facilities={}", facilityIds.size());

        facilityIds.forEach(
                facilityId -> {
                    try {
                        backfillFacility(facilityId);
                    } catch (Exception e) {
                        log.error("backfill.failed facilityId={}", facilityId, e);
                    }

                    sleep();
                });

        log.info("backfill.all.complete");
    }

    void backfillFacility(int facilityId) {
        log.info("backfill.start facilityId={}", facilityId);

        ParkingBackfillDocument parkingBackfillDocument = findOrCreateBackfill(facilityId);
        if (parkingBackfillDocument.isBackfillComplete()
                || parkingPolicy.isBeforeBackfillWindow(parkingBackfillDocument)) {
            return;
        }

        LocalDate eventDate = calculateNextDay(parkingBackfillDocument);
        log.info("backfill.history facilityId={} eventDate={}", facilityId, eventDate);

        List<Parking> parkingList =
                parkingClient.fetchHistory(facilityId, eventDate).stream()
                        .map(parkingResponseMapper::toParking)
                        .filter(parkingPolicy::isParkingAllowed)
                        .toList();
        if (parkingList.isEmpty()) {
            parkingBackfillDocument.setBackfillComplete(true);
            parkingBackfillDocument.setBackfillUntilDate(eventDate);
            saveBackfill(parkingBackfillDocument);
            log.warn("history.empty facilityId={} date={}", facilityId, eventDate);
            return;
        }

        saveParkings(parkingList);
        parkingBackfillDocument.setLastProcessedDate(eventDate);
        saveBackfill(parkingBackfillDocument);
        log.info(
                "backfill.imported facilityId={} date={} records={}",
                parkingBackfillDocument.getFacilityId(),
                eventDate,
                parkingList.size());
    }

    private void saveParkings(List<Parking> parkingList) {
        List<ParkingDocument> parkingDocuments =
                parkingList.stream()
                        .map(
                                parking -> {
                                    ParkingDocument doc =
                                            parkingDocumentMapper.toParkingDocument(parking);
                                    doc.setId(
                                            parkingIdStrategy.generateId(
                                                    parking.facilityId(),
                                                    parking.sourceTimestamp()));
                                    return doc;
                                })
                        .toList();

        parkingRepository.saveAll(parkingDocuments);
    }

    private void saveBackfill(ParkingBackfillDocument parkingBackfillDocument) {
        parkingBackfillDocument.setUpdatedAt(Instant.now());

        parkingBackfillRepository.save(parkingBackfillDocument);
    }

    private ParkingBackfillDocument findOrCreateBackfill(int facilityId) {
        return parkingBackfillRepository
                .findById(facilityId)
                .orElseGet(
                        () -> {
                            ParkingBackfillDocument parkingBackfillDocument =
                                    new ParkingBackfillDocument();
                            parkingBackfillDocument.setFacilityId(facilityId);
                            parkingBackfillDocument.setBackfillComplete(false);
                            parkingBackfillDocument.setUpdatedAt(Instant.now());
                            return parkingBackfillDocument;
                        });
    }

    private LocalDate calculateNextDay(ParkingBackfillDocument parkingBackfillDocument) {
        if (parkingBackfillDocument.getLastProcessedDate() == null) {
            return LocalDate.now();
        }
        return parkingBackfillDocument.getLastProcessedDate().minusDays(1);
    }

    private void sleep() {
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
