/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.service;

import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import java.text.Normalizer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingSlugService {

    private final MongoTemplate mongoTemplate;

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
        if (slugIndex.containsKey(slug)) {
            return slugIndex.get(slug);
        }

        Query query = new Query().with(Sort.by("facilityId"));
        mongoTemplate
                .findDistinct(query, "facilityName", ParkingDocument.class, String.class)
                .forEach(
                        name -> {
                            Query q =
                                    new Query(
                                                    org.springframework.data.mongodb.core.query
                                                            .Criteria.where("facilityName")
                                                            .is(name))
                                            .with(Sort.by(Sort.Direction.DESC, "sourceTimestamp"))
                                            .limit(1);
                            ParkingDocument doc = mongoTemplate.findOne(q, ParkingDocument.class);
                            if (doc != null) {
                                slugIndex.put(toSlug(name), doc.getFacilityId());
                            }
                        });

        if (!slugIndex.containsKey(slug)) {
            throw new IllegalArgumentException("Unknown facility slug: " + slug);
        }

        return slugIndex.get(slug);
    }
}
