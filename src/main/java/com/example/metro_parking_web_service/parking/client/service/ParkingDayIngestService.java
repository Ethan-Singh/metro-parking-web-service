/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.client.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.client.document.ParkingDay;
import com.example.metro_parking_web_service.parking.client.document.ParkingDay.ParkingSample;
import com.example.metro_parking_web_service.parking.client.dto.Parking;
import com.example.metro_parking_web_service.parking.client.repository.ParkingDayRepository;
import com.example.metro_parking_web_service.parking.server.dto.CarParkApiRecord;
import com.example.metro_parking_web_service.parking.server.dto.CarParkApiRecordMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingDayIngestService {

    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_BUCKETS_PER_DAY = 150;
    private static final int SAVE_ATTEMPTS = 5;

    private final ParkingPolicy parkingPolicy;
    private final CarParkApiRecordMapper parkingResponseMapper;
    private final ParkingDayRepository parkingDayRepository;

    public void ingest(List<CarParkApiRecord> responses) {
        if (responses == null || responses.isEmpty()) return;

        List<Parking> mapped = new ArrayList<>();
        for (CarParkApiRecord response : responses) {
            Parking parking = parkingResponseMapper.toParking(response);
            if (parking != null) mapped.add(parking);
        }
        ingestMapped(mapped);
    }

    public void ingestMapped(List<Parking> observations) {
        if (observations == null || observations.isEmpty()) return;
        Map<DayKey, List<Parking>> byDay = new HashMap<>();
        for (Parking parking : observations) {
            if (parking == null || !parkingPolicy.isParkingFacilityAllowed(parking)) continue;
            byDay.computeIfAbsent(
                            new DayKey(parking.facilityId(), parking.sydneyDate()),
                            key -> new ArrayList<>())
                    .add(parking);
        }

        byDay.forEach(this::mergeDay);
    }

    private void mergeDay(DayKey key, List<Parking> observations) {
        for (int attempt = 1; attempt <= SAVE_ATTEMPTS; attempt++) {
            try {
                String dateKey = DATE_KEY.format(key.sydneyDate());
                ParkingDay day =
                        parkingDayRepository
                                .findByFacilityIdAndSydneyDate(key.facilityId(), dateKey)
                                .orElseGet(() -> newDay(key, dateKey, observations.getFirst()));

                Map<Integer, ParkingSample> merged = new HashMap<>();
                if (day.getSamples() != null) {
                    day.getSamples().forEach(sample -> merged.put(sample.bucketIndex(), sample));
                }

                for (Parking observation : observations) {
                    int bucketIndex = observation.bucketIndex();
                    if (bucketIndex < 0 || bucketIndex >= MAX_BUCKETS_PER_DAY) {
                        log.warn(
                                "event=parking_ingest decision=skip reason=invalid_sydney_bucket"
                                        + " facilityId={} date={} bucketIndex={}",
                                key.facilityId(),
                                dateKey,
                                bucketIndex);
                        continue;
                    }

                    ParkingSample existing = merged.get(bucketIndex);
                    if (existing == null
                            || observation.sourceTimestamp().isAfter(existing.sourceTime())) {
                        merged.put(
                                bucketIndex,
                                new ParkingSample(
                                        bucketIndex,
                                        observation.sourceTimestamp(),
                                        observation.occupancy(),
                                        observation.spots()));
                    }
                }

                if (merged.size() > MAX_BUCKETS_PER_DAY) {
                    throw new IllegalStateException(
                            "Parking day exceeded the bounded sample count");
                }

                Parking newest =
                        observations.stream()
                                .max(Comparator.comparing(Parking::sourceTimestamp))
                                .orElseThrow();
                day.setFacilityName(newest.facilityName());
                day.setSamples(
                        merged.values().stream()
                                .sorted(Comparator.comparingInt(ParkingSample::bucketIndex))
                                .toList());
                parkingDayRepository.save(day);
                return;
            } catch (OptimisticLockingFailureException | DuplicateKeyException exception) {
                if (attempt == SAVE_ATTEMPTS) throw exception;
            }
        }
    }

    private ParkingDay newDay(DayKey key, String dateKey, Parking sample) {
        ParkingDay day = new ParkingDay();
        day.setId(key.facilityId() + "_" + dateKey);
        day.setFacilityId(key.facilityId());
        day.setFacilityName(sample.facilityName());
        day.setSydneyDate(dateKey);
        day.setSamples(new ArrayList<>());
        return day;
    }

    private record DayKey(int facilityId, LocalDate sydneyDate) {}
}
