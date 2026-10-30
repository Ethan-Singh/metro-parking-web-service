/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GtfsRealtimeBinaryNormalizerTest {

    private final GtfsRealtimeBinaryNormalizer normalizer = new GtfsRealtimeBinaryNormalizer();
    private final Instant retrievedAt = Instant.parse("2026-10-30T10:00:00Z");

    @Test
    void decodesSydneyTrainsTripUpdatesWithItsPinnedExtensionSchema() throws Exception {
        var result =
                normalizer.normalize(
                        "trip-updates-sydneytrains",
                        capture("docs/apis/v2-gtfs/realtime/sydneytrains/response.bin"),
                        retrievedAt);

        assertThat(result.protocolVersion()).isEqualTo("2.0");
        assertThat(result.incrementality()).isEqualTo("FULL_DATASET");
        assertThat(result.tripUpdates()).isNotEmpty();
        assertThat(result.tripUpdates().get(0).path("feedKey").asText())
                .isEqualTo("trip-updates-sydneytrains");
        assertThat(result.tripUpdates())
                .anySatisfy(update -> assertThat(update.path("stopUpdates")).isNotEmpty());
        assertThat(result.tripUpdates().get(0).path("matchStatus").asText()).isEqualTo("UNMATCHED");
    }

    @Test
    void decodesMetroTripUpdatesWithItsPinnedExtensionSchema() throws Exception {
        var result =
                normalizer.normalize(
                        "trip-updates-metro",
                        capture("docs/apis/v2-gtfs/realtime/metro/tripupdate"),
                        retrievedAt);

        assertThat(result.protocolVersion()).isEqualTo("1.0");
        assertThat(result.incrementality()).isEqualTo("FULL_DATASET");
        assertThat(result.tripUpdates()).isNotEmpty();
        assertThat(result.tripUpdates().get(0).path("startDate").asText())
                .matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(result.tripUpdates())
                .anySatisfy(update -> assertThat(update.path("stopUpdates")).isNotEmpty());
    }

    @Test
    void decodesSydneyTrainsVehiclePositionsAndTrainExtensions() throws Exception {
        var result =
                normalizer.normalize(
                        "vehicle-positions-sydneytrains",
                        capture("docs/apis/v2-gtfs/vehiclepos/sydneytrains/response.bin"),
                        retrievedAt);

        assertThat(result.protocolVersion()).isEqualTo("2.0");
        assertThat(result.vehiclePositions()).isNotEmpty();
        assertThat(result.vehiclePositions().get(0).path("latitude").isNumber()).isTrue();
        assertThat(result.vehiclePositions().get(0).path("longitude").isNumber()).isTrue();
        assertThat(result.vehiclePositions())
                .anySatisfy(
                        position ->
                                assertThat(position.path("vehicleAttributes").isObject()).isTrue());
        assertThat(result.vehiclePositions().get(0).path("carriages").isArray()).isTrue();
    }

    @Test
    void decodesMetroVehiclePositionsAndMetroSpecificExtensions() throws Exception {
        var result =
                normalizer.normalize(
                        "vehicle-positions-metro",
                        capture("docs/apis/v2-gtfs/vehiclepos/metro/vehpos"),
                        retrievedAt);

        assertThat(result.protocolVersion()).isEqualTo("1.0");
        assertThat(result.schemaKey()).isEqualTo("tfnsw-gtfs-realtime-metro-1007-v1");
        assertThat(result.vehiclePositions()).isNotEmpty();
        assertThat(result.vehiclePositions().get(0).path("latitude").isNumber()).isTrue();
        assertThat(result.vehiclePositions())
                .anySatisfy(
                        position ->
                                assertThat(position.path("vehicleAttributes").isObject()).isTrue());
        assertThat(result.vehiclePositions())
                .anySatisfy(position -> assertThat(position.path("carriages").isArray()).isTrue());
    }

    private byte[] capture(String path) throws Exception {
        return Files.readAllBytes(Path.of(path));
    }
}
