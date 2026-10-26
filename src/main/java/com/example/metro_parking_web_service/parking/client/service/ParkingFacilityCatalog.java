/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.client.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.server.service.CarParkApiClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ParkingFacilityCatalog {

    private final CarParkApiClient client;
    private final ParkingPolicy parkingPolicy;
    private final AtomicInteger rotation = new AtomicInteger();

    @Getter private volatile Map<Integer, String> facilities = Map.of();

    public boolean refresh() {
        Map<String, String> upstream = client.fetchFacilities();
        if (upstream == null || upstream.isEmpty()) {
            log.warn("event=parking_facilities_refresh decision=skip reason=empty_response");
            return false;
        }

        Map<Integer, String> next = new LinkedHashMap<>();
        upstream.entrySet().stream()
                .map(entry -> parseFacility(entry.getKey(), entry.getValue()))
                .filter(entry -> entry != null && parkingPolicy.isFacilityAllowed(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> next.put(entry.getKey(), entry.getValue()));

        if (!next.isEmpty()) {
            facilities = Map.copyOf(next);
            log.info(
                    "event=parking_facilities_refresh decision=success facilityCount={}"
                            + " excludedCount={}",
                    next.size(),
                    upstream.size() - next.size());
            return true;
        }
        return false;
    }

    public List<Integer> getFacilityIds() {
        return facilities.keySet().stream().sorted().toList();
    }

    public String getFacilityName(int facilityId) {
        return facilities.get(facilityId);
    }

    public Integer nextFacilityId() {
        List<Integer> ids = getFacilityIds();
        if (ids.isEmpty()) return null;
        int index = Math.floorMod(rotation.getAndIncrement(), ids.size());
        return ids.get(index);
    }

    private Map.Entry<Integer, String> parseFacility(String rawId, String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return Map.entry(Integer.parseInt(rawId), name);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
