/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.client.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.client.document.ParkingDay;
import com.example.metro_parking_web_service.parking.client.document.ParkingDay.BackfillState;
import com.example.metro_parking_web_service.parking.client.repository.ParkingDayRepository;
import com.example.metro_parking_web_service.parking.server.dto.CarParkApiRecord;
import com.example.metro_parking_web_service.parking.server.service.CarParkApiClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingBackfillService {

    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_RETRY_SECONDS = 3600;
    private static final long LEASE_SECONDS = 300;
    private static final Duration BACKFILL_PLAN_MAX_AGE = Duration.ofMinutes(5);

    private final ParkingPolicy parkingPolicy;
    private final CarParkApiClient parkingClient;
    private final ParkingDayIngestService parkingIngestService;
    private final ParkingDayRepository parkingDayRepository;
    private final ParkingFacilityCatalog parkingSnapshot;
    private final AtomicInteger backfillRotation = new AtomicInteger();
    private final AtomicInteger workSlot = new AtomicInteger();
    private volatile BackfillPlan backfillPlan;

    /** Rotate between direct live snapshots and historical date backfill. */
    public void syncHistory() {
        if (Math.floorMod(workSlot.getAndIncrement(), 5) == 4) {
            backfillOne();
        } else {
            refreshLiveParking();
        }
    }

    private void refreshLiveParking() {
        Integer facilityId = parkingSnapshot.nextFacilityId();
        if (facilityId == null) {
            log.warn("event=live_parking_sync decision=skip reason=no_facilities");
            return;
        }

        try {
            CarParkApiRecord record = parkingClient.fetchLive(facilityId);
            parkingIngestService.ingest(record == null ? List.of() : List.of(record));
            log.debug(
                    "event=live_parking_sync decision=success facilityId={} messageDate={}",
                    facilityId,
                    record == null ? null : record.messageDate());
        } catch (Exception exception) {
            log.warn("event=live_parking_sync decision=retry facilityId={}", facilityId, exception);
        }
    }

    /** Backfill one older facility/date per run, newest date first. */
    private void backfillOne() {
        List<Integer> facilityIds = parkingSnapshot.getFacilityIds();
        if (facilityIds.isEmpty()) return;

        LocalDate today = LocalDate.now(ParkingPolicy.SYDNEY_ZONE);
        LocalDate earliest = parkingPolicy.getEarliestBackfillDate();
        Instant now = Instant.now();
        BackfillPlan plan = getBackfillPlan(facilityIds, today, earliest, now);
        int start = Math.floorMod(backfillRotation.getAndIncrement(), facilityIds.size());

        for (int offset = 0; offset < facilityIds.size(); offset++) {
            int facilityId = facilityIds.get((start + offset) % facilityIds.size());
            for (LocalDate date = today.minusDays(1);
                    !date.isBefore(earliest);
                    date = date.minusDays(1)) {
                WorkKey key = new WorkKey(facilityId, date);
                if (!plan.isEligible(key, now)) continue;
                ParkingDay claim = claim(facilityId, date);
                if (claim == null) {
                    backfillPlan = null;
                    return;
                }
                plan.update(
                        key,
                        new BackfillStatus(
                                BackfillState.PROCESSING, null, now.plusSeconds(LEASE_SECONDS)),
                        now);
                fetchAndSave(facilityId, date, plan);
                return;
            }
        }
    }

    private synchronized BackfillPlan getBackfillPlan(
            List<Integer> facilityIds, LocalDate today, LocalDate earliest, Instant now) {
        BackfillPlan current = backfillPlan;
        if (current != null
                && current.matches(facilityIds, today, earliest)
                && !current.isRefreshDue(now)) {
            return current;
        }

        Map<WorkKey, BackfillStatus> statuses = new HashMap<>();
        String from = DATE_KEY.format(earliest);
        String to = DATE_KEY.format(today.minusDays(1));
        if (!earliest.isAfter(today.minusDays(1))) {
            Set<Integer> allowedFacilityIds = Set.copyOf(facilityIds);
            parkingDayRepository
                    .findBackfillMetadataBetweenDates(from, to)
                    .forEach(
                            day -> {
                                if (allowedFacilityIds.contains(day.getFacilityId())) {
                                    LocalDate date = LocalDate.parse(day.getSydneyDate());
                                    statuses.put(
                                            new WorkKey(day.getFacilityId(), date),
                                            new BackfillStatus(
                                                    day.getBackfillState(),
                                                    day.getRetryAfter(),
                                                    day.getLeaseUntil()));
                                }
                            });
        }

        current =
                new BackfillPlan(
                        List.copyOf(facilityIds),
                        today,
                        earliest,
                        statuses,
                        now.plus(BACKFILL_PLAN_MAX_AGE));
        backfillPlan = current;
        return current;
    }

    private ParkingDay claim(int facilityId, LocalDate date) {
        Instant now = Instant.now();
        String dateKey = DATE_KEY.format(date);
        String id = facilityId + "_" + dateKey;

        for (int attempt = 0; attempt < 3; attempt++) {
            ParkingDay day = parkingDayRepository.findById(id).orElse(null);
            if (day == null) {
                day = new ParkingDay();
                day.setId(id);
                day.setFacilityId(facilityId);
                day.setFacilityName(parkingSnapshot.getFacilityName(facilityId));
                day.setSydneyDate(dateKey);
                day.setSamples(List.of());
            } else if (!isEligible(day, now)) {
                return null;
            }

            day.setBackfillState(BackfillState.PROCESSING);
            day.setLeaseUntil(now.plusSeconds(LEASE_SECONDS));
            try {
                return parkingDayRepository.save(day);
            } catch (DuplicateKeyException | OptimisticLockingFailureException exception) {
                // Another scheduler instance created or claimed the same unique facility/date.
            }
        }
        return null;
    }

    private boolean isEligible(ParkingDay day, Instant now) {
        return switch (day.getBackfillState()) {
            case null -> true;
            case COMPLETE -> false;
            case RETRY_WAIT -> day.getRetryAfter() == null || !day.getRetryAfter().isAfter(now);
            case PROCESSING -> day.getLeaseUntil() == null || !day.getLeaseUntil().isAfter(now);
        };
    }

    private void fetchAndSave(int facilityId, LocalDate date, BackfillPlan plan) {
        try {
            List<CarParkApiRecord> history = parkingClient.fetchHistory(facilityId, date);
            parkingIngestService.ingest(history);
            markComplete(facilityId, date, plan);
            log.info(
                    "event=parking_backfill decision=complete facilityId={} date={} recordCount={}",
                    facilityId,
                    date,
                    history == null ? 0 : history.size());
        } catch (Exception exception) {
            markRetry(facilityId, date, plan);
            log.warn(
                    "event=parking_backfill decision=retry facilityId={} date={}",
                    facilityId,
                    date,
                    exception);
        }
    }

    private void markComplete(int facilityId, LocalDate date, BackfillPlan plan) {
        parkingDayRepository
                .findById(facilityId + "_" + DATE_KEY.format(date))
                .ifPresent(
                        day -> {
                            day.setBackfillState(BackfillState.COMPLETE);
                            day.setCompletedAt(Instant.now());
                            day.setRetryAfter(null);
                            day.setLeaseUntil(null);
                            day.setRetryCount(0);
                            parkingDayRepository.save(day);
                            plan.update(
                                    new WorkKey(facilityId, date),
                                    new BackfillStatus(BackfillState.COMPLETE, null, null),
                                    Instant.now());
                        });
    }

    private void markRetry(int facilityId, LocalDate date, BackfillPlan plan) {
        parkingDayRepository
                .findById(facilityId + "_" + DATE_KEY.format(date))
                .ifPresent(
                        day -> {
                            int retryCount = day.getRetryCount() + 1;
                            long retrySeconds =
                                    Math.min(MAX_RETRY_SECONDS, 30L << Math.min(retryCount - 1, 7));
                            Instant retryAfter = Instant.now().plusSeconds(retrySeconds);
                            day.setBackfillState(BackfillState.RETRY_WAIT);
                            day.setRetryCount(retryCount);
                            day.setRetryAfter(retryAfter);
                            day.setLeaseUntil(null);
                            parkingDayRepository.save(day);
                            plan.update(
                                    new WorkKey(facilityId, date),
                                    new BackfillStatus(BackfillState.RETRY_WAIT, retryAfter, null),
                                    Instant.now());
                        });
    }

    private record WorkKey(int facilityId, LocalDate date) {}

    private record BackfillStatus(BackfillState state, Instant retryAfter, Instant leaseUntil) {}

    private static final class BackfillPlan {
        private final List<Integer> facilityIds;
        private final LocalDate today;
        private final LocalDate earliest;
        private final Map<WorkKey, BackfillStatus> statuses;
        private Instant refreshAt;

        private BackfillPlan(
                List<Integer> facilityIds,
                LocalDate today,
                LocalDate earliest,
                Map<WorkKey, BackfillStatus> statuses,
                Instant refreshAt) {
            this.facilityIds = facilityIds;
            this.today = today;
            this.earliest = earliest;
            this.statuses = statuses;
            this.refreshAt = refreshAt;
            statuses.values().stream()
                    .map(
                            status ->
                                    status.state() == BackfillState.RETRY_WAIT
                                            ? status.retryAfter()
                                            : status.state() == BackfillState.PROCESSING
                                                    ? status.leaseUntil()
                                                    : null)
                    .filter(nextCheck -> nextCheck != null && nextCheck.isAfter(Instant.now()))
                    .min(Instant::compareTo)
                    .ifPresent(nextCheck -> this.refreshAt = earlier(this.refreshAt, nextCheck));
        }

        private boolean matches(List<Integer> facilityIds, LocalDate today, LocalDate earliest) {
            return this.facilityIds.equals(facilityIds)
                    && this.today.equals(today)
                    && this.earliest.equals(earliest);
        }

        private boolean isRefreshDue(Instant now) {
            return !refreshAt.isAfter(now);
        }

        private boolean isEligible(WorkKey key, Instant now) {
            BackfillStatus status = statuses.get(key);
            if (status == null) return true;
            return switch (status.state()) {
                case null -> true;
                case COMPLETE -> false;
                case RETRY_WAIT -> status.retryAfter() == null || !status.retryAfter().isAfter(now);
                case PROCESSING -> status.leaseUntil() == null || !status.leaseUntil().isAfter(now);
            };
        }

        private void update(WorkKey key, BackfillStatus status, Instant now) {
            statuses.put(key, status);
            Instant nextCheck =
                    status.state() == BackfillState.RETRY_WAIT
                            ? status.retryAfter()
                            : status.state() == BackfillState.PROCESSING
                                    ? status.leaseUntil()
                                    : null;
            if (nextCheck != null && nextCheck.isAfter(now)) {
                refreshAt = earlier(refreshAt, nextCheck);
            }
        }

        private static Instant earlier(Instant first, Instant second) {
            return first.isBefore(second) ? first : second;
        }
    }

    public void cleanup() {
        String earliest = DATE_KEY.format(parkingPolicy.getEarliestBackfillDate());
        long deleted = parkingDayRepository.deleteBySydneyDateLessThan(earliest);
        log.info(
                "event=cleanup_parking_day decision=success earliestDate={} deletedCount={}",
                earliest,
                deleted);
    }
}
