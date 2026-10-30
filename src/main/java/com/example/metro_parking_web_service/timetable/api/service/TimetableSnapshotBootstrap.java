/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Local captured-feed bootstrap; disabled unless explicitly enabled. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "timetable.snapshot",
        name = "bootstrap-enabled",
        havingValue = "true")
public class TimetableSnapshotBootstrap implements ApplicationRunner {

    private final TimetableSnapshotBuildService buildService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        buildService.buildAndPublish();
    }
}
