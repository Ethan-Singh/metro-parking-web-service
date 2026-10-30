/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.timetable.api.document.TimetablePackageDocument;
import com.example.metro_parking_web_service.timetable.api.repository.TimetableManifestRepository;
import com.example.metro_parking_web_service.timetable.api.repository.TimetablePackageRepository;
import com.example.metro_parking_web_service.timetable.api.service.TimetableSnapshotPublisher.SnapshotCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimetableSnapshotPublisherTest {

    @Mock private TimetablePackageRepository packageRepository;
    @Mock private TimetableManifestRepository manifestRepository;

    private TimetableSnapshotPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new TimetableSnapshotPublisher(packageRepository, manifestRepository);
        lenient()
                .when(manifestRepository.findByIdStartingWithOrderByPublishedAtDesc("snapshot:"))
                .thenReturn(List.of());
    }

    @Test
    void rejectsPackageFromDifferentSnapshotBeforeSwitchingActiveManifest() throws Exception {
        TimetablePackageDocument document =
                packageDocument("other-version", "gzip", new byte[] {1});

        assertThatThrownBy(() -> publisher.publish(candidate("snapshot-1", document)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Package belongs to a different snapshot version");

        verify(manifestRepository, never()).save(any());
    }

    @Test
    void rejectsUncompressedPackage() throws Exception {
        TimetablePackageDocument document =
                packageDocument("snapshot-1", "identity", new byte[] {1});

        assertThatThrownBy(() -> publisher.publish(candidate("snapshot-1", document)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Published package payload must be gzip-compressed");

        verify(manifestRepository, never()).save(any());
    }

    @Test
    void rejectsEmptySnapshotVersion() {
        assertThatThrownBy(
                        () ->
                                publisher.publish(
                                        new SnapshotCandidate(" ", "etag", null, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Snapshot version is required");
    }

    @Test
    void rejectsSnapshotWithoutPackages() {
        assertThatThrownBy(
                        () ->
                                publisher.publish(
                                        new SnapshotCandidate(
                                                "snapshot-1",
                                                "etag",
                                                new ObjectMapper().createObjectNode(),
                                                List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A snapshot must contain at least one date package");
    }

    @Test
    void rejectsSnapshotWithoutManifest() throws Exception {
        TimetablePackageDocument document = packageDocument("snapshot-1", "gzip", new byte[] {1});
        assertThatThrownBy(
                        () ->
                                publisher.publish(
                                        new SnapshotCandidate(
                                                "snapshot-1", "etag", null, List.of(document))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A snapshot manifest is required");
    }

    @Test
    void rejectsEmptyPackagePayload() throws Exception {
        TimetablePackageDocument document = packageDocument("snapshot-1", "gzip", new byte[0]);

        assertThatThrownBy(() -> publisher.publish(candidate("snapshot-1", document)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Empty package payload cannot be published");
    }

    @Test
    void keepsOnlyTheFiveMostRecentSnapshotsIncludingTheActiveOne() throws Exception {
        List<com.example.metro_parking_web_service.timetable.api.document.TimetableManifestDocument>
                manifests =
                        List.of(
                                manifest("snapshot-7", 7),
                                manifest("snapshot-6", 6),
                                manifest("snapshot-5", 5),
                                manifest("snapshot-4", 4),
                                manifest("snapshot-3", 3),
                                manifest("snapshot-2", 2),
                                manifest("snapshot-1", 1));
        when(manifestRepository.findById("active"))
                .thenReturn(Optional.of(manifest("snapshot-7", 7)));
        when(manifestRepository.findByIdStartingWithOrderByPublishedAtDesc("snapshot:"))
                .thenReturn(manifests);

        publisher.publish(
                candidate("snapshot-7", packageDocument("snapshot-7", "gzip", new byte[] {1})));

        verify(packageRepository)
                .deleteBySnapshotVersionNotIn(
                        org.mockito.ArgumentMatchers.argThat(
                                versions ->
                                        versions.equals(
                                                java.util.Set.of(
                                                        "snapshot-7",
                                                        "snapshot-6",
                                                        "snapshot-5",
                                                        "snapshot-4",
                                                        "snapshot-3"))));
        verify(manifestRepository).deleteById("snapshot:snapshot-2");
        verify(manifestRepository).deleteById("snapshot:snapshot-1");
    }

    private com.example.metro_parking_web_service.timetable.api.document.TimetableManifestDocument
            manifest(String version, int age) {
        var document =
                new com.example.metro_parking_web_service.timetable.api.document
                        .TimetableManifestDocument();
        document.setId("snapshot:" + version);
        document.setSnapshotVersion(version);
        document.setPublishedAt(Instant.parse("2026-10-30T00:00:00Z").plusSeconds(age));
        return document;
    }

    private SnapshotCandidate candidate(String version, TimetablePackageDocument document)
            throws Exception {
        return new SnapshotCandidate(
                version,
                "\"manifest\"",
                new ObjectMapper().createObjectNode().put("snapshotVersion", version),
                List.of(document));
    }

    private TimetablePackageDocument packageDocument(
            String version, String encoding, byte[] payload) {
        TimetablePackageDocument document = new TimetablePackageDocument();
        document.setSnapshotVersion(version);
        document.setTravelDate(LocalDate.of(2026, 10, 30));
        document.setOffering("ALL");
        document.setContentEncoding(encoding);
        document.setPayload(payload);
        return document;
    }
}
