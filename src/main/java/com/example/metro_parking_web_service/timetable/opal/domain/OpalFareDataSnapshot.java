/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.opal.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Separately versioned normalized data extracted from the Opal fare workbook. */
public record OpalFareDataSnapshot(
        String revision,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String sourceChecksum,
        List<FareDistanceEntry> distances,
        List<FareTableEntry> fares) {

    public OpalFareDataSnapshot {
        Objects.requireNonNull(revision, "revision is required");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        Objects.requireNonNull(sourceChecksum, "sourceChecksum is required");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo precedes effectiveFrom");
        }
        distances = List.copyOf(distances);
        fares = List.copyOf(fares);
    }
}
