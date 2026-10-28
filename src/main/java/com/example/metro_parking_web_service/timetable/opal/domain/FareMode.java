/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.opal.domain;

public enum FareMode {
    TRAIN_METRO(FareFamily.RAIL, false),
    INTERCITY_TRAIN(FareFamily.RAIL, false),
    BUS(FareFamily.BUS, false),
    STOCKTON_FERRY(FareFamily.BUS, false),
    FERRY(FareFamily.FERRY, false),
    MANLY_FERRY(FareFamily.FERRY, true),
    LIGHT_RAIL(FareFamily.LIGHT_RAIL, false);

    private final FareFamily family;
    private final boolean transferWindowStartsAtTapOn;

    FareMode(FareFamily family, boolean transferWindowStartsAtTapOn) {
        this.family = family;
        this.transferWindowStartsAtTapOn = transferWindowStartsAtTapOn;
    }

    public FareFamily family() {
        return family;
    }

    public boolean transferWindowStartsAtTapOn() {
        return transferWindowStartsAtTapOn;
    }

    public enum FareFamily {
        RAIL,
        BUS,
        FERRY,
        LIGHT_RAIL
    }
}
