/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class TimetableSnapshotBootstrapTest {

    @Mock private TimetableSnapshotBuildService buildService;

    @Test
    void applicationStartupDelegatesToSnapshotBuildService() throws Exception {
        new TimetableSnapshotBootstrap(buildService).run(new DefaultApplicationArguments());

        verify(buildService).buildAndPublish();
    }
}
