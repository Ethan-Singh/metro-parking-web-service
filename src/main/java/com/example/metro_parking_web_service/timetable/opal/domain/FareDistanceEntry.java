/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.opal.domain;

import java.util.Objects;

/** Maps a normalized stop pair to the fare band used by the current Opal workbook. */
public record FareDistanceEntry(
        FareMode mode,
        String originStopId,
        String destinationStopId,
        String fareBandKey,
        int distanceMetres) {

    public FareDistanceEntry {
        Objects.requireNonNull(mode, "mode is required");
        Objects.requireNonNull(originStopId, "originStopId is required");
        Objects.requireNonNull(destinationStopId, "destinationStopId is required");
        Objects.requireNonNull(fareBandKey, "fareBandKey is required");
        if (distanceMetres < 0)
            throw new IllegalArgumentException("distanceMetres must be non-negative");
    }
}
