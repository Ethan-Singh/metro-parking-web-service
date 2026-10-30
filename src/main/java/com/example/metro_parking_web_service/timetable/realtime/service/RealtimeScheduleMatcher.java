/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.service;

import com.example.metro_parking_web_service.timetable.api.document.TimetablePackageDocument;
import com.example.metro_parking_web_service.timetable.api.repository.TimetableManifestRepository;
import com.example.metro_parking_web_service.timetable.api.repository.TimetablePackageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Joins realtime trip references to the active, feed-scoped timetable snapshot. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimeScheduleMatcher {

    private static final Map<String, String> SCHEDULE_FEEDS =
            Map.of(
                    "trip-updates-sydneytrains", "sydneytrains",
                    "vehicle-positions-sydneytrains", "sydneytrains",
                    "trip-updates-metro", "metro",
                    "vehicle-positions-metro", "metro");

    private final TimetableManifestRepository manifestRepository;
    private final TimetablePackageRepository packageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile SnapshotIndex cachedIndex;

    public GtfsRealtimeBinaryNormalizer.NormalizedFeed join(
            GtfsRealtimeBinaryNormalizer.NormalizedFeed feed) {
        String scheduleFeed = SCHEDULE_FEEDS.get(feed.feedKey());
        if (scheduleFeed == null) return feed;

        SnapshotIndex index;
        try {
            index = activeIndex();
        } catch (RuntimeException exception) {
            log.warn(
                    "event=timetable_realtime_join_index_unavailable feed={} failureType={}",
                    feed.feedKey(),
                    exception.getClass().getSimpleName());
            return feed;
        }

        ArrayNode updates = joinRecords(feed.tripUpdates(), scheduleFeed, index, true);
        ArrayNode vehicles = joinRecords(feed.vehiclePositions(), scheduleFeed, index, false);
        return new GtfsRealtimeBinaryNormalizer.NormalizedFeed(
                feed.feedKey(),
                feed.schemaKey(),
                feed.protocolVersion(),
                feed.incrementality(),
                feed.providerGeneratedAt(),
                feed.retrievedAt(),
                updates,
                vehicles);
    }

    private synchronized SnapshotIndex activeIndex() {
        String activeVersion =
                manifestRepository
                        .findById("active")
                        .map(document -> document.getSnapshotVersion())
                        .orElse(null);
        if (activeVersion == null || activeVersion.isBlank()) return SnapshotIndex.empty();
        SnapshotIndex current = cachedIndex;
        if (current != null && current.snapshotVersion().equals(activeVersion)) return current;

        Map<TripKey, Set<TripRef>> trips = new HashMap<>();
        Map<CallKey, Set<String>> calls = new HashMap<>();
        for (TimetablePackageDocument document :
                packageRepository.findAllBySnapshotVersionAndOffering(activeVersion, "ALL")) {
            JsonNode root = readPackage(document);
            for (JsonNode trip : root.path("trips")) {
                String id = text(trip, "id");
                String providerId = text(trip, "providerId");
                String routeId = text(trip, "routeId");
                String serviceDate = text(trip, "serviceDate");
                if (id == null || providerId == null || routeId == null) continue;
                String feedKey = text(trip, "feedKey");
                if (feedKey == null) feedKey = feedKeyFromNamespacedId(id);
                if (feedKey == null) continue;
                TripRef ref = new TripRef(id, routeId, serviceDate);
                trips.computeIfAbsent(new TripKey(feedKey, providerId), ignored -> new HashSet<>())
                        .add(ref);
            }
            for (JsonNode call : root.path("calls")) {
                String tripId = text(call, "tripId");
                String stopId = text(call, "stopId");
                Integer sequence = integer(call, "sequence");
                if (tripId == null || stopId == null || sequence == null) continue;
                String feedKey = feedKeyFromNamespacedId(stopId);
                if (feedKey == null) continue;
                String providerStopId = stopId.substring(feedKey.length() + 1);
                calls.computeIfAbsent(
                                new CallKey(tripId, providerStopId, sequence),
                                ignored -> new HashSet<>())
                        .add(stopId);
            }
        }
        SnapshotIndex built = new SnapshotIndex(activeVersion, trips, calls);
        cachedIndex = built;
        log.info(
                "event=timetable_realtime_join_index_built snapshotVersion={} tripKeys={}"
                        + " callKeys={}",
                activeVersion,
                trips.size(),
                calls.size());
        return built;
    }

    private JsonNode readPackage(TimetablePackageDocument document) {
        try (GZIPInputStream gzip =
                new GZIPInputStream(new ByteArrayInputStream(document.getPayload()))) {
            return objectMapper.readTree(gzip);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Published timetable package cannot be read", exception);
        }
    }

    private ArrayNode joinRecords(
            ArrayNode source, String scheduleFeed, SnapshotIndex index, boolean tripUpdates) {
        ArrayNode result = objectMapper.createArrayNode();
        for (JsonNode node : source) {
            ObjectNode record = node.deepCopy();
            List<TripRef> candidates = candidates(record, scheduleFeed, index);
            if (candidates.size() == 1) {
                TripRef match = candidates.getFirst();
                record.put("matchStatus", "MATCHED");
                record.put("scheduleTripId", match.id());
                record.put("scheduleRouteId", match.routeId());
                record.put("scheduleSnapshotVersion", index.snapshotVersion());
                if (tripUpdates) joinStopUpdates(record, match, scheduleFeed, index);
                else joinVehicleStop(record, match, scheduleFeed, index);
            } else {
                record.put("matchStatus", candidates.isEmpty() ? "UNMATCHED" : "AMBIGUOUS");
                record.remove("scheduleTripId");
                record.remove("scheduleRouteId");
                if (candidates.size() > 1) {
                    record.put("scheduleSnapshotVersion", index.snapshotVersion());
                    ArrayNode candidateIds = record.putArray("scheduleTripCandidates");
                    candidates.stream().map(TripRef::id).sorted().forEach(candidateIds::add);
                }
            }
            result.add(record);
        }
        return result;
    }

    private List<TripRef> candidates(JsonNode record, String scheduleFeed, SnapshotIndex index) {
        String providerId = text(record, "tripId");
        if (providerId == null) return List.of();
        List<TripRef> matching =
                new ArrayList<>(
                        index.trips()
                                .getOrDefault(new TripKey(scheduleFeed, providerId), Set.of()));
        String routeId = text(record, "routeId");
        String startDate = text(record, "startDate");
        if (routeId != null) {
            String expected = scheduleFeed + ":" + routeId;
            matching.removeIf(candidate -> !candidate.routeId().equals(expected));
        }
        if (startDate != null) {
            matching.removeIf(candidate -> !startDate.equals(candidate.serviceDate()));
        }
        return matching;
    }

    private void joinStopUpdates(
            ObjectNode record, TripRef trip, String scheduleFeed, SnapshotIndex index) {
        JsonNode updates = record.path("stopUpdates");
        if (!(updates instanceof ArrayNode array)) return;
        for (JsonNode node : array) {
            if (!(node instanceof ObjectNode stop)) continue;
            String providerStopId = text(stop, "stopId");
            Integer sequence = integer(stop, "stopSequence");
            List<String> matched = new ArrayList<>();
            if (providerStopId != null && sequence != null) {
                matched.addAll(
                        index.calls()
                                .getOrDefault(
                                        new CallKey(trip.id(), providerStopId, sequence),
                                        Set.of()));
            }
            matched.removeIf(id -> !id.startsWith(scheduleFeed + ":"));
            if (matched.size() == 1) {
                stop.put("matchStatus", "MATCHED");
                stop.put("scheduleStopId", matched.getFirst());
            } else {
                stop.put("matchStatus", matched.isEmpty() ? "UNMATCHED" : "AMBIGUOUS");
                stop.remove("scheduleStopId");
            }
        }
    }

    private void joinVehicleStop(
            ObjectNode record, TripRef trip, String scheduleFeed, SnapshotIndex index) {
        String stopId = text(record, "stopId");
        Integer sequence = integer(record, "stopSequence");
        if (stopId == null || sequence == null) return;
        Set<String> matches =
                index.calls().getOrDefault(new CallKey(trip.id(), stopId, sequence), Set.of());
        List<String> scoped =
                matches.stream().filter(id -> id.startsWith(scheduleFeed + ":")).toList();
        if (scoped.size() == 1) {
            record.put("stopMatchStatus", "MATCHED");
            record.put("scheduleStopId", scoped.getFirst());
        } else {
            record.put("stopMatchStatus", scoped.isEmpty() ? "UNMATCHED" : "AMBIGUOUS");
        }
    }

    private String feedKeyFromNamespacedId(String id) {
        int separator = id.indexOf(':');
        return separator > 0 ? id.substring(0, separator) : null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isValueNode() && !value.asText().isBlank()
                ? value.asText()
                : null;
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.canConvertToInt() ? value.intValue() : null;
    }

    private record TripKey(String feedKey, String providerId) {}

    private record CallKey(String tripId, String providerStopId, int sequence) {}

    private record TripRef(String id, String routeId, String serviceDate) {}

    private record SnapshotIndex(
            String snapshotVersion,
            Map<TripKey, Set<TripRef>> trips,
            Map<CallKey, Set<String>> calls) {
        private static SnapshotIndex empty() {
            return new SnapshotIndex("", Map.of(), Map.of());
        }
    }
}
