/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.client.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.client.document.ParkingBackfillCursorDocument;
import com.example.metro_parking_web_service.parking.client.document.ParkingBackfillDocument;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import com.example.metro_parking_web_service.parking.client.repository.ParkingBackfillCursorRepository;
import com.example.metro_parking_web_service.parking.client.repository.ParkingBackfillRepository;
import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import com.example.metro_parking_web_service.parking.server.service.ParkingClient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingBackfillService {

    private final ParkingPolicy parkingPolicy;
    private final ParkingClient parkingClient;
    private final ParkingIngestService parkingIngestService;
    private final ParkingBackfillRepository parkingBackfillRepository;
    private final ParkingBackfillCursorRepository parkingBackfillCursorRepository;
    private final ParkingSnapshot parkingSnapshot;
    private final MongoTemplate mongoTemplate;

    public void backfill() {
        List<Integer> facilityIds = parkingSnapshot.getFacilityIds();

        if (facilityIds == null || facilityIds.isEmpty()) {
            log.warn("event=backfill decision=skip reason=no_valid_facility_ids");
            return;
        }

        int facilityId = resolveNextFacility(facilityIds);
        log.debug(
                "event=backfill decision=process facilityId={} facilityCount={}",
                facilityId,
                facilityIds.size());

        try {
            backfillFacility(facilityId);
        } catch (Exception e) {
            log.error(
                    "event=backfill decision=failed facilityId={} reason=exception", facilityId, e);
        }

        advanceCursor(facilityId);
    }

    private int resolveNextFacility(List<Integer> facilityIds) {
        int lastId =
                parkingBackfillCursorRepository
                        .findById("cursor")
                        .map(ParkingBackfillCursorDocument::getLastFacilityId)
                        .orElse(-1);
        return facilityIds.stream()
                .filter(id -> id > lastId)
                .findFirst()
                .orElse(facilityIds.getFirst());
    }

    void backfillFacility(int facilityId) {
        ParkingBackfillDocument document = findOrCreateBackfillDocument(facilityId);

        if (backfillForward(facilityId, document)) return;
        backfillBackward(facilityId, document);
    }

    private void advanceCursor(int facilityId) {
        ParkingBackfillCursorDocument cursor =
                parkingBackfillCursorRepository
                        .findById("cursor")
                        .orElseGet(ParkingBackfillCursorDocument::new);
        cursor.setLastFacilityId(facilityId);
        cursor.setUpdatedAt(Instant.now());
        parkingBackfillCursorRepository.save(cursor);
    }

    private ParkingBackfillDocument findOrCreateBackfillDocument(int facilityId) {
        return parkingBackfillRepository
                .findById(facilityId)
                .orElseGet(
                        () -> {
                            ParkingBackfillDocument document = new ParkingBackfillDocument();
                            document.setFacilityId(facilityId);
                            document.setComplete(false);
                            document.setUpdatedAt(Instant.now());
                            return document;
                        });
    }

    private boolean backfillForward(int facilityId, ParkingBackfillDocument document) {
        LocalDate nextForward = nextForward(document);
        if (nextForward == null) return false;

        fetchHistoryAndIngest(facilityId, nextForward);
        document.setLastForwardDate(nextForward);
        saveBackfillDocument(document);
        log.info(
                "event=backfill_facility decision=forward facilityId={} date={}",
                facilityId,
                nextForward);
        return true;
    }

    private LocalDate nextForward(ParkingBackfillDocument document) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate lastForward = document.getLastForwardDate();

        if (lastForward == null) return yesterday;

        LocalDate candidate = lastForward.plusDays(1);
        return candidate.isAfter(yesterday) ? null : candidate;
    }

    private void backfillBackward(int facilityId, ParkingBackfillDocument document) {
        if (document.isComplete() || parkingPolicy.isOutsideBackfillWindow(document)) {
            log.debug(
                    "event=backfill_facility decision=skip facilityId={} reason={}",
                    facilityId,
                    document.isComplete() ? "complete" : "outside_window");
            return;
        }

        LocalDate nextBackwardDay = nextBackward(document);
        fetchHistoryAndIngest(facilityId, nextBackwardDay);
        document.setLastProcessedDate(nextBackwardDay);
        saveBackfillDocument(document);
        log.info(
                "event=backfill_facility decision=backward facilityId={} date={}",
                facilityId,
                nextBackwardDay);
    }

    private LocalDate nextBackward(ParkingBackfillDocument document) {
        return document.getLastProcessedDate() != null
                ? document.getLastProcessedDate().minusDays(1)
                : LocalDate.now();
    }

    private void fetchHistoryAndIngest(int facilityId, LocalDate date) {
        List<ParkingResponse> history = parkingClient.fetchHistory(facilityId, date);
        if (!history.isEmpty()) {
            parkingIngestService.ingest(history);
        }
    }

    private void saveBackfillDocument(ParkingBackfillDocument document) {
        document.setUpdatedAt(Instant.now());
        parkingBackfillRepository.save(document);
    }

    public void cleanup() {
        LocalDate cutoff = LocalDate.now().minusWeeks(parkingPolicy.getBackfillWindow());

        LocalDateTime cutoffTime = cutoff.atStartOfDay();

        Query query = new Query(Criteria.where("sourceTimestamp").lt(cutoffTime));

        long deleted = mongoTemplate.remove(query, ParkingDocument.class).getDeletedCount();

        log.info(
                "event=cleanup_parking_data decision=success cutoff={} deletedCount={}",
                cutoffTime,
                deleted);
    }
}
