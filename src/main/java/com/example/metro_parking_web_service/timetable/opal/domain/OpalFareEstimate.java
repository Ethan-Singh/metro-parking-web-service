/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.opal.domain;

import java.util.List;
import java.util.Objects;

public record OpalFareEstimate(
        int amountMinorUnits,
        int baseFareMinorUnits,
        int transferDiscountMinorUnits,
        int stationAccessFeeMinorUnits,
        OpalFareProfile profile,
        String fareDataRevision,
        String rulesRevision,
        List<String> limitations) {

    public OpalFareEstimate {
        if (amountMinorUnits < 0
                || baseFareMinorUnits < 0
                || transferDiscountMinorUnits < 0
                || stationAccessFeeMinorUnits < 0) {
            throw new IllegalArgumentException("Estimate amounts must be non-negative");
        }
        Objects.requireNonNull(profile, "profile is required");
        Objects.requireNonNull(fareDataRevision, "fareDataRevision is required");
        Objects.requireNonNull(rulesRevision, "rulesRevision is required");
        limitations = List.copyOf(limitations);
    }
}
