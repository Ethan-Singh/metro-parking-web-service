/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import com.example.metro_parking_web_service.timetable.api.document.TimetablePackageDocument;
import com.example.metro_parking_web_service.timetable.api.repository.TimetableManifestRepository;
import com.example.metro_parking_web_service.timetable.api.repository.TimetablePackageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TimetableReadService {

    public static final ZoneId SYDNEY_ZONE = ZoneId.of("Australia/Sydney");
    private static final Set<String> OFFERINGS =
            Set.of(
                    "SYDNEY_TRAINS",
                    "SYDNEY_METRO",
                    "SYDNEY_REGIONAL_BUSES",
                    "SYDNEY_FERRIES",
                    "SYDNEY_NEWCASTLE_LIGHT_RAIL",
                    "NSW_TRAINLINK_REGIONAL_TRAINS",
                    "NSW_TRAINLINK_REGIONAL_COACHES",
                    "SYDNEY_REGIONAL_SCHOOL_BUSES");
    private static final List<OfferingDefinition> OFFERING_DEFINITIONS =
            List.of(
                    new OfferingDefinition("SYDNEY_TRAINS", "AVAILABLE", List.of("STATION")),
                    new OfferingDefinition("SYDNEY_METRO", "AVAILABLE", List.of("STATION")),
                    new OfferingDefinition(
                            "SYDNEY_REGIONAL_BUSES",
                            "NOT_CONFIGURED",
                            List.of("ROUTE", "LOCALITY", "STOP")),
                    new OfferingDefinition("SYDNEY_FERRIES", "NOT_CONFIGURED", List.of("WHARF")),
                    new OfferingDefinition(
                            "SYDNEY_NEWCASTLE_LIGHT_RAIL", "NOT_CONFIGURED", List.of("STOP")),
                    new OfferingDefinition(
                            "NSW_TRAINLINK_REGIONAL_TRAINS", "NOT_CONFIGURED", List.of("STATION")),
                    new OfferingDefinition(
                            "NSW_TRAINLINK_REGIONAL_COACHES",
                            "NOT_CONFIGURED",
                            List.of("ROUTE", "LOCALITY", "STOP")),
                    new OfferingDefinition(
                            "SYDNEY_REGIONAL_SCHOOL_BUSES",
                            "NOT_CONFIGURED",
                            List.of("ROUTE", "LOCALITY", "STOP")));

    private final TimetableManifestRepository manifestRepository;
    private final TimetablePackageRepository packageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Representation getManifest() {
        return manifestRepository
                .findById("active")
                .filter(document -> document.getPayload() != null)
                .map(document -> jsonRepresentation(document.getPayload(), document.getEtag()))
                .orElseGet(this::emptyManifest);
    }

    public Representation getPackage(LocalDate travelDate, String offering) {
        if (offering != null && !OFFERINGS.contains(offering)) {
            throw new IllegalArgumentException("Unsupported timetable offering");
        }
        String packageKey = travelDate + "#" + (offering == null ? "ALL" : offering);
        return packageRepository
                .findById(packageKey)
                .map(this::storedPackage)
                .orElseGet(() -> emptyPackage(travelDate, offering));
    }

    private Representation storedPackage(TimetablePackageDocument document) {
        return new Representation(
                document.getPayload(),
                document.getEtag(),
                document.getContentEncoding() == null ? "gzip" : document.getContentEncoding());
    }

    private Representation jsonRepresentation(ObjectNode payload, String storedEtag) {
        byte[] bytes = serialize(payload);
        return new Representation(bytes, normalizeEtag(storedEtag, bytes), null);
    }

    private Representation emptyManifest() {
        LocalDate today = LocalDate.now(SYDNEY_ZONE);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("schemaVersion", "1");
        body.put("generatedAt", java.time.Instant.now().toString());
        body.put("timezone", SYDNEY_ZONE.getId());
        ObjectNode window = body.putObject("travelDateWindow");
        window.put("from", today.minusDays(1).toString());
        window.put("through", today.plusDays(6).toString());
        ArrayNode offerings = body.putArray("offerings");
        for (OfferingDefinition definition : OFFERING_DEFINITIONS) {
            ObjectNode item = offerings.addObject();
            item.put("key", definition.key());
            item.put(
                    "state",
                    definition.key().equals("SYDNEY_TRAINS")
                                    || definition.key().equals("SYDNEY_METRO")
                            ? "UNAVAILABLE"
                            : definition.unconfiguredState());
            item.putArray("coveredDates");
            ArrayNode minimum = item.putArray("minimumFacets");
            ArrayNode supported = item.putArray("supportedFacets");
            definition
                    .facets()
                    .forEach(
                            facet -> {
                                minimum.add(facet);
                                supported.add(facet);
                            });
            item.putObject("packageVersions");
            item.putArray("sources");
        }
        return jsonRepresentation(body, null);
    }

    private Representation emptyPackage(LocalDate travelDate, String offering) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("schemaVersion", "1");
        body.put("packageVersion", "unavailable");
        body.put("generatedAt", java.time.Instant.now().toString());
        body.put("travelDate", travelDate.toString());
        body.put("timezone", SYDNEY_ZONE.getId());
        body.put("availability", "UNAVAILABLE");
        ArrayNode packageOfferings = body.putArray("offerings");
        ArrayNode statuses = body.putArray("offeringStatuses");
        OFFERING_DEFINITIONS.stream()
                .filter(definition -> offering == null || definition.key().equals(offering))
                .forEach(
                        definition -> {
                            packageOfferings.add(definition.key());
                            ObjectNode status = statuses.addObject();
                            status.put("key", definition.key());
                            status.put("state", "UNAVAILABLE");
                            status.putArray("coveredDates");
                            status.putArray("minimumFacets")
                                    .addAll(
                                            definition.facets().stream()
                                                    .map(objectMapper.getNodeFactory()::textNode)
                                                    .toList());
                            status.putArray("supportedFacets")
                                    .addAll(
                                            definition.facets().stream()
                                                    .map(objectMapper.getNodeFactory()::textNode)
                                                    .toList());
                            status.putObject("packageVersions");
                            status.putArray("sources");
                        });
        body.putArray("sources");
        body.putArray("stops");
        body.putArray("routes");
        body.putArray("trips");
        body.putArray("calls");
        body.putArray("interchanges");
        return jsonRepresentation(body, null);
    }

    private byte[] serialize(ObjectNode payload) {
        try {
            return objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialize timetable representation", exception);
        }
    }

    private String normalizeEtag(String etag, byte[] bytes) {
        return etag == null || etag.isBlank() ? '"' + sha256(bytes) + '"' : etag;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record OfferingDefinition(String key, String unconfiguredState, List<String> facets) {}

    public record Representation(byte[] body, String etag, String contentEncoding) {
        public String bodyAsText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
