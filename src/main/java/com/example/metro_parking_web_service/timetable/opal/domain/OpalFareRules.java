/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.opal.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/** Versioned business-rule constants transcribed from the captured TfNSW v1.7 guide. */
public final class OpalFareRules {

    public static final String RULES_REVISION = "v1.7";
    public static final Duration STANDARD_TRANSFER_WINDOW = Duration.ofMinutes(60);
    public static final Duration MANLY_FERRY_TRANSFER_WINDOW = Duration.ofMinutes(130);
    public static final Duration INTRA_MODAL_TRANSFER_WINDOW = Duration.ofMinutes(60);
    public static final LocalTime DAILY_CAP_RESET = LocalTime.of(4, 0);
    public static final int OFF_PEAK_DISCOUNT_PERCENT = 30;
    public static final int ADULT_INTERMODAL_DISCOUNT_MINOR_UNITS = 200;
    public static final int OTHER_INTERMODAL_DISCOUNT_MINOR_UNITS = 100;

    private static final LocalTime STANDARD_AM_PEAK_START = LocalTime.of(6, 30);
    private static final LocalTime INTERCITY_AM_PEAK_START = LocalTime.of(6, 0);
    private static final LocalTime AM_PEAK_END = LocalTime.of(10, 0);
    private static final LocalTime PM_PEAK_START = LocalTime.of(15, 0);
    private static final LocalTime PM_PEAK_END = LocalTime.of(19, 0);

    private OpalFareRules() {}

    public static int intermodalDiscountMinorUnits(OpalFareProfile profile) {
        Objects.requireNonNull(profile, "profile is required");
        if (profile.paymentMethod() == PaymentMethod.SINGLE_TRIP_TICKET) return 0;
        return profile.category() == FareCategory.ADULT
                ? ADULT_INTERMODAL_DISCOUNT_MINOR_UNITS
                : OTHER_INTERMODAL_DISCOUNT_MINOR_UNITS;
    }

    /** Determines the fare period at the first tap-on, before table lookup. */
    public static FarePeriod farePeriod(
            FareMode mode, LocalDate travelDate, LocalTime tapOnTime, boolean publicHoliday) {
        Objects.requireNonNull(mode, "mode is required");
        Objects.requireNonNull(travelDate, "travelDate is required");
        Objects.requireNonNull(tapOnTime, "tapOnTime is required");

        if (!supportsOffPeakDiscount(mode)) return FarePeriod.STANDARD;
        DayOfWeek day = travelDate.getDayOfWeek();
        if (publicHoliday
                || day == DayOfWeek.FRIDAY
                || day == DayOfWeek.SATURDAY
                || day == DayOfWeek.SUNDAY) {
            return FarePeriod.OFF_PEAK;
        }

        LocalTime amPeakStart =
                mode == FareMode.INTERCITY_TRAIN ? INTERCITY_AM_PEAK_START : STANDARD_AM_PEAK_START;
        boolean amPeak = !tapOnTime.isBefore(amPeakStart) && tapOnTime.isBefore(AM_PEAK_END);
        boolean pmPeak = !tapOnTime.isBefore(PM_PEAK_START) && tapOnTime.isBefore(PM_PEAK_END);
        return amPeak || pmPeak ? FarePeriod.PEAK : FarePeriod.OFF_PEAK;
    }

    private static boolean supportsOffPeakDiscount(FareMode mode) {
        return mode != FareMode.FERRY && mode != FareMode.MANLY_FERRY;
    }
}
