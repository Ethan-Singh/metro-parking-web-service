/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.service;

import com.example.metro_parking_web_service.timetable.realtime.protobuf.metro.MetroGtfsRealtime;
import com.example.metro_parking_web_service.timetable.realtime.protobuf.metro.TfnswMetroExtensions;
import com.example.metro_parking_web_service.timetable.realtime.protobuf.sydney.SydneyGtfsRealtime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.Message;
import com.google.protobuf.UnknownFieldSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Decodes endpoint-pinned GTFS-Realtime schemas into the service-owned overlay shape. */
@Component
public class GtfsRealtimeBinaryNormalizer {

    private static final DateTimeFormatter GTFS_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NormalizedFeed normalize(String feedKey, byte[] bytes, Instant retrievedAt) {
        ParsedFeed parsed = parse(feedKey, bytes);
        Message feed = parsed.message();
        Message header = message(feed, "header");
        if (header == null) throw new IllegalArgumentException("GTFS-Realtime header is missing");

        String incrementality = enumName(header, "incrementality");
        if (!"FULL_DATASET".equals(incrementality)) {
            throw new IllegalArgumentException(
                    "Differential GTFS-Realtime feeds are not supported");
        }
        String schemaKey = parsed.schemaKey();
        Instant providerGeneratedAt = instant(header, "timestamp");
        ArrayNode tripUpdates = objectMapper.createArrayNode();
        ArrayNode vehiclePositions = objectMapper.createArrayNode();
        List<Message> entities = messages(feed, "entity");
        for (Message entity : entities) {
            if (bool(entity, "is_deleted", false)) continue;
            String entityId = string(entity, "id");
            Message tripUpdate = message(entity, "trip_update");
            if (tripUpdate != null) {
                tripUpdates.add(normalizeTripUpdate(feedKey, entityId, tripUpdate, retrievedAt));
            }
            Message vehiclePosition = message(entity, "vehicle");
            if (vehiclePosition != null) {
                vehiclePositions.add(
                        normalizeVehiclePosition(feedKey, entityId, vehiclePosition, retrievedAt));
            }
        }
        return new NormalizedFeed(
                feedKey,
                schemaKey,
                string(header, "gtfs_realtime_version"),
                incrementality,
                providerGeneratedAt,
                retrievedAt,
                tripUpdates,
                vehiclePositions);
    }

    private ParsedFeed parse(String feedKey, byte[] bytes) {
        try {
            ExtensionRegistry registry = ExtensionRegistry.newInstance();
            if (feedKey.contains("sydneytrains")) {
                SydneyGtfsRealtime.registerAllExtensions(registry);
                return new ParsedFeed(
                        SydneyGtfsRealtime.FeedMessage.parseFrom(bytes, registry),
                        "tfnsw-gtfs-realtime-sydneytrains-1007-v1");
            }
            if (feedKey.contains("metro")) {
                TfnswMetroExtensions.registerAllExtensions(registry);
                Message metro = MetroGtfsRealtime.FeedMessage.parseFrom(bytes, registry);
                if (containsUnknownField(metro, 1007)) {
                    ExtensionRegistry metro1007Registry = ExtensionRegistry.newInstance();
                    SydneyGtfsRealtime.registerAllExtensions(metro1007Registry);
                    return new ParsedFeed(
                            SydneyGtfsRealtime.FeedMessage.parseFrom(bytes, metro1007Registry),
                            "tfnsw-gtfs-realtime-metro-1007-v1");
                }
                return new ParsedFeed(metro, "tfnsw-gtfs-realtime-metro-1006-1999-v1");
            }
            throw new IllegalArgumentException("No pinned protobuf schema for feed " + feedKey);
        } catch (com.google.protobuf.InvalidProtocolBufferException exception) {
            throw new IllegalArgumentException(
                    "TfNSW returned an invalid GTFS-Realtime protobuf", exception);
        }
    }

