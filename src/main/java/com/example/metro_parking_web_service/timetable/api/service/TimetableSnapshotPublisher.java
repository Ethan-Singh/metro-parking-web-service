/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import com.example.metro_parking_web_service.timetable.api.document.TimetableManifestDocument;
import com.example.metro_parking_web_service.timetable.api.document.TimetablePackageDocument;
import com.example.metro_parking_web_service.timetable.api.repository.TimetableManifestRepository;
import com.example.metro_parking_web_service.timetable.api.repository.TimetablePackageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Publishes immutable package rows before switching the single active manifest pointer. */
@Service
@RequiredArgsConstructor
public class TimetableSnapshotPublisher {

    static final int MAX_RETAINED_SNAPSHOTS = 5;

    private final TimetablePackageRepository packageRepository;
    private final TimetableManifestRepository manifestRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public synchronized void publish(SnapshotCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (candidate.snapshotVersion() == null || candidate.snapshotVersion().isBlank()) {
            throw new IllegalArgumentException("Snapshot version is required");
        }
        if (candidate.packages() == null || candidate.packages().isEmpty()) {
            throw new IllegalArgumentException("A snapshot must contain at least one date package");
        }
        if (candidate.manifest() == null) {
            throw new IllegalArgumentException("A snapshot manifest is required");
        }

        for (TimetablePackageDocument packageDocument : candidate.packages()) {
            if (!candidate.snapshotVersion().equals(packageDocument.getSnapshotVersion())) {
                throw new IllegalArgumentException(
                        "Package belongs to a different snapshot version");
            }
            packageDocument.setId(packageId(packageDocument));
            if (packageDocument.getPayload() == null || packageDocument.getPayload().length == 0) {
                throw new IllegalArgumentException("Empty package payload cannot be published");
            }
            if (!"gzip".equalsIgnoreCase(packageDocument.getContentEncoding())) {
                throw new IllegalArgumentException(
                        "Published package payload must be gzip-compressed");
            }
            packageRepository.save(packageDocument);
        }

        TimetableManifestDocument manifest = new TimetableManifestDocument();
        manifest.setId("snapshot:" + candidate.snapshotVersion());
        manifest.setSnapshotVersion(candidate.snapshotVersion());
        manifest.setEtag(candidate.manifestEtag());
        manifest.setPublishedAt(Instant.now());
        manifest.setPayload(objectMapper.convertValue(candidate.manifest(), java.util.Map.class));
        manifestRepository.save(manifest);

        TimetableManifestDocument activeManifest = new TimetableManifestDocument();
        activeManifest.setId("active");
        activeManifest.setSnapshotVersion(candidate.snapshotVersion());
        activeManifest.setEtag(candidate.manifestEtag());
        activeManifest.setPublishedAt(manifest.getPublishedAt());
        activeManifest.setPayload(manifest.getPayload());
        manifestRepository.save(activeManifest);

        String activeVersion =
                manifestRepository
                        .findById("active")
                        .map(TimetableManifestDocument::getSnapshotVersion)
                        .orElse(candidate.snapshotVersion());
        List<TimetableManifestDocument> recentManifests =
                manifestRepository.findByIdStartingWithOrderByPublishedAtDesc("snapshot:");
        java.util.Set<String> retainedVersions = new java.util.LinkedHashSet<>();
        recentManifests.stream()
                .limit(MAX_RETAINED_SNAPSHOTS)
                .map(TimetableManifestDocument::getSnapshotVersion)
                .filter(Objects::nonNull)
                .forEach(retainedVersions::add);
        retainedVersions.add(activeVersion);
        while (retainedVersions.size() > MAX_RETAINED_SNAPSHOTS) {
            String oldest =
                    recentManifests.stream()
                            .map(TimetableManifestDocument::getSnapshotVersion)
                            .filter(version -> !version.equals(activeVersion))
                            .filter(retainedVersions::contains)
                            .reduce((first, second) -> second)
                            .orElse(null);
            if (oldest == null) break;
            retainedVersions.remove(oldest);
        }
        packageRepository.deleteBySnapshotVersionNotIn(retainedVersions);
        for (TimetableManifestDocument historicalManifest : recentManifests) {
            if (!retainedVersions.contains(historicalManifest.getSnapshotVersion())) {
                manifestRepository.deleteById(historicalManifest.getId());
            }
        }
    }

    public static String packageId(TimetablePackageDocument document) {
        return document.getSnapshotVersion()
                + "#"
                + document.getTravelDate()
                + "#"
                + (document.getOffering() == null ? "ALL" : document.getOffering());
    }

    public record SnapshotCandidate(
            String snapshotVersion,
            String manifestEtag,
            ObjectNode manifest,
            List<TimetablePackageDocument> packages) {}
}
