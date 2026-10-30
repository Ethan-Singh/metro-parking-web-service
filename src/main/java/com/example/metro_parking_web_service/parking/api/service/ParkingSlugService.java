/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.api.service;

import com.example.metro_parking_web_service.parking.domain.FacilitySlug;
import com.example.metro_parking_web_service.parking.ingestion.service.ParkingFacilityCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParkingSlugService {

    private final ParkingFacilityCatalog parkingCatalog;

    public String toSlug(String facilityName) {
        return FacilitySlug.fromName(facilityName);
    }

    public int facilityIdFromSlug(String slug) {
        Integer facilityId = parkingCatalog.facilityIdForSlug(slug);
        if (facilityId == null) {
            throw new IllegalArgumentException("Unknown facility slug: " + slug);
        }
        return facilityId;
    }
}
