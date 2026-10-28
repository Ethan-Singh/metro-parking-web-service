/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.opal.domain;

import java.time.Instant;
import java.util.Objects;

/** Fare-table result for one continuous same-mode section of a planned itinerary. */
public record PricedFareSection(
        FareMode mode,
        FarePeriod period,
        OpalFareProfile profile,
        Instant tapOnAt,
        Instant tapOffAt,
        int baseFareMinorUnits,
        int stationAccessFeeMinorUnits) {

    public PricedFareSection {
        Objects.requireNonNull(mode, "mode is required");
        Objects.requireNonNull(period, "period is required");
        Objects.requireNonNull(profile, "profile is required");
        Objects.requireNonNull(tapOnAt, "tapOnAt is required");
        Objects.requireNonNull(tapOffAt, "tapOffAt is required");
        if (tapOffAt.isBefore(tapOnAt))
            throw new IllegalArgumentException("tapOffAt precedes tapOnAt");
        if (baseFareMinorUnits < 0 || stationAccessFeeMinorUnits < 0) {
            throw new IllegalArgumentException("Fare amounts must be non-negative");
        }
    }
}
