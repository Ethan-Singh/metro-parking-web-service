/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.client.document.ParkingBackfillCursorDocument;
import com.example.metro_parking_web_service.parking.client.document.ParkingBackfillDocument;
import com.example.metro_parking_web_service.parking.client.dto.ParkingResponseMapper;
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
    private final ParkingResponseMapper parkingResponseMapper;
    private final ParkingIngestService parkingIngestService;
    private final ParkingBackfillRepository parkingBackfillRepository;
    private final ParkingBackfillCursorRepository parkingBackfillCursorRepository;

    public void backfillNext(List<ParkingResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            log.warn("backfill.skip");
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
            return;
        }

        int facilityId = resolveNextFacilityId(facilityIds);
        log.info("backfill.tick facilityId={}", facilityId);

        try {
            backfillFacility(facilityId);
        } catch (Exception e) {
            log.error("backfill.failed facilityId={}", facilityId, e);
        }

        advanceCursor(facilityId);
    }

    void backfillFacility(int facilityId) {
        ParkingBackfillDocument state = findOrCreateBackfill(facilityId);

        log.debug(
                "backfill.state facilityId={} isComplete={} isOutsideWindow={}"
                        + " lastProcessedDate={}",
                facilityId,
                state.isComplete(),
                parkingPolicy.isOutsideBackfillWindow(state),
                state.getLastProcessedDate());

        if (state.isComplete()) {
            log.debug("backfill.skip.complete facilityId={}", facilityId);
            return;
        }

        if (parkingPolicy.isOutsideBackfillWindow(state)) {
            log.debug("backfill.skip.outsideWindow facilityId={}", facilityId);
            return;
        }

        LocalDate eventDate = calculateNextDay(state);
        log.info("backfill.fetch facilityId={} eventDate={}", facilityId, eventDate);

        List<ParkingResponse> history = parkingClient.fetchHistory(facilityId, eventDate);

        if (!history.isEmpty()) {
            parkingIngestService.ingest(history);
            log.info(
                    "backfill.processed facilityId={} date={} records={}",
                    facilityId,
                    eventDate,
                    history.size());
        } else {
            log.warn("backfill.empty facilityId={} eventDate={}", facilityId, eventDate);
        }

        state.setLastProcessedDate(eventDate);
        saveBackfillState(state);
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
