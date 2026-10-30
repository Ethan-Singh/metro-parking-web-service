/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.timetable.api.document.TimetableManifestDocument;
import com.example.metro_parking_web_service.timetable.api.document.TimetablePackageDocument;
import com.example.metro_parking_web_service.timetable.api.repository.TimetableManifestRepository;
import com.example.metro_parking_web_service.timetable.api.repository.TimetablePackageRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimetableReadServiceTest {

    @Mock private TimetableManifestRepository manifestRepository;
    @Mock private TimetablePackageRepository packageRepository;

    private TimetableReadService service;

    @BeforeEach
    void setUp() {
        service = new TimetableReadService(manifestRepository, packageRepository);
    }

    @Test
    void returnsEmptyManifestWithSupportedAndUnavailableOfferings() {
        when(manifestRepository.findById("active")).thenReturn(Optional.empty());

        var response = service.getManifest();

        assertThat(response.bodyAsText())
                .contains("Australia/Sydney", "SYDNEY_TRAINS", "UNAVAILABLE");
    }

    @Test
    void returnsUnavailablePackageForDateWithoutPublishedSnapshot() {
        when(manifestRepository.findById("active")).thenReturn(Optional.empty());
        LocalDate date = LocalDate.of(2026, 10, 30);

        var response = service.getPackage(date, "SYDNEY_TRAINS");

        assertThat(response.bodyAsText())
                .contains("2026-10-30", "UNAVAILABLE", "Australia/Sydney", "\"trips\":[]");
    }

    @Test
    void rejectsUnknownOffering() {
        assertThatThrownBy(() -> service.getPackage(LocalDate.of(2026, 10, 30), "UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported timetable offering");
    }

    @Test
    void servesPublishedCompressedPackageWithStoredEtag() {
        when(manifestRepository.findById("active")).thenReturn(Optional.of(manifest("snapshot-1")));
        TimetablePackageDocument document = new TimetablePackageDocument();
        document.setPayload(new byte[] {1, 2, 3});
        document.setEtag("\"package-etag\"");
        document.setContentEncoding("gzip");
        when(packageRepository.findById("snapshot-1#2026-10-30#SYDNEY_TRAINS"))
                .thenReturn(Optional.of(document));

        var response = service.getPackage(LocalDate.of(2026, 10, 30), "SYDNEY_TRAINS");

        assertThat(response.etag()).isEqualTo("\"package-etag\"");
        assertThat(response.contentEncoding()).isEqualTo("gzip");
        assertThat(response.body()).containsExactly(1, 2, 3);
    }

    private TimetableManifestDocument manifest(String version) {
        TimetableManifestDocument document = new TimetableManifestDocument();
        document.setId("active");
        document.setSnapshotVersion(version);
        document.setPayload(Map.of("snapshotVersion", version));
        return document;
    }
}
