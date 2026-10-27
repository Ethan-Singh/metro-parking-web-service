/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.domain;

import java.text.Normalizer;

public final class FacilitySlug {

    private FacilitySlug() {}

    public static String fromName(String facilityName) {
        if (facilityName == null) return "";
        return Normalizer.normalize(facilityName, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }
}
