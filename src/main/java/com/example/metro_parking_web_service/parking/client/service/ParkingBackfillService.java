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

    public void backfillNext(List<Integer> facilityIds) {
        if (facilityIds == null || facilityIds.isEmpty()) {
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

    void backfillFacility(int facilityId) {
        ParkingBackfillDocument state = findOrCreateBackfill(facilityId);

        if (fillForward(facilityId, state)) return;
        fillBackward(facilityId, state);
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

    private boolean fillForward(int facilityId, ParkingBackfillDocument state) {
        LocalDate nextForwardDay = nextForwardDay(state);
        if (nextForwardDay == null) return false;

        fetchAndSave(facilityId, nextForwardDay);
        state.setLastForwardDate(nextForwardDay);
        saveBackfillState(state);
        log.info(
                "event=backfill_facility decision=forward facilityId={} date={}",
                facilityId,
                nextForwardDay);
        return true;
    }

    private LocalDate nextForwardDay(ParkingBackfillDocument state) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate lastForward = state.getLastForwardDate();

        if (lastForward == null) return yesterday;

        LocalDate candidate = lastForward.plusDays(1);
        return candidate.isAfter(yesterday) ? null : candidate;
    }

    private void fillBackward(int facilityId, ParkingBackfillDocument state) {
        if (state.isComplete() || parkingPolicy.isOutsideBackfillWindow(state)) {
            log.debug(
                    "event=backfill_facility decision=skip facilityId={} reason={}",
                    facilityId,
                    state.isComplete() ? "complete" : "outside_window");
            return;
        }

        LocalDate nextBackwardDay = nextBackwardDay(state);
        fetchAndSave(facilityId, nextBackwardDay);
        state.setLastProcessedDate(nextBackwardDay);
        saveBackfillState(state);
        log.info(
                "event=backfill_facility decision=backward facilityId={} date={}",
                facilityId,
                nextBackwardDay);
    }

    private LocalDate nextBackwardDay(ParkingBackfillDocument state) {
        return state.getLastProcessedDate() != null
                ? state.getLastProcessedDate().minusDays(1)
                : LocalDate.now();
    }

    private void fetchAndSave(int facilityId, LocalDate date) {
        List<ParkingResponse> history = parkingClient.fetchHistory(facilityId, date);
        if (!history.isEmpty()) {
            parkingIngestService.ingest(history);
        }
    }

    private void saveBackfillState(ParkingBackfillDocument state) {
        state.setUpdatedAt(Instant.now());
        parkingBackfillRepository.save(state);
    }
}
