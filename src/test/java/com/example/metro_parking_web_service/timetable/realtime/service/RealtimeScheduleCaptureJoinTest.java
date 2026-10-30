/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.timetable.api.document.TimetableManifestDocument;
import com.example.metro_parking_web_service.timetable.api.document.TimetablePackageDocument;
import com.example.metro_parking_web_service.timetable.api.repository.TimetableManifestRepository;
import com.example.metro_parking_web_service.timetable.api.repository.TimetablePackageRepository;
import com.example.metro_parking_web_service.timetable.api.service.GtfsDatePackageBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Validates joins with the checked-in production GTFS ZIP and realtime protobuf captures. */
@ExtendWith(MockitoExtension.class)
class RealtimeScheduleCaptureJoinTest {

    private static final String VERSION = "captured-production-fixtures";
    private static final LocalDate METRO_CAPTURE_DATE = LocalDate.parse("2026-10-28");
    private static final LocalDate TRAIN_CAPTURE_DATE = LocalDate.parse("2026-10-30");

    @Mock private TimetableManifestRepository manifestRepository;
    @Mock private TimetablePackageRepository packageRepository;

    @Test
    void capturesJoinOnlyWhenScheduleCoverageContainsTheirServiceDate() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<TimetablePackageDocument> packages = buildCapturedPackages(mapper);
        TimetableManifestDocument manifest = new TimetableManifestDocument();
        manifest.setId("active");
        manifest.setSnapshotVersion(VERSION);
        when(manifestRepository.findById("active")).thenReturn(Optional.of(manifest));
        when(packageRepository.findAllBySnapshotVersionAndOffering(VERSION, "ALL"))
                .thenReturn(packages);
        RealtimeScheduleMatcher matcher =
                new RealtimeScheduleMatcher(manifestRepository, packageRepository);
        GtfsRealtimeBinaryNormalizer normalizer = new GtfsRealtimeBinaryNormalizer();

        var trains =
                matcher.join(
                        normalizer.normalize(
                                "trip-updates-sydneytrains",
                                Files.readAllBytes(
                                        Path.of(
                                                "docs/apis/v2-gtfs/realtime/sydneytrains/response.bin")),
                                Instant.parse("2026-10-28T10:00:00Z")));
        var metro =
                matcher.join(
                        normalizer.normalize(
                                "trip-updates-metro",
                                Files.readAllBytes(
                                        Path.of("docs/apis/v2-gtfs/realtime/metro/tripupdate")),
                                Instant.parse("2026-10-28T10:00:00Z")));
        var trainVehicles =
                matcher.join(
                        normalizer.normalize(
                                "vehicle-positions-sydneytrains",
                                Files.readAllBytes(
                                        Path.of(
                                                "docs/apis/v2-gtfs/vehiclepos/sydneytrains/response.bin")),
                                Instant.parse("2026-10-28T10:00:00Z")));
        var metroVehicles =
                matcher.join(
                        normalizer.normalize(
                                "vehicle-positions-metro",
                                Files.readAllBytes(
                                        Path.of("docs/apis/v2-gtfs/vehiclepos/metro/vehpos")),
                                Instant.parse("2026-10-28T10:00:00Z")));

        assertTrue(
                countStatus(trains.tripUpdates(), "MATCHED") > 0,
                () -> "captured train trips join; first=" + trains.tripUpdates().get(0));
        assertTrue(metro.tripUpdates().size() > 0, "captured Metro updates are present");
        assertEquals("UNMATCHED", metro.tripUpdates().get(0).path("matchStatus").asText());
        assertFalse(metro.tripUpdates().get(0).has("scheduleTripId"));
        assertTrue(
                countStatus(trainVehicles.vehiclePositions(), "MATCHED") > 0,
                "captured Sydney Trains vehicle trips join");
        assertTrue(
                metroVehicles.vehiclePositions().size() > 0, "captured Metro vehicles are present");
        assertEquals(
                "UNMATCHED", metroVehicles.vehiclePositions().get(0).path("matchStatus").asText());
    }

    private List<TimetablePackageDocument> buildCapturedPackages(ObjectMapper mapper)
            throws Exception {
        GtfsDatePackageBuilder builder = new GtfsDatePackageBuilder();
        List<TimetablePackageDocument> packages = new ArrayList<>();
        packages.add(
                packageDocument(
                        mapper,
                        builder.build(
                                Path.of(
                                        "docs/apis/v1-gtfs/schedules/sydneytrains/sydneytrains_GTFS_PROD_20261027140100.zip"),
                                "sydneytrains",
                                "SYDNEY_TRAINS",
                                METRO_CAPTURE_DATE)));
        packages.add(
                packageDocument(
                        mapper,
                        builder.build(
                                Path.of(
                                        "docs/apis/v1-gtfs/schedules/sydneytrains/sydneytrains_GTFS_PROD_20261029140100.zip"),
                                "sydneytrains",
                                "SYDNEY_TRAINS",
                                TRAIN_CAPTURE_DATE)));
        packages.add(
                packageDocument(
                        mapper,
                        builder.build(
                                Path.of(
                                        "docs/apis/v2-gtfs/schedules/metro/metro_GTFS_PROD_20261026124700.zip"),
                                "metro",
                                "SYDNEY_METRO",
                                METRO_CAPTURE_DATE)));
        return packages;
    }

    private TimetablePackageDocument packageDocument(
            ObjectMapper mapper, com.fasterxml.jackson.databind.JsonNode payload) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            mapper.writeValue(gzip, payload);
        }
        TimetablePackageDocument document = new TimetablePackageDocument();
        document.setSnapshotVersion(VERSION);
        document.setOffering("ALL");
        document.setPayload(bytes.toByteArray());
        document.setContentEncoding("gzip");
        return document;
    }

    private int countStatus(ArrayNode records, String expected) {
        int count = 0;
        for (var record : records) {
            if (expected.equals(record.path("matchStatus").asText())) count++;
        }
        return count;
    }
}
