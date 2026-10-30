/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import com.example.metro_parking_web_service.timetable.api.config.TimetableSnapshotProperties;
import com.example.metro_parking_web_service.timetable.api.document.TimetablePackageDocument;
import com.example.metro_parking_web_service.timetable.api.service.TimetableSnapshotPublisher.SnapshotCandidate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimetableSnapshotBuildService {

    private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");
    private static final String TRAINS = "SYDNEY_TRAINS";
    private static final String METRO = "SYDNEY_METRO";

    private final GtfsDatePackageBuilder packageBuilder;
    private final TimetableSnapshotPublisher publisher;
    private final TimetableSnapshotProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void buildAndPublish() throws IOException {
        buildAndPublish(
                properties.sydneyTrainsZip(),
                properties.sydneyTrainsZip().getFileName().toString(),
                properties.metroZip(),
                properties.metroZip().getFileName().toString(),
                properties.replacementBusZip(),
                properties.replacementBusZip().getFileName().toString(),
                properties.pcbcZip(),
                properties.pcbcZip().getFileName().toString());
    }

    public void buildAndPublish(
            Path trainsZip, String trainsRevision, Path metroZip, String metroRevision)
            throws IOException {
        buildAndPublish(trainsZip, trainsRevision, metroZip, metroRevision, null, null, null, null);
    }

    public void buildAndPublish(
            Path trainsZip,
            String trainsRevision,
            Path metroZip,
            String metroRevision,
            Path replacementBusZip,
            String replacementBusRevision,
            Path pcbcZip,
            String pcbcRevision)
            throws IOException {
        buildAndPublishForDate(
                trainsZip,
                trainsRevision,
                metroZip,
                metroRevision,
                replacementBusZip,
                replacementBusRevision,
                pcbcZip,
                pcbcRevision,
                LocalDate.now(SYDNEY));
    }

    void buildAndPublishForDate(
            Path trainsZip,
            String trainsRevision,
            Path metroZip,
            String metroRevision,
            Path replacementBusZip,
            String replacementBusRevision,
            Path pcbcZip,
            String pcbcRevision,
            LocalDate today)
            throws IOException {
        LocalDate from = today.minusDays(1);
        LocalDate through = today.plusDays(6);
        String snapshotVersion =
                Instant.now().toString().replace(":", "").replace("-", "")
                        + "-"
                        + UUID.randomUUID().toString().substring(0, 8);
        List<TimetablePackageDocument> packageDocuments = new ArrayList<>();
        List<PackageEntry> manifestEntries = new ArrayList<>();

        for (LocalDate date = from; !date.isAfter(through); date = date.plusDays(1)) {
            ObjectNode trains =
                    packageBuilder.build(trainsZip, "sydneytrains", TRAINS, date, trainsRevision);
            ObjectNode metro = packageBuilder.build(metroZip, "metro", METRO, date, metroRevision);
            int primaryTrainTripCount = trains.withArray("trips").size();
            if (replacementBusZip != null) {
                appendSupportingSchedules(
                        trains,
                        replacementBusZip,
                        replacementBusRevision,
                        date,
                        SupportingSchedulePolicy.routes().stream()
                                .filter(route -> route.feedKey().equals("replacementbus"))
                                .toList());
            }
            if (pcbcZip != null) {
                appendSupportingSchedules(
                        trains,
                        pcbcZip,
                        pcbcRevision,
                        date,
                        SupportingSchedulePolicy.routes().stream()
                                .filter(route -> route.feedKey().equals("pcbc"))
                                .toList());
            }
            ObjectNode all = combine(date, snapshotVersion, trains, metro);
            trains.put("packageVersion", snapshotVersion);
            metro.put("packageVersion", snapshotVersion);
            all.put("packageVersion", snapshotVersion);
            for (ObjectNode item : List.of(trains, metro, all)) {
                item.put("generatedAt", Instant.now().toString());
            }

            packageDocuments.add(toDocument(snapshotVersion, date, TRAINS, trains));
            packageDocuments.add(toDocument(snapshotVersion, date, METRO, metro));
            packageDocuments.add(toDocument(snapshotVersion, date, "ALL", all));
            manifestEntries.add(new PackageEntry(date));
            log.info(
                    "event=timetable_package_built date={} trainsTrips={} metroTrips={}"
                            + " supportingTrips={} combinedGzipBytes={}",
                    date,
                    trains.withArray("trips").size(),
                    metro.withArray("trips").size(),
                    trains.withArray("trips").size() - primaryTrainTripCount,
                    packageDocuments.get(packageDocuments.size() - 1).getPayload().length);
        }

        ObjectNode manifest =
                manifest(
                        snapshotVersion,
                        from,
                        through,
                        manifestEntries,
                        trainsRevision,
                        metroRevision,
                        replacementBusRevision,
                        pcbcRevision);
        String manifestEtag = '"' + sha256(serialize(manifest)) + '"';
        publisher.publish(
                new SnapshotCandidate(snapshotVersion, manifestEtag, manifest, packageDocuments));
        log.info(
                "event=timetable_snapshot_published version={} from={} through={} packages={}",
                snapshotVersion,
                from,
                through,
                packageDocuments.size());
    }

    private ObjectNode combine(
            LocalDate date, String version, ObjectNode trains, ObjectNode metro) {
        ObjectNode combined = objectMapper.createObjectNode();
        combined.put("schemaVersion", "1");
        combined.put("packageVersion", version);
        combined.put("travelDate", date.toString());
        combined.put("timezone", SYDNEY.getId());
        combined.put("availability", "AVAILABLE");
        combined.put("generatedAt", Instant.now().toString());
        addAll(
                combined.putArray("offerings"),
                trains.withArray("offerings"),
                metro.withArray("offerings"));
        addAll(
                combined.putArray("offeringStatuses"),
                trains.withArray("offeringStatuses"),
                metro.withArray("offeringStatuses"));
        addAll(
                combined.putArray("sources"),
                trains.withArray("sources"),
                metro.withArray("sources"));
        addAll(combined.putArray("stops"), trains.withArray("stops"), metro.withArray("stops"));
        addAll(combined.putArray("routes"), trains.withArray("routes"), metro.withArray("routes"));
        ArrayNode combinedTrips = combined.putArray("trips");
        combinedTrips.addAll(trains.withArray("trips"));
        int callOffset = trains.withArray("calls").size();
        for (var metroTrip : metro.withArray("trips")) {
            ObjectNode shiftedTrip = metroTrip.deepCopy();
            shiftedTrip.put(
                    "stopCallStart", shiftedTrip.path("stopCallStart").asInt() + callOffset);
            combinedTrips.add(shiftedTrip);
        }
        addAll(combined.putArray("calls"), trains.withArray("calls"), metro.withArray("calls"));
        addAll(
                combined.putArray("interchanges"),
                trains.withArray("interchanges"),
                metro.withArray("interchanges"));
        return combined;
    }

    private void appendSupportingSchedules(
            ObjectNode target,
            Path zipPath,
            String providerRevision,
            LocalDate date,
            List<SupportingSchedulePolicy.Route> policies)
            throws IOException {
        for (SupportingSchedulePolicy.Route policy : policies) {
            ObjectNode supporting =
                    packageBuilder.buildSupporting(zipPath, TRAINS, date, providerRevision, policy);
            int callOffset = target.withArray("calls").size();
            ArrayNode supportingTrips = supporting.withArray("trips");
            for (var item : supportingTrips) {
                ObjectNode shiftedTrip = ((ObjectNode) item).deepCopy();
                shiftedTrip.put(
                        "stopCallStart", shiftedTrip.path("stopCallStart").asInt() + callOffset);
                target.withArray("trips").add(shiftedTrip);
            }
            target.withArray("calls").addAll(supporting.withArray("calls"));
            appendUniqueById(target.withArray("routes"), supporting.withArray("routes"));
            appendUniqueById(target.withArray("stops"), supporting.withArray("stops"));
            target.withArray("sources").addAll(supporting.withArray("sources"));
            Set<String> stopIds = new java.util.HashSet<>();
            for (var stop : target.withArray("stops")) {
                stopIds.add(stop.path("id").asText());
            }
            for (var interchange : supporting.withArray("interchanges")) {
                if (stopIds.contains(interchange.path("fromStopId").asText())
                        && stopIds.contains(interchange.path("toStopId").asText())) {
                    target.withArray("interchanges").add(interchange.deepCopy());
                }
            }
        }
    }

    private void appendUniqueById(ArrayNode target, ArrayNode additions) {
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (var item : target) existing.add(item.path("id").asText());
        for (var item : additions) {
            String id = item.path("id").asText();
            if (existing.add(id)) target.add(item.deepCopy());
        }
    }

    private void addAll(ArrayNode target, ArrayNode... sources) {
        for (ArrayNode source : sources) target.addAll(source);
    }

    private TimetablePackageDocument toDocument(
            String version, LocalDate date, String offering, ObjectNode packageBody)
            throws IOException {
        byte[] compressed = gzip(serialize(packageBody));
        if (compressed.length > properties.maximumCompressedPackageBytes()) {
            throw new IllegalStateException(
                    "Timetable package exceeded compressed-size target: "
                            + date
                            + " "
                            + offering
                            + " bytes="
                            + compressed.length);
        }
        TimetablePackageDocument document = new TimetablePackageDocument();
        document.setSnapshotVersion(version);
        document.setTravelDate(date);
        document.setOffering(offering);
        document.setId(TimetableSnapshotPublisher.packageId(document));
        document.setEtag('"' + sha256(compressed) + '"');
        document.setContentEncoding("gzip");
        document.setGeneratedAt(Instant.now());
        document.setPayload(compressed);
        return document;
    }

    private ObjectNode manifest(
            String version,
            LocalDate from,
            LocalDate through,
            List<PackageEntry> packages,
            String trainsRevision,
            String metroRevision,
            String replacementBusRevision,
            String pcbcRevision) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("schemaVersion", "1");
        body.put("snapshotVersion", version);
        body.put("generatedAt", Instant.now().toString());
        body.put("timezone", SYDNEY.getId());
        body.putObject("travelDateWindow")
                .put("from", from.toString())
                .put("through", through.toString());
        ArrayNode offerings = body.putArray("offerings");
        offerings.add(
                manifestOffering(
                        TRAINS,
                        "STATION",
                        packages,
                        true,
                        version,
                        trainsRevision,
                        replacementBusRevision,
                        pcbcRevision));
        offerings.add(
                manifestOffering(
                        METRO, "STATION", packages, false, version, metroRevision, null, null));
        return body;
    }

    private ObjectNode manifestOffering(
            String key,
            String facet,
            List<PackageEntry> packages,
            boolean trains,
            String version,
            String providerRevision,
            String replacementBusRevision,
            String pcbcRevision) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("key", key);
        item.put("state", "AVAILABLE");
        ArrayNode dates = item.putArray("coveredDates");
        ObjectNode versions = item.putObject("packageVersions");
        ArrayNode sources = item.putArray("sources");
        for (PackageEntry entry : packages) {
            dates.add(entry.date().toString());
            versions.put(entry.date().toString(), version);
        }
        item.putArray("minimumFacets").add(facet);
        item.putArray("supportedFacets").add(facet);
        addManifestSource(
                sources, key, trains ? "sydneytrains" : "metro", "PRIMARY", providerRevision);
        if (trains && replacementBusRevision != null) {
            addManifestSource(sources, key, "replacementbus", "SUPPORTING", replacementBusRevision);
        }
        if (trains && pcbcRevision != null) {
            addManifestSource(sources, key, "pcbc", "SUPPORTING", pcbcRevision);
        }
        return item;
    }

    private void addManifestSource(
            ArrayNode sources,
            String offering,
            String feedKey,
            String role,
            String providerRevision) {
        ObjectNode source = sources.addObject();
        source.put("feedKey", feedKey);
        source.put("role", role);
        source.put("status", "CURRENT");
        source.put("associatedOffering", offering);
        source.put("providerRevision", providerRevision);
        source.put("lastSuccessfulAt", Instant.now().toString());
    }

    private byte[] serialize(ObjectNode body) throws JsonProcessingException {
        return objectMapper.writeValueAsBytes(body);
    }

    private byte[] gzip(byte[] bytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
            gzip.write(bytes);
        }
        return buffer.toByteArray();
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record PackageEntry(LocalDate date) {}
}
