/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.service;

import com.example.metro_parking_web_service.timetable.realtime.document.RealtimeOverlayDocument;
import com.example.metro_parking_web_service.timetable.realtime.repository.RealtimeOverlayRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Writes a complete overlay document in one MongoDB operation. */
@Service
@RequiredArgsConstructor
public class RealtimeOverlayPublisher {

    private static final String ACTIVE_ID = "active";
    private static final String ALERTS_SCHEMA = "tfnsw-gtfs-realtime-alerts-json-v1";

    private final RealtimeOverlayRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public synchronized void publishAlerts(
            String feedKey,
            GtfsRealtimeAlertNormalizer.NormalizedAlerts source,
            Instant retrievedAt) {
        ObjectNode payload = currentPayload();
        replaceSource(payload, sourceState(feedKey, source, retrievedAt, "CURRENT", null));
        replaceRecords(payload.withArray("alerts"), feedKey, source.alerts());
        publish(payload);
    }

    public synchronized void publishProtobuf(GtfsRealtimeBinaryNormalizer.NormalizedFeed source) {
        ObjectNode payload = currentPayload();
        replaceSource(payload, sourceState(source));
        replaceRecords(payload.withArray("tripUpdates"), source.feedKey(), source.tripUpdates());
        replaceRecords(
                payload.withArray("vehiclePositions"), source.feedKey(), source.vehiclePositions());
        publish(payload);
    }

    public synchronized void publishFailure(String feedKey, String errorCode, Instant attemptedAt) {
        publishFailure(feedKey, ALERTS_SCHEMA, errorCode, attemptedAt);
    }

    public synchronized void publishFailure(
            String feedKey, String schemaKey, String errorCode, Instant attemptedAt) {
        ObjectNode payload = currentPayload();
        ObjectNode previous = source(payload, feedKey);
        ObjectNode next = previous == null ? objectMapper.createObjectNode() : previous.deepCopy();
        next.put("feedKey", feedKey);
        if (previous == null || !previous.hasNonNull("schemaKey")) {
            next.put("schemaKey", schemaKey);
        }
        next.put(
                "status",
                previous != null && previous.hasNonNull("lastSuccessfulAt")
                        ? "DEGRADED"
                        : "UNAVAILABLE");
        next.put("lastAttemptAt", attemptedAt.toString());
        next.put("errorCode", errorCode);
        replaceSource(payload, next);
        publish(payload);
    }

    private ObjectNode sourceState(GtfsRealtimeBinaryNormalizer.NormalizedFeed source) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("feedKey", source.feedKey());
        result.put("schemaKey", source.schemaKey());
        result.put("status", "CURRENT");
        result.put("gtfsRealtimeVersion", source.protocolVersion());
        result.put("incrementality", source.incrementality());
        if (source.providerGeneratedAt() != null) {
            result.put("providerGeneratedAt", source.providerGeneratedAt().toString());
        }
        result.put("lastAttemptAt", source.retrievedAt().toString());
        result.put("retrievedAt", source.retrievedAt().toString());
        result.put("lastSuccessfulAt", source.retrievedAt().toString());
        return result;
    }

    private ObjectNode sourceState(
            String feedKey,
            GtfsRealtimeAlertNormalizer.NormalizedAlerts source,
            Instant retrievedAt,
            String status,
            String errorCode) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("feedKey", feedKey);
        result.put("schemaKey", ALERTS_SCHEMA);
        result.put("status", status);
        result.put("gtfsRealtimeVersion", source.protocolVersion());
        result.put("incrementality", source.incrementality());
        if (source.providerGeneratedAt() != null) {
            result.put("providerGeneratedAt", source.providerGeneratedAt().toString());
        }
        result.put("lastAttemptAt", retrievedAt.toString());
        result.put("retrievedAt", retrievedAt.toString());
        result.put("lastSuccessfulAt", retrievedAt.toString());
        if (errorCode != null) result.put("errorCode", errorCode);
        return result;
    }

    private ObjectNode currentPayload() {
        return repository
                .findById(ACTIVE_ID)
                .map(RealtimeOverlayDocument::getPayloadJson)
                .filter(value -> value != null && !value.isBlank())
                .map(this::parsePayload)
                .orElseGet(this::emptyPayload);
    }

    private ObjectNode parsePayload(String payloadJson) {
        try {
            return (ObjectNode) objectMapper.readTree(payloadJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read published realtime overlay", exception);
        }
    }

    private ObjectNode emptyPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("schemaVersion", "1");
        payload.putArray("sources");
        payload.putArray("alerts");
        payload.putArray("tripUpdates");
        payload.putArray("vehiclePositions");
        return payload;
    }

    private ObjectNode source(ObjectNode payload, String feedKey) {
        for (var item : payload.withArray("sources")) {
            if (feedKey.equals(item.path("feedKey").asText())) return (ObjectNode) item;
        }
        return null;
    }

    private void replaceSource(ObjectNode payload, ObjectNode replacement) {
        ArrayNode sources = payload.withArray("sources");
        String feedKey = replacement.path("feedKey").asText();
        for (int i = sources.size() - 1; i >= 0; i--) {
            if (feedKey.equals(sources.get(i).path("feedKey").asText())) sources.remove(i);
        }
        sources.add(replacement);
    }

    private void replaceRecords(ArrayNode target, String feedKey, List<ObjectNode> records) {
        for (int i = target.size() - 1; i >= 0; i--) {
            if (feedKey.equals(target.get(i).path("feedKey").asText())) target.remove(i);
        }
        records.forEach(target::add);
    }

    private void replaceRecords(ArrayNode target, String feedKey, ArrayNode records) {
        for (int i = target.size() - 1; i >= 0; i--) {
            if (feedKey.equals(target.get(i).path("feedKey").asText())) target.remove(i);
        }
        records.forEach(target::add);
    }

    private void publish(ObjectNode payload) {
        Instant publishedAt = Instant.now();
        String version =
                publishedAt.toString() + "-" + UUID.randomUUID().toString().substring(0, 8);
        payload.put("overlayVersion", version);
        payload.put("generatedAt", publishedAt.toString());
        byte[] bytes = serialize(payload);
        RealtimeOverlayDocument document = new RealtimeOverlayDocument();
        document.setId(ACTIVE_ID);
        document.setOverlayVersion(version);
        document.setGeneratedAt(publishedAt);
        document.setEtag('"' + sha256(bytes) + '"');
        document.setPayloadJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        repository.save(document);
    }

    private byte[] serialize(ObjectNode payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize realtime overlay", exception);
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
