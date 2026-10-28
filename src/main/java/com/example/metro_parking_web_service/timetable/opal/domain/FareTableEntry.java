/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.opal.domain;

import java.util.Objects;

/** A normalized workbook fare value, in Australian cents. */
public record FareTableEntry(
        FareMode mode,
        String fareBandKey,
        FareCategory category,
        PaymentMethod paymentMethod,
        FarePeriod period,
        int amountMinorUnits) {

    public FareTableEntry {
        Objects.requireNonNull(mode, "mode is required");
        Objects.requireNonNull(fareBandKey, "fareBandKey is required");
        Objects.requireNonNull(category, "category is required");
        Objects.requireNonNull(paymentMethod, "paymentMethod is required");
        Objects.requireNonNull(period, "period is required");
        if (amountMinorUnits < 0)
            throw new IllegalArgumentException("amountMinorUnits must be non-negative");
    }
}