    private boolean containsUnknownField(Message message, int fieldNumber) {
        UnknownFieldSet fields = message.getUnknownFields();
        if (fields.hasField(fieldNumber)) return true;
        for (Object value : message.getAllFields().values()) {
            if (value instanceof Message child && containsUnknownField(child, fieldNumber)) {
                return true;
            }
            if (value instanceof List<?> repeated) {
                for (Object item : repeated) {
                    if (item instanceof Message child && containsUnknownField(child, fieldNumber)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private ObjectNode normalizeTripUpdate(
            String feedKey, String entityId, Message update, Instant retrievedAt) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("feedKey", feedKey);
        put(result, "entityId", entityId);
        result.put("retrievedAt", retrievedAt.toString());
        result.put("matchStatus", "UNMATCHED");
        Message trip = message(update, "trip");
        if (trip != null) {
            put(result, "tripId", string(trip, "trip_id"));
            put(result, "routeId", string(trip, "route_id"));
            put(result, "startDate", normalizedDate(string(trip, "start_date")));
            put(result, "startTime", string(trip, "start_time"));
            put(result, "directionId", number(trip, "direction_id"));
            put(result, "scheduleRelationship", enumName(trip, "schedule_relationship"));
        }
        put(result, "providerTimestamp", instant(update, "timestamp"));
        put(result, "delaySeconds", number(update, "delay"));
        Message vehicle = message(update, "vehicle");
        if (vehicle != null) {
            ObjectNode descriptor = result.putObject("vehicle");
            put(descriptor, "id", string(vehicle, "id"));
            put(descriptor, "label", string(vehicle, "label"));
        }
        ArrayNode stops = result.putArray("stopUpdates");
        for (Message stop : messages(update, "stop_time_update")) {
            ObjectNode stopResult = stops.addObject();
            put(stopResult, "stopId", string(stop, "stop_id"));
            put(stopResult, "stopSequence", number(stop, "stop_sequence"));
            put(stopResult, "scheduleRelationship", enumName(stop, "schedule_relationship"));
            addStopEvent(stopResult, "arrival", message(stop, "arrival"));
            addStopEvent(stopResult, "departure", message(stop, "departure"));
            put(stopResult, "departureOccupancy", enumName(stop, "departure_occupancy_status"));
        }
        return result;
    }

    private void addStopEvent(ObjectNode target, String prefix, Message event) {
        if (event == null) return;
        put(target, prefix + "Prediction", instant(event, "time"));
        put(target, prefix + "DelaySeconds", number(event, "delay"));
        put(target, prefix + "UncertaintySeconds", number(event, "uncertainty"));
    }

    private ObjectNode normalizeVehiclePosition(
            String feedKey, String entityId, Message position, Instant retrievedAt) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("feedKey", feedKey);
        put(result, "entityId", entityId);
        result.put("retrievedAt", retrievedAt.toString());
        result.put("matchStatus", "UNMATCHED");
        Message trip = message(position, "trip");
        if (trip != null) {
            put(result, "tripId", string(trip, "trip_id"));
            put(result, "routeId", string(trip, "route_id"));
            put(result, "startDate", normalizedDate(string(trip, "start_date")));
            put(result, "startTime", string(trip, "start_time"));
            put(result, "directionId", number(trip, "direction_id"));
            put(result, "scheduleRelationship", enumName(trip, "schedule_relationship"));
        }
        Message vehicle = message(position, "vehicle");
        if (vehicle != null) {
            put(result, "vehicleId", string(vehicle, "id"));
            put(result, "vehicleLabel", string(vehicle, "label"));
            Message attributes = message(vehicle, "tfnsw_vehicle_descriptor");
            if (attributes != null) addVehicleAttributes(result, attributes);
        }
        Message coordinate = message(position, "position");
        if (coordinate != null) {
            put(result, "latitude", number(coordinate, "latitude"));
            put(result, "longitude", number(coordinate, "longitude"));
            put(result, "bearing", number(coordinate, "bearing"));
            put(result, "speedMetersPerSecond", number(coordinate, "speed"));
            put(result, "trackDirection", enumName(coordinate, "track_direction"));
        }
        put(result, "stopId", string(position, "stop_id"));
        put(result, "stopSequence", number(position, "current_stop_sequence"));
        put(result, "status", enumName(position, "current_status"));
        put(result, "timestamp", instant(position, "timestamp"));
        put(result, "occupancy", enumName(position, "occupancy_status"));
        put(result, "congestion", enumName(position, "congestion_level"));
        ArrayNode carriages = result.putArray("carriages");
        for (Message carriage : extensionMessages(position, "consist")) {
            ObjectNode carriageResult = carriages.addObject();
            put(carriageResult, "name", string(carriage, "name"));
            put(carriageResult, "positionInConsist", number(carriage, "position_in_consist"));
            put(carriageResult, "occupancy", enumName(carriage, "occupancy_status"));
            put(
                    carriageResult,
                    "departureOccupancy",
                    enumName(carriage, "departure_occupancy_status"));
            put(carriageResult, "quietCarriage", nullableBoolean(carriage, "quiet_carriage"));
            put(carriageResult, "toilet", enumName(carriage, "toilet"));
            put(carriageResult, "luggageRack", nullableBoolean(carriage, "luggage_rack"));
        }
        return result;
    }

    private void addVehicleAttributes(ObjectNode target, Message attributes) {
        ObjectNode result = target.putObject("vehicleAttributes");
        put(result, "airConditioned", nullableBoolean(attributes, "air_conditioned"));
        put(result, "wheelchairCapacity", number(attributes, "wheelchair_accessible"));
        put(result, "vehicleModel", string(attributes, "vehicle_model"));
        put(result, "performingPriorTrip", nullableBoolean(attributes, "performing_prior_trip"));
        put(result, "specialAttributes", number(attributes, "special_vehicle_attributes"));
    }

    private List<Message> extensionMessages(Message message, String fieldName) {
        List<Message> result = new ArrayList<>();
        for (Map.Entry<FieldDescriptor, Object> entry : message.getAllFields().entrySet()) {
            if (entry.getKey().isExtension() && entry.getKey().getName().equals(fieldName)) {
                for (Object value : (List<?>) entry.getValue()) result.add((Message) value);
            }
        }
        return result;
    }

    private Message message(Message parent, String fieldName) {
        FieldDescriptor field = field(parent, fieldName);
        if (field == null || !parent.hasField(field)) return null;
        return (Message) parent.getField(field);
    }

    private List<Message> messages(Message parent, String fieldName) {
        FieldDescriptor field = field(parent, fieldName);
        if (field == null) return List.of();
        return (List<Message>) parent.getField(field);
    }

    private String string(Message parent, String fieldName) {
        Object value = scalar(parent, fieldName);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private Object number(Message parent, String fieldName) {
        Object value = scalar(parent, fieldName);
        return value instanceof Number ? value : null;
    }

    private Boolean nullableBoolean(Message parent, String fieldName) {
        Object value = scalar(parent, fieldName);
        return value instanceof Boolean bool ? bool : null;
    }

    private boolean bool(Message parent, String fieldName, boolean defaultValue) {
        Object value = scalar(parent, fieldName);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private String enumName(Message parent, String fieldName) {
        Object value = scalar(parent, fieldName);
        return value instanceof EnumValueDescriptor descriptor ? descriptor.getName() : null;
    }

    private Instant instant(Message parent, String fieldName) {
        Object value = scalar(parent, fieldName);
        if (!(value instanceof Number number)) return null;
        long epochSeconds = number.longValue();
        return epochSeconds > 0 ? Instant.ofEpochSecond(epochSeconds) : null;
    }

    private Object scalar(Message parent, String fieldName) {
        FieldDescriptor field = field(parent, fieldName);
        if (field == null || !parent.hasField(field)) return null;
        Object value = parent.getField(field);
        return value instanceof Message ? null : value;
    }

    private FieldDescriptor field(Message message, String fieldName) {
        FieldDescriptor field = message.getDescriptorForType().findFieldByName(fieldName);
        if (field != null) return field;
        return message.getAllFields().keySet().stream()
                .filter(
                        candidate ->
                                candidate.isExtension() && candidate.getName().equals(fieldName))
                .findFirst()
                .orElse(null);
    }

    private void put(ObjectNode target, String field, Object value) {
        if (value == null) return;
        if (value instanceof String text) target.put(field, text);
        else if (value instanceof Boolean bool) target.put(field, bool);
        else if (value instanceof Integer integer) target.put(field, integer);
        else if (value instanceof Long longValue) target.put(field, longValue);
        else if (value instanceof Float floatValue) target.put(field, floatValue);
        else if (value instanceof Double doubleValue) target.put(field, doubleValue);
        else if (value instanceof Instant instant) target.put(field, instant.toString());
    }

    private String normalizedDate(String providerDate) {
        if (providerDate == null || providerDate.isBlank()) return null;
        try {
            return LocalDate.parse(providerDate, GTFS_DATE).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "GTFS-Realtime trip start_date is invalid", exception);
        }
    }

    private record ParsedFeed(Message message, String schemaKey) {}

    public record NormalizedFeed(
            String feedKey,
            String schemaKey,
            String protocolVersion,
            String incrementality,
            Instant providerGeneratedAt,
            Instant retrievedAt,
            ArrayNode tripUpdates,
            ArrayNode vehiclePositions) {}
}
