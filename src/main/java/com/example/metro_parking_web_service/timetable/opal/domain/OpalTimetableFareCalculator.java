/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.opal.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies v1.7 itinerary transfer discounts to fares resolved from the versioned fare tables. */
public final class OpalTimetableFareCalculator {

    public OpalFareEstimate estimate(
            List<PricedFareSection> sections, OpalFareProfile profile, String fareDataRevision) {
        Objects.requireNonNull(sections, "sections are required");
        Objects.requireNonNull(profile, "profile is required");
        Objects.requireNonNull(fareDataRevision, "fareDataRevision is required");
        if (sections.isEmpty())
            throw new IllegalArgumentException("At least one fare section is required");

        int baseFare = 0;
        int transferDiscount = 0;
        int stationAccessFees = 0;
        List<String> limitations = new ArrayList<>();

        for (int index = 0; index < sections.size(); index++) {
            PricedFareSection current = sections.get(index);
            if (!current.profile().equals(profile)) {
                throw new IllegalArgumentException(
                        "All fare sections must use the requested fare profile");
            }
            baseFare = Math.addExact(baseFare, current.baseFareMinorUnits());
            stationAccessFees =
                    Math.addExact(stationAccessFees, current.stationAccessFeeMinorUnits());
            if (index == 0 || profile.paymentMethod() == PaymentMethod.SINGLE_TRIP_TICKET) continue;

            PricedFareSection previous = sections.get(index - 1);
            if (current.tapOnAt().isBefore(previous.tapOnAt())) {
                throw new IllegalArgumentException("Fare sections must be ordered by tap-on time");
            }
            Instant transferReference =
                    previous.mode().transferWindowStartsAtTapOn()
                            ? previous.tapOnAt()
                            : previous.tapOffAt();
            Duration transferWindow =
                    previous.mode() == FareMode.MANLY_FERRY
                            ? OpalFareRules.MANLY_FERRY_TRANSFER_WINDOW
                            : OpalFareRules.STANDARD_TRANSFER_WINDOW;
            Duration connection = Duration.between(transferReference, current.tapOnAt());
            boolean timelyConnection =
                    !connection.isNegative() && connection.compareTo(transferWindow) < 0;
            boolean changesMode = previous.mode().family() != current.mode().family();
            if (timelyConnection && changesMode) {
                transferDiscount =
                        Math.addExact(
                                transferDiscount,
                                Math.min(
                                        current.baseFareMinorUnits(),
                                        OpalFareRules.intermodalDiscountMinorUnits(profile)));
            }
        }

        limitations.add(
                "Daily and weekly caps and rewards are excluded because prior card travel is"
                        + " unknown.");
        limitations.add(
                "Fare sections must already include the applicable peak/off-peak table value.");
        limitations.add("A selected concession profile does not verify travel eligibility.");
        if (profile.paymentMethod() == PaymentMethod.SINGLE_TRIP_TICKET) {
            limitations.add(
                    "Single-trip tickets cannot be used to break a journey; each section is priced"
                            + " separately.");
        }
        int amount =
                Math.addExact(Math.subtractExact(baseFare, transferDiscount), stationAccessFees);
        return new OpalFareEstimate(
                amount,
                baseFare,
                transferDiscount,
                stationAccessFees,
                profile,
                fareDataRevision,
                OpalFareRules.RULES_REVISION,
                limitations);
    }
}
