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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
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

    public void syncAll(List<Parking> parkingList) {
        log.info("sync.all.start");

        if (parkingList == null) {
            log.warn("sync.all.skip.null");
            return;
        }
        if (parkingList.isEmpty()) {
            log.warn("sync.all.skip.empty");
            return;
        }

        List<Parking> filtered =
                parkingList.stream().filter(parkingPolicy::isParkingAllowed).toList();

        if (filtered.isEmpty()) {
            log.warn("sync.all.filtered.empty originalSize={}", parkingList.size());
            return;
        }

        try {
            log.info(
                    "sync.all.processing originalSize={} filteredSize={}",
                    parkingList.size(),
                    filtered.size());
            saveParkings(filtered);

        } catch (Exception e) {
            log.error("sync.all.failed size={}", filtered.size(), e);
            throw e;
        }

        log.info("sync.all.complete count={}", filtered.size());
    }

    public void backfillAll(List<Integer> facilityIds) {
        log.info("backfill.all.start");

        for (int facilityId : facilityIds) {
            log.info("backfill.step.start facilityId={}", facilityId);

            try {
                ParkingBackfillDocument backfillDocument = findOrCreateBackfill(facilityId);

                if (backfillDocument.isComplete()) {
                    log.info("backfill.step.skip.complete facilityId={}", facilityId);
                    continue;
                }
                if (parkingPolicy.isOutsideBackfillWindow(backfillDocument)) {
                    log.info(
                            "backfill.step.skip.outsideWindow facilityId={} lastProcessedDate={}",
                            facilityId,
                            backfillDocument.getLastProcessedDate());
                    continue;
                }
                backfillFacility(facilityId);

            } catch (Exception e) {
                log.error("backfill.step.failed facilityId={}", facilityId, e);
            }

            log.info("backfill.step.complete");
            return;
        }

        log.info("backfill.all.complete facilities={}", facilityIds.size());
    }

    void backfillFacility(int facilityId) {
        ParkingBackfillDocument backfillDocument = findOrCreateBackfill(facilityId);
        boolean isOutsideWindow = parkingPolicy.isOutsideBackfillWindow(backfillDocument);
        log.debug(
                "backfill.step.state facilityId={} isComplete={} isOutsideWindow={}"
                        + " lastProcessedDate={}",
                facilityId,
                backfillDocument.isComplete(),
                isOutsideWindow,
                backfillDocument.getLastProcessedDate());

        if (backfillDocument.isComplete()) {
            log.debug("backfill.step.skip.isComplete facilityId={}", facilityId);
            return;
        }

        if (isOutsideWindow) {
            log.debug("backfill.step.skip.isOutsideWindow facilityId={}", facilityId);
            return;
        }

        LocalDate eventDate = calculateNextDay(backfillDocument);
        log.info("backfill.step.fetch facilityId={} eventDate={}", facilityId, eventDate);

        List<Parking> parkingList =
                parkingClient.fetchHistory(facilityId, eventDate).stream()
                        .map(parkingResponseMapper::toParking)
                        .filter(Objects::nonNull)
                        .filter(parkingPolicy::isParkingAllowed)
                        .toList();

        if (!parkingList.isEmpty()) {
            saveParkings(parkingList);
            log.info(
                    "backfill.step.processed facilityId={} date={} records={}",
                    facilityId,
                    eventDate,
                    parkingList.size());
        } else {
            log.warn("backfill.step.empty facilityId={} eventDate={}", facilityId, eventDate);
        }

        backfillDocument.setLastProcessedDate(eventDate);
        saveBackfill(backfillDocument);
    }

    private void saveParkings(List<Parking> parkingList) {
        List<ParkingDocument> parkingDocuments =
                parkingList.stream()
                        .map(
                                parking -> {
                                    ParkingDocument parkingDocument =
                                            parkingDocumentMapper.toParkingDocument(parking);

                                    if (parkingDocument == null) {
                                        log.warn(
                                                "parkingDocumentMapper.null facilityId={}"
                                                        + " sourceTimestamp={}",
                                                parking.facilityId(),
                                                parking.sourceTimestamp());
                                        return null;
                                    }

                                    parkingDocument.setId(
                                            parkingIdStrategy.generateId(
                                                    parking.facilityId(),
                                                    parking.sourceTimestamp()));

                                    return parkingDocument;
                                })
                        .filter(Objects::nonNull)
                        .toList();

        if (parkingDocuments.isEmpty()) {
            log.warn("saveParkings.noValidParkingDocuments inputSize={}", parkingList.size());
            return;
        }

        parkingRepository.saveAll(parkingDocuments);

        log.info(
                "saveParkings.saved inputSize={} savedSize={}",
                parkingList.size(),
                parkingDocuments.size());
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
}
