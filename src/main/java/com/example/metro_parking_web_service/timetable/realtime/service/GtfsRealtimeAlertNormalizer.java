/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Maps the provider's GTFS-Realtime JSON representation to the service API model. */
@Component
public class GtfsRealtimeAlertNormalizer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public NormalizedAlerts normalize(String feedKey, JsonNode feed) {
        JsonNode header = feed.path("header");
        String version = text(header, "gtfsRealtimeVersion");
        String incrementality = text(header, "incrementality");
        if (version == null || incrementality == null) {
            throw new IllegalArgumentException("GTFS-Realtime header is incomplete");
        }
        if (!"FULL_DATASET".equals(incrementality)) {
            throw new IllegalArgumentException(
                    "Differential GTFS-Realtime alert feeds are not supported yet");
        }

        Instant providerGeneratedAt = epochInstant(header.get("timestamp"));
        List<ObjectNode> alerts = new ArrayList<>();
        JsonNode entities = feed.path("entity");
        if (!entities.isArray()) {
            throw new IllegalArgumentException("GTFS-Realtime entity list is invalid");
        }
        for (JsonNode entity : entities) {
            if (entity.path("isDeleted").asBoolean(false) || !entity.path("alert").isObject()) {
                continue;
            }
            alerts.add(normalizeAlert(feedKey, entity));
        }
        return new NormalizedAlerts(version, incrementality, providerGeneratedAt, alerts);
    }

    private ObjectNode normalizeAlert(String feedKey, JsonNode entity) {
        JsonNode alert = entity.path("alert");
        ObjectNode result = objectMapper.createObjectNode();
        putIfPresent(result, "id", entity, "id");
        result.put("feedKey", feedKey);
        result.put("cause", text(alert, "cause") == null ? "UNKNOWN_CAUSE" : text(alert, "cause"));
        result.put(
                "effect", text(alert, "effect") == null ? "UNKNOWN_EFFECT" : text(alert, "effect"));
        putIfPresent(result, "severity", alert, "severityLevel");
        putTranslation(result, "header", alert.path("headerText"));
        putTranslation(result, "description", alert.path("descriptionText"));
        putTranslation(result, "url", alert.path("url"));

        var periods = result.putArray("activePeriods");
        JsonNode activePeriods = alert.path("activePeriod");
        if (activePeriods.isArray()) {
            for (JsonNode period : activePeriods) {
                ObjectNode normalized = periods.addObject();
                putEpoch(normalized, "startsAt", period.get("start"));
                putEpoch(normalized, "endsAt", period.get("end"));
            }
        }

        var targets = result.putArray("informedEntities");
        JsonNode informedEntities = alert.path("informedEntity");
        if (informedEntities.isArray()) {
            for (JsonNode informedEntity : informedEntities) {
                ObjectNode target = targets.addObject();
                target.put("feedKey", feedKey);
                putIfPresent(target, "agencyId", informedEntity, "agencyId");
                putIfPresent(target, "routeId", informedEntity, "routeId");
                JsonNode trip = informedEntity.path("trip");
                putIfPresent(target, "tripId", trip, "tripId");
                putIfPresent(target, "startDate", trip, "startDate");
                putIfPresent(target, "startTime", trip, "startTime");
                putIfPresent(target, "scheduleRelationship", trip, "scheduleRelationship");
                if (!target.has("routeId")) putIfPresent(target, "routeId", trip, "routeId");
                putIfPresent(target, "stopId", informedEntity, "stopId");
                putIfPresent(target, "directionId", informedEntity, "directionId");
            }
        }
        return result;
    }

    private void putTranslation(ObjectNode target, String field, JsonNode translated) {
        JsonNode translations = translated.path("translation");
        if (!translations.isArray()) return;
        JsonNode fallback = null;
        for (JsonNode item : translations) {
            if (fallback == null && item.hasNonNull("text")) fallback = item;
            String language = text(item, "language");
            if ("en".equals(language) && item.hasNonNull("text")) {
                target.put(field, item.path("text").asText());
                return;
            }
        }
        if (fallback != null) target.put(field, fallback.path("text").asText());
    }

    private void putEpoch(ObjectNode target, String field, JsonNode value) {
        Instant instant = epochInstant(value);
        if (instant != null) target.put(field, instant.toString());
    }

    private Instant epochInstant(JsonNode value) {
        if (value == null || value.isNull() || value.asText().isBlank()) return null;
        try {
            return Instant.ofEpochSecond(Long.parseLong(value.asText()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "GTFS-Realtime timestamp is not Unix seconds", exception);
        }
    }

    private void putIfPresent(
            ObjectNode target, String field, JsonNode source, String sourceField) {
        if (source != null && source.hasNonNull(sourceField)) {
            JsonNode value = source.get(sourceField);
            if (value.isValueNode()) target.set(field, value.deepCopy());
        }
    }

    private String text(JsonNode source, String field) {
        JsonNode value = source == null ? null : source.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    public record NormalizedAlerts(
            String protocolVersion,
            String incrementality,
            Instant providerGeneratedAt,
            List<ObjectNode> alerts) {}
}
