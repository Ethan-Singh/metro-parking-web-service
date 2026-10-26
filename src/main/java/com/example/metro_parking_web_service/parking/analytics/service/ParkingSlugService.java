/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.service;

import com.example.metro_parking_web_service.parking.client.document.ParkingDay;
import com.example.metro_parking_web_service.parking.client.repository.ParkingDayRepository;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingSlugService {

    private final ParkingDayRepository parkingDayRepository;
    private final Map<String, Integer> slugIndex = new ConcurrentHashMap<>();

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
        if (slugIndex.isEmpty()) {
            parkingDayRepository.findAll(Sort.by(Sort.Direction.DESC, "sydneyDate")).stream()
                    .filter(day -> day.getFacilityName() != null)
                    .sorted(Comparator.comparing(ParkingDay::getSydneyDate).reversed())
                    .forEach(
                            day ->
                                    slugIndex.putIfAbsent(
                                            toSlug(day.getFacilityName()), day.getFacilityId()));
        }

        Integer facilityId = slugIndex.get(slug);
        if (facilityId == null)
            throw new IllegalArgumentException("Unknown facility slug: " + slug);
        return facilityId;
    }
}
