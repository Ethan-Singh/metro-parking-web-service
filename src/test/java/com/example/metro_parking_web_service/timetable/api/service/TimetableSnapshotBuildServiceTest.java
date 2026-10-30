/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.timetable.api.config.TimetableSnapshotProperties;
import com.example.metro_parking_web_service.timetable.api.document.TimetablePackageDocument;
import com.example.metro_parking_web_service.timetable.api.service.TimetableSnapshotPublisher.SnapshotCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimetableSnapshotBuildServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private GtfsDatePackageBuilder packageBuilder;
    @Mock private TimetableSnapshotPublisher publisher;

    private TimetableSnapshotBuildService buildService;

    @BeforeEach
    void setUp() {
        buildService =
                new TimetableSnapshotBuildService(
                        packageBuilder,
                        publisher,
                        new TimetableSnapshotProperties(
                                false, Path.of("trains.zip"), Path.of("metro.zip"), 2_097_152));
    }

    @Test
    void buildsAllWindowDatesAndCombinedPackagesThenPublishesOnce() throws Exception {
        when(packageBuilder.build(any(), anyString(), anyString(), any(), anyString()))
                .thenAnswer(
                        invocation ->
                                packageBody(
                                        invocation.getArgument(2),
                                        invocation.getArgument(3),
                                        invocation.getArgument(2).equals("SYDNEY_METRO")));

        buildService.buildAndPublish(
                Path.of("trains.zip"), "train-r1", Path.of("metro.zip"), "metro-r1");

        ArgumentCaptor<SnapshotCandidate> candidateCaptor =
                ArgumentCaptor.forClass(SnapshotCandidate.class);
        verify(publisher).publish(candidateCaptor.capture());
        SnapshotCandidate candidate = candidateCaptor.getValue();
        assertThat(candidate.packages()).hasSize(24);
        assertThat(candidate.manifestEtag()).startsWith("\"").endsWith("\"");
        assertThat(candidate.manifest().path("timezone").asText()).isEqualTo("Australia/Sydney");
        assertThat(candidate.manifest().path("travelDateWindow").path("through").asText())
                .isEqualTo(LocalDate.now(TimetableReadService.SYDNEY_ZONE).plusDays(6).toString());

        TimetablePackageDocument all =
                candidate.packages().stream()
                        .filter(document -> "ALL".equals(document.getOffering()))
                        .findFirst()
                        .orElseThrow();
        assertThat(all.getPayload()).isNotEmpty();
        assertThat(all.getContentEncoding()).isEqualTo("gzip");
        assertThat(all.getEtag()).startsWith("\"").endsWith("\"");
        verify(packageBuilder, times(16))
                .build(any(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void refusesSnapshotWhenCompressedDatePackageExceedsConfiguredLimit() throws Exception {
        buildService =
                new TimetableSnapshotBuildService(
                        packageBuilder,
                        publisher,
                        new TimetableSnapshotProperties(
                                false, Path.of("trains.zip"), Path.of("metro.zip"), 1));
        when(packageBuilder.build(any(), anyString(), anyString(), any(), anyString()))
                .thenReturn(packageBody("SYDNEY_TRAINS", LocalDate.now(), false));

        assertThatThrownBy(
                        () ->
                                buildService.buildAndPublish(
                                        Path.of("trains.zip"),
                                        "train-r1",
                                        Path.of("metro.zip"),
                                        "metro-r1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timetable package exceeded compressed-size target");
        verify(publisher, org.mockito.Mockito.never()).publish(any());
    }

    private ObjectNode packageBody(String offering, LocalDate date, boolean metro) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("travelDate", date.toString());
        body.putArray("offerings").add(offering);
        body.putArray("offeringStatuses").add(MAPPER.createObjectNode().put("key", offering));
        body.putArray("sources").add(MAPPER.createObjectNode().put("feedKey", offering));
        body.putArray("stops").add(MAPPER.createObjectNode().put("id", offering + "-stop"));
        body.putArray("routes").add(MAPPER.createObjectNode().put("id", offering + "-route"));
        body.putArray("calls").add(MAPPER.createObjectNode().put("sequence", 1));
        body.putArray("trips")
                .add(
                        MAPPER.createObjectNode()
                                .put("id", offering + "-trip")
                                .put("stopCallStart", metro ? 0 : 0)
                                .put("stopCallCount", 1));
        return body;
    }
}
