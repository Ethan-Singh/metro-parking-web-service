/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("timetable.snapshot")
public record TimetableSnapshotProperties(
        boolean bootstrapEnabled,
        Path sydneyTrainsZip,
        Path metroZip,
        Path replacementBusZip,
        Path pcbcZip,
        int maximumCompressedPackageBytes) {}
