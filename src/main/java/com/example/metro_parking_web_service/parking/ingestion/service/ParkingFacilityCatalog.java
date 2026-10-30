/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.ingestion.service;

import com.example.metro_parking_web_service.parking.domain.FacilitySlug;
import com.example.metro_parking_web_service.parking.ingestion.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.provider.service.CarParkApiClient;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ParkingFacilityCatalog {

    private final CarParkApiClient client;
    private final ParkingPolicy parkingPolicy;
    private volatile CatalogSnapshot snapshot = new CatalogSnapshot(Map.of(), Map.of());

    public boolean refresh() {
        Map<String, String> upstream = client.fetchFacilities();
        if (upstream == null || upstream.isEmpty()) {
            log.warn("event=parking_facility_catalog_refresh decision=skip reason=empty_response");
            return false;
        }

        Map<Integer, String> next = new LinkedHashMap<>();
        upstream.entrySet().stream()
                .map(entry -> parseFacility(entry.getKey(), entry.getValue()))
                .filter(entry -> entry != null && parkingPolicy.isFacilityAllowed(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> next.put(entry.getKey(), entry.getValue()));

        if (!next.isEmpty()) {
            Map<String, Integer> slugIndex = new HashMap<>();
            next.forEach(
                    (facilityId, facilityName) ->
                            slugIndex.putIfAbsent(FacilitySlug.fromName(facilityName), facilityId));
            snapshot = new CatalogSnapshot(Map.copyOf(next), Map.copyOf(slugIndex));
            log.info(
                    "event=parking_facility_catalog_refresh decision=success facilityCount={}"
                            + " excludedCount={}",
                    next.size(),
                    upstream.size() - next.size());
            return true;
        }
        return false;
    }

    public List<Integer> getFacilityIds() {
        return snapshot.facilities().keySet().stream().sorted().toList();
    }

    public String getFacilityName(int facilityId) {
        return snapshot.facilities().get(facilityId);
    }

    public Map<Integer, String> getFacilities() {
        return snapshot.facilities();
    }

    public Integer facilityIdForSlug(String slug) {
        return snapshot.facilityIdsBySlug().get(slug);
    }

    private Map.Entry<Integer, String> parseFacility(String rawId, String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return Map.entry(Integer.parseInt(rawId), name);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record CatalogSnapshot(
            Map<Integer, String> facilities, Map<String, Integer> facilityIdsBySlug) {}
}
