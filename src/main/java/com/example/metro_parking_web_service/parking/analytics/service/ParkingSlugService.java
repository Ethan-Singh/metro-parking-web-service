/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.service;

import com.example.metro_parking_web_service.parking.client.repository.ParkingDayRepository;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingSlugService {

    private final ParkingDayRepository parkingDayRepository;
    private volatile Map<String, Integer> slugIndex = Map.of();

    public String toSlug(String facilityName) {
        if (facilityName == null) return "";
        return Normalizer.normalize(facilityName, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    public int facilityIdFromSlug(String slug) {
        Integer facilityId = slugIndex().get(slug);
        if (facilityId == null) {
            facilityId = refreshSlugIndex().get(slug);
        }
        if (facilityId == null)
            throw new IllegalArgumentException("Unknown facility slug: " + slug);
        return facilityId;
    }

    private Map<String, Integer> slugIndex() {
        Map<String, Integer> current = slugIndex;
        return current.isEmpty() ? refreshSlugIndex() : current;
    }

    private synchronized Map<String, Integer> refreshSlugIndex() {
        Map<String, Integer> refreshed = new HashMap<>();
        parkingDayRepository.findAll(Sort.by(Sort.Direction.DESC, "sydneyDate")).stream()
                .filter(day -> day.getFacilityName() != null)
                .forEach(
                        day ->
                                refreshed.putIfAbsent(
                                        toSlug(day.getFacilityName()), day.getFacilityId()));
        slugIndex = Map.copyOf(refreshed);
        return slugIndex;
    }
}
