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
        log.info("sync.all.start");

        List<Parking> parkingList =
                parkingClient.fetchFullList().stream()
                        .map(parkingResponseMapper::toParking)
                        .filter(parkingPolicy::isParkingAllowed)
                        .toList();
        log.info("sync.all.fetched count={}", parkingList.size());

        if (parkingList.isEmpty()) {
            log.warn("sync.all.empty.skip");
            return;
        }

        saveParkings(parkingList);
        log.info("sync.all.complete count={}", parkingList.size());
    }

    public void backfillAll() {
        log.info("backfill.all.start");

        List<Integer> facilityIds =
                parkingClient.fetchFullList().stream()
                        .map(ParkingResponse::facilityId)
                        .map(Integer::parseInt)
                        .distinct()
                        .toList();
        log.info("backfill.all.fetched facilities={}", facilityIds.size());

        for (int facilityId : facilityIds) {
            log.info("backfill.all.processing facilityId={}", facilityId);

            try {
                backfillFacility(facilityId);
            } catch (Exception e) {
                log.error("backfill.failed facilityId={}", facilityId, e);
            }

            sleep();
        }

        log.info("backfill.all.complete facilities={}", facilityIds.size());
    }

    void backfillFacility(int facilityId) {
        ParkingBackfillDocument backfillDocument = findOrCreateBackfill(facilityId);
        boolean isOutsideWindow = parkingPolicy.isOutsideBackfillWindow(backfillDocument);
        log.info(
                "backfill.state facilityId={} isComplete={} isOutsideWindow={}"
                        + " lastProcessedDate={}",
                facilityId,
                backfillDocument.isComplete(),
                isOutsideWindow,
                backfillDocument.getLastProcessedDate());

        if (backfillDocument.isComplete()) {
            log.debug("backfill.skip.complete facilityId={}", facilityId);
            return;
        }

        if (isOutsideWindow) {
            log.debug("backfill.skip.isOutsideWindow facilityId={}", facilityId);
            return;
        }

        LocalDate eventDate = calculateNextDay(backfillDocument);
        log.info("backfill.fetch facilityId={} eventDate={}", facilityId, eventDate);

        List<Parking> parkingList =
                parkingClient.fetchHistory(facilityId, eventDate).stream()
                        .map(parkingResponseMapper::toParking)
                        .filter(parkingPolicy::isParkingAllowed)
                        .toList();
        if (parkingList.isEmpty()) {
            log.warn("backfill.empty facilityId={} eventDate={}", facilityId, eventDate);
            backfillDocument.setLastProcessedDate(eventDate);
            saveBackfill(backfillDocument);
            return;
        }

        saveParkings(parkingList);
        backfillDocument.setLastProcessedDate(eventDate);
        saveBackfill(backfillDocument);
        log.info(
                "backfill.progress facilityId={} date={} records={}",
                facilityId,
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

    private void saveBackfill(ParkingBackfillDocument backfillDocument) {
        backfillDocument.setUpdatedAt(Instant.now());

        parkingBackfillRepository.save(backfillDocument);
    }

    private ParkingBackfillDocument findOrCreateBackfill(int facilityId) {
        return parkingBackfillRepository
                .findById(facilityId)
                .orElseGet(
                        () -> {
                            ParkingBackfillDocument backfillDocument =
                                    new ParkingBackfillDocument();
                            backfillDocument.setFacilityId(facilityId);
                            backfillDocument.setComplete(false);
                            backfillDocument.setUpdatedAt(Instant.now());
                            return backfillDocument;
                        });
    }

    private LocalDate calculateNextDay(ParkingBackfillDocument backfillDocument) {
        if (backfillDocument.getLastProcessedDate() == null) {
            return LocalDate.now();
        }
        return backfillDocument.getLastProcessedDate().minusDays(1);
    }

    private void sleep() {
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
