/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.timetable.realtime.document.RealtimeOverlayDocument;
import com.example.metro_parking_web_service.timetable.realtime.repository.RealtimeOverlayRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealtimeOverlayReadServiceTest {

    @Mock private RealtimeOverlayRepository repository;

    @Test
    void returnsThePublishedOverlayAndItsEtag() {
        RealtimeOverlayDocument document = new RealtimeOverlayDocument();
        document.setPayloadJson("{\"overlayVersion\":\"v1\"}");
        document.setEtag("\"etag-v1\"");
        when(repository.findById("active")).thenReturn(Optional.of(document));

        var response = new RealtimeOverlayReadService(repository).getOverlay();

        assertThat(new String(response.body(), StandardCharsets.UTF_8))
                .isEqualTo("{\"overlayVersion\":\"v1\"}");
        assertThat(response.etag()).isEqualTo("\"etag-v1\"");
    }

    @Test
    void returnsAnEmptyVersionedOverlayWhenNoValidSnapshotExists() throws Exception {
        RealtimeOverlayDocument document = new RealtimeOverlayDocument();
        document.setPayloadJson(null);
        when(repository.findById("active")).thenReturn(Optional.of(document));

        var response = new RealtimeOverlayReadService(repository).getOverlay();
        var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());

        assertThat(body.path("overlayVersion").asText()).isEqualTo("unavailable");
        assertThat(body.path("sources")).isEmpty();
        assertThat(body.path("alerts")).isEmpty();
        assertThat(body.path("tripUpdates")).isEmpty();
        assertThat(body.path("vehiclePositions")).isEmpty();
        assertThat(response.etag()).startsWith("\"").endsWith("\"");
    }
}
