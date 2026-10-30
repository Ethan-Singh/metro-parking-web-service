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

    public void stage(TimetablePackageDocument packageDocument) {
        validatePackage(
                packageDocument,
                packageDocument == null ? null : packageDocument.getSnapshotVersion());
        packageDocument.setId(packageId(packageDocument));
        packageRepository.save(packageDocument);
    }

    public synchronized void publishStaged(
            String snapshotVersion,
            String manifestEtag,
            ObjectNode manifest,
            long expectedPackages) {
        if (snapshotVersion == null || snapshotVersion.isBlank()) {
            throw new IllegalArgumentException("Snapshot version is required");
        }
        if (manifest == null) throw new IllegalArgumentException("A snapshot manifest is required");
        if (expectedPackages <= 0
                || packageRepository.countBySnapshotVersion(snapshotVersion) != expectedPackages) {
            throw new IllegalStateException("Staged timetable snapshot is incomplete");
        }
        activate(snapshotVersion, manifestEtag, manifest);
    }

    public void discardStaged(String snapshotVersion) {
        boolean active =
                manifestRepository
                        .findById("active")
                        .map(document -> snapshotVersion.equals(document.getSnapshotVersion()))
                        .orElse(false);
        if (!active) packageRepository.deleteBySnapshotVersion(snapshotVersion);
    }

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
            validatePackage(packageDocument, candidate.snapshotVersion());
            packageDocument.setId(packageId(packageDocument));
            packageRepository.save(packageDocument);
        }

        activate(candidate.snapshotVersion(), candidate.manifestEtag(), candidate.manifest());
    }

    private void activate(String snapshotVersion, String manifestEtag, ObjectNode manifestPayload) {

        TimetableManifestDocument manifest = new TimetableManifestDocument();
        manifest.setId("snapshot:" + snapshotVersion);
        manifest.setSnapshotVersion(snapshotVersion);
        manifest.setEtag(manifestEtag);
        manifest.setPublishedAt(Instant.now());
        manifest.setPayload(objectMapper.convertValue(manifestPayload, java.util.Map.class));
        manifestRepository.save(manifest);

        TimetableManifestDocument activeManifest = new TimetableManifestDocument();
        activeManifest.setId("active");
        activeManifest.setSnapshotVersion(snapshotVersion);
        activeManifest.setEtag(manifestEtag);
        activeManifest.setPublishedAt(manifest.getPublishedAt());
        activeManifest.setPayload(manifest.getPayload());
        manifestRepository.save(activeManifest);

        String activeVersion =
                manifestRepository
                        .findById("active")
                        .map(TimetableManifestDocument::getSnapshotVersion)
                        .orElse(snapshotVersion);
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

    private void validatePackage(TimetablePackageDocument packageDocument, String expectedVersion) {
        if (packageDocument == null
                || expectedVersion == null
                || !expectedVersion.equals(packageDocument.getSnapshotVersion())) {
            throw new IllegalArgumentException("Package belongs to a different snapshot version");
        }
        if (packageDocument.getPayload() == null || packageDocument.getPayload().length == 0) {
            throw new IllegalArgumentException("Empty package payload cannot be published");
        }
        if (!"gzip".equalsIgnoreCase(packageDocument.getContentEncoding())) {
            throw new IllegalArgumentException("Published package payload must be gzip-compressed");
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
