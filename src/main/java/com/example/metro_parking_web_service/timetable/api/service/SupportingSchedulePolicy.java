/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import java.time.LocalTime;
import java.util.List;

/** Reviewed feed and route links for supporting services included in rail packages. */
public final class SupportingSchedulePolicy {

    private static final String ALERT_80106 = "https://transportnsw.info/alerts/details/ems-80106";

    private SupportingSchedulePolicy() {}

    public static List<Route> routes() {
        return List.of(
                new Route(
                        "replacementbus",
                        "7063_9T2",
                        "REPLACEMENT",
                        "Leppington to Glenfield",
                        LocalTime.of(8, 0),
                        LocalTime.of(14, 0),
                        ALERT_80106,
                        "217933",
                        "216710",
                        List.of(
                                new StopMapping("217938", "217933"),
                                new StopMapping("217421", "217426"),
                                new StopMapping("217422", "217426"),
                                new StopMapping("216711", "216710"),
                                new StopMapping("216712", "216710"))),
                new Route(
                        "pcbc",
                        "700015_11T2",
                        "PLANNED_CONTINGENCY",
                        "Olympic Park to Ashfield",
                        LocalTime.of(2, 0),
                        LocalTime.of(10, 0),
                        ALERT_80106,
                        "212710",
                        "213110",
                        List.of(
                                new StopMapping("212712", "212710"),
                                new StopMapping("212792", "212710"),
                                new StopMapping("214021", "214020"),
                                new StopMapping("214052", "214020"),
                                new StopMapping("214011", "214010"),
                                new StopMapping("214048", "214010"),
                                new StopMapping("213563", "213510"),
                                new StopMapping("213464", "213410"),
                                new StopMapping("213466", "213410"),
                                new StopMapping("213268", "213210"),
                                new StopMapping("213111", "213110"),
                                new StopMapping("213112", "213110"))));
    }

    public record Route(
            String feedKey,
            String providerRouteId,
            String servicePurpose,
            String affectedSegment,
            LocalTime activeFrom,
            LocalTime activeUntil,
            String evidenceReference,
            String affectedFromProviderStopId,
            String affectedToProviderStopId,
            List<StopMapping> stopMappings) {}

    public record StopMapping(String supportingProviderStopId, String railProviderStopId) {}
}
