/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.opal.domain;

import java.util.Objects;

/** Estimate preference only; this is not an Opal account or proof of eligibility. */
public record OpalFareProfile(FareCategory category, PaymentMethod paymentMethod) {

    public static final OpalFareProfile ADULT_OPAL =
            new OpalFareProfile(FareCategory.ADULT, PaymentMethod.OPAL_CARD);

    public OpalFareProfile {
        Objects.requireNonNull(category, "category is required");
        Objects.requireNonNull(paymentMethod, "paymentMethod is required");

        if (paymentMethod == PaymentMethod.CONTACTLESS && category != FareCategory.ADULT) {
            throw new IllegalArgumentException("Contactless pricing uses the Adult fare category");
        }
        if (paymentMethod == PaymentMethod.SINGLE_TRIP_TICKET
                && category != FareCategory.ADULT
                && category != FareCategory.CHILD_YOUTH) {
            throw new IllegalArgumentException(
                    "Single-trip tickets support Adult or Child/Youth fares");
        }
    }
}
