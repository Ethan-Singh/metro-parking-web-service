/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.timetable.realtime.document.RealtimeOverlayDocument;
import com.example.metro_parking_web_service.timetable.realtime.repository.RealtimeOverlayRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealtimeOverlayPublisherTest {

    @Mock private RealtimeOverlayRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<RealtimeOverlayDocument> active = new AtomicReference<>();
    private RealtimeOverlayPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new RealtimeOverlayPublisher(repository);
        when(repository.findById("active"))
                .thenAnswer(ignored -> Optional.ofNullable(active.get()));
        when(repository.save(any(RealtimeOverlayDocument.class)))
                .thenAnswer(
                        invocation -> {
                            RealtimeOverlayDocument document = invocation.getArgument(0);
                            active.set(document);
                            return document;
                        });
    }

    @Test
    void successfulSourceUpdatesPreserveOtherSourcesAndPublishOneVersionedPayload()
            throws Exception {
        publisher.publishAlerts(
                "alerts-sydneytrains", source("2.0", List.of(alert("train"))), Instant.now());
        String firstVersion = active.get().getOverlayVersion();
        publisher.publishAlerts(
                "alerts-metro", source("1.0", List.of(alert("metro"))), Instant.now());

        var payload = objectMapper.readTree(active.get().getPayloadJson());
        assertThat(active.get().getId()).isEqualTo("active");
        assertThat(active.get().getOverlayVersion()).isNotEqualTo(firstVersion);
        assertThat(active.get().getEtag()).startsWith("\"").endsWith("\"");
        assertThat(payload.path("sources")).hasSize(2);
        assertThat(payload.path("alerts")).hasSize(2);
    }

    @Test
    void failedRefreshRetainsLastGoodDataAndReportsDegradedSourceState() throws Exception {
        publisher.publishAlerts(
                "alerts-metro", source("1.0", List.of(alert("metro"))), Instant.now());

        publisher.publishFailure("alerts-metro", "upstream_http_503", Instant.now());

        var payload = objectMapper.readTree(active.get().getPayloadJson());
        assertThat(payload.path("alerts")).hasSize(1);
        assertThat(payload.path("alerts").get(0).path("id").asText()).isEqualTo("metro");
        var source = payload.path("sources").get(0);
        assertThat(source.path("status").asText()).isEqualTo("DEGRADED");
        assertThat(source.path("errorCode").asText()).isEqualTo("upstream_http_503");
        assertThat(source.path("lastSuccessfulAt").asText()).isNotBlank();
        assertThat(source.path("lastAttemptAt").asText()).isNotBlank();
    }

    @Test
    void firstRefreshFailurePublishesUnavailableSourceWithoutInventingData() throws Exception {
        publisher.publishFailure(
                "alerts-sydneytrains", "invalid_or_unavailable_feed", Instant.now());

        var payload = objectMapper.readTree(active.get().getPayloadJson());
        assertThat(payload.path("sources").get(0).path("status").asText()).isEqualTo("UNAVAILABLE");
        assertThat(payload.path("alerts")).isEmpty();
    }

    @Test
    void successfulProtobufRefreshPublishesNormalizedEntitiesWithSchemaProvenance()
            throws Exception {
        var updates = objectMapper.createArrayNode();
        updates.addObject().put("feedKey", "trip-updates-metro").put("tripId", "trip-1");
        var vehicles = objectMapper.createArrayNode();
        vehicles.addObject().put("feedKey", "trip-updates-metro").put("vehicleId", "vehicle-1");
        var retrievedAt = Instant.parse("2026-10-30T00:00:00Z");
        var normalized =
                new GtfsRealtimeBinaryNormalizer.NormalizedFeed(
                        "trip-updates-metro",
                        "tfnsw-gtfs-realtime-metro-1006-1999-v1",
                        "1.0",
                        "FULL_DATASET",
                        retrievedAt.minusSeconds(10),
                        retrievedAt,
                        updates,
                        vehicles);

        publisher.publishProtobuf(normalized);

        var payload = objectMapper.readTree(active.get().getPayloadJson());
        assertThat(payload.path("sources").get(0).path("schemaKey").asText())
                .isEqualTo("tfnsw-gtfs-realtime-metro-1006-1999-v1");
        assertThat(payload.path("sources").get(0).path("status").asText()).isEqualTo("CURRENT");
        assertThat(payload.path("tripUpdates").get(0).path("tripId").asText()).isEqualTo("trip-1");
        assertThat(payload.path("vehiclePositions").get(0).path("vehicleId").asText())
                .isEqualTo("vehicle-1");
    }

    @Test
    void protobufFailureRetainsLastGoodDataAndSchemaVariant() throws Exception {
        var updates = objectMapper.createArrayNode();
        updates.addObject().put("feedKey", "trip-updates-metro").put("tripId", "trip-1");
        var source =
                new GtfsRealtimeBinaryNormalizer.NormalizedFeed(
                        "trip-updates-metro",
                        "tfnsw-gtfs-realtime-metro-1007-v1",
                        "1.0",
                        "FULL_DATASET",
                        null,
                        Instant.parse("2026-10-30T00:00:00Z"),
                        updates,
                        objectMapper.createArrayNode());
        publisher.publishProtobuf(source);

        publisher.publishFailure(
                "trip-updates-metro",
                "tfnsw-gtfs-realtime-metro-variant-detected-at-runtime-v1",
                "upstream_http_503",
                Instant.parse("2026-10-30T00:01:00Z"));

        var payload = objectMapper.readTree(active.get().getPayloadJson());
        assertThat(payload.path("tripUpdates")).hasSize(1);
        assertThat(payload.path("sources").get(0).path("schemaKey").asText())
                .isEqualTo("tfnsw-gtfs-realtime-metro-1007-v1");
        assertThat(payload.path("sources").get(0).path("status").asText()).isEqualTo("DEGRADED");
        assertThat(payload.path("sources").get(0).path("errorCode").asText())
                .isEqualTo("upstream_http_503");
    }

    private GtfsRealtimeAlertNormalizer.NormalizedAlerts source(
            String protocolVersion, List<ObjectNode> alerts) {
        return new GtfsRealtimeAlertNormalizer.NormalizedAlerts(
                protocolVersion, "FULL_DATASET", Instant.parse("2026-10-30T00:00:00Z"), alerts);
    }

    private ObjectNode alert(String id) {
        ObjectNode alert = objectMapper.createObjectNode();
        alert.put("id", id);
        alert.put("feedKey", id.equals("metro") ? "alerts-metro" : "alerts-sydneytrains");
        return alert;
    }
}
