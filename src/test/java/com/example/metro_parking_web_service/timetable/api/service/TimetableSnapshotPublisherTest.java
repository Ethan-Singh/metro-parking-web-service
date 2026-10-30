/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.timetable.api.document.TimetablePackageDocument;
import com.example.metro_parking_web_service.timetable.api.repository.TimetableManifestRepository;
import com.example.metro_parking_web_service.timetable.api.repository.TimetablePackageRepository;
import com.example.metro_parking_web_service.timetable.api.service.TimetableSnapshotPublisher.SnapshotCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    }

    @Test
    void rejectsPackageFromDifferentSnapshotBeforeSwitchingActiveManifest() throws Exception {
        when(manifestRepository.findById("active")).thenReturn(Optional.empty());
        TimetablePackageDocument document =
                packageDocument("other-version", "gzip", new byte[] {1});

        assertThatThrownBy(() -> publisher.publish(candidate("snapshot-1", document)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Package belongs to a different snapshot version");

        verify(manifestRepository, never()).save(any());
    }

    @Test
    void rejectsUncompressedPackage() throws Exception {
        when(manifestRepository.findById("active")).thenReturn(Optional.empty());
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
        when(manifestRepository.findById("active")).thenReturn(Optional.empty());
        TimetablePackageDocument document = packageDocument("snapshot-1", "gzip", new byte[0]);

        assertThatThrownBy(() -> publisher.publish(candidate("snapshot-1", document)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Empty package payload cannot be published");
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
