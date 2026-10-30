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

    private final TimetablePackageRepository packageRepository;
    private final TimetableManifestRepository manifestRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void publish(SnapshotCandidate candidate) {
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

        String previousVersion =
                manifestRepository
                        .findById("active")
                        .map(document -> document.getSnapshotVersion())
                        .orElse(null);

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

        java.util.Set<String> retainedVersions = new java.util.HashSet<>();
        retainedVersions.add(candidate.snapshotVersion());
        if (previousVersion != null && !previousVersion.isBlank())
            retainedVersions.add(previousVersion);
        packageRepository.deleteBySnapshotVersionNotIn(retainedVersions);
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
