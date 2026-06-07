/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.client.document.ParkingBackfillCursorDocument;
import com.example.metro_parking_web_service.parking.client.document.ParkingBackfillDocument;
import com.example.metro_parking_web_service.parking.client.repository.ParkingBackfillCursorRepository;
import com.example.metro_parking_web_service.parking.client.repository.ParkingBackfillRepository;
import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
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
public class ParkingBackfillService {

    private final ParkingPolicy parkingPolicy;
    private final ParkingClient parkingClient;
    private final ParkingIngestService parkingIngestService;
    private final ParkingBackfillRepository parkingBackfillRepository;
    private final ParkingBackfillCursorRepository parkingBackfillCursorRepository;

    public void backfillNext(List<ParkingResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            log.warn("event=backfill_next decision=skip reason=empty_responses");
            return;
        }

        List<Integer> facilityIds =
                responses.stream()
                        .map(ParkingResponse::facilityId)
                        .filter(Objects::nonNull)
                        .map(Integer::parseInt)
                        .distinct()
                        .sorted()
                        .toList();

        if (facilityIds.isEmpty()) {
            log.warn("event=backfill_next decision=skip reason=no_valid_facility_ids");
            return;
        }

        int facilityId = resolveNextFacilityId(facilityIds);
        log.info(
                "event=backfill_next decision=process facilityId={} facilityCount={}",
                facilityId,
                facilityIds.size());

        try {
            backfillFacility(facilityId);
        } catch (Exception e) {
            log.error(
                    "event=backfill_next decision=failed facilityId={} reason=exception",
                    facilityId,
                    e);
        }

        advanceCursor(facilityId);
    }

    void backfillFacility(int facilityId) {
        ParkingBackfillDocument document = findOrCreateBackfill(facilityId);
        log.debug(
                "event=backfill_facility_state facilityId={} complete={} outsideWindow={}"
                        + " lastProcessedDate={}",
                facilityId,
                document.isComplete(),
                parkingPolicy.isOutsideBackfillWindow(document),
                document.getLastProcessedDate());

        if (document.isComplete()) {
            log.info(
                    "event=backfill_facility decision=skip facilityId={} reason=complete",
                    facilityId);
            return;
        }

        if (parkingPolicy.isOutsideBackfillWindow(document)) {
            log.info(
                    "event=backfill_facility decision=skip facilityId={} reason=outside_window",
                    facilityId);
            return;
        }

        LocalDate eventDate = calculateNextDay(document);
        log.info("event=backfill_fetch facilityId={} eventDate={}", facilityId, eventDate);
        List<ParkingResponse> history = parkingClient.fetchHistory(facilityId, eventDate);

        if (!history.isEmpty()) {
            parkingIngestService.ingest(history);
            log.info("event=backfill_fetch facilityId={} eventDate={}", facilityId, eventDate);
        } else {
            log.warn(
                    "event=backfill_processed facilityId={} eventDate={} decision=no_data",
                    facilityId,
                    eventDate);
        }

        document.setLastProcessedDate(eventDate);
        saveBackfillState(document);
    }

    private int resolveNextFacilityId(List<Integer> facilityIds) {
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

    private void advanceCursor(int facilityId) {
        ParkingBackfillCursorDocument cursor =
                parkingBackfillCursorRepository
                        .findById("cursor")
                        .orElseGet(ParkingBackfillCursorDocument::new);
        cursor.setLastFacilityId(facilityId);
        cursor.setUpdatedAt(Instant.now());
        parkingBackfillCursorRepository.save(cursor);
    }

    private ParkingBackfillDocument findOrCreateBackfill(int facilityId) {
        return parkingBackfillRepository
                .findById(facilityId)
                .orElseGet(
                        () -> {
                            ParkingBackfillDocument state = new ParkingBackfillDocument();
                            state.setFacilityId(facilityId);
                            state.setComplete(false);
                            state.setUpdatedAt(Instant.now());
                            return state;
                        });
    }

    private void saveBackfillState(ParkingBackfillDocument state) {
        state.setUpdatedAt(Instant.now());
        parkingBackfillRepository.save(state);
    }

    private LocalDate calculateNextDay(ParkingBackfillDocument state) {
        if (state.getLastProcessedDate() == null) {
            return LocalDate.now();
        }
        return state.getLastProcessedDate().minusDays(1);
    }
}
