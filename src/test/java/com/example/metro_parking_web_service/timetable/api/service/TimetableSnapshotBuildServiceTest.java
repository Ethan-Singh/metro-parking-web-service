/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.timetable.api.config.TimetableSnapshotProperties;
import com.example.metro_parking_web_service.timetable.api.document.TimetablePackageDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;
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
                                false,
                                Path.of("trains.zip"),
                                Path.of("metro.zip"),
                                Path.of("replacementbus.zip"),
                                Path.of("pcbc.zip"),
                                2_097_152));
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

        ArgumentCaptor<TimetablePackageDocument> packageCaptor =
                ArgumentCaptor.forClass(TimetablePackageDocument.class);
        verify(publisher, times(24)).stage(packageCaptor.capture());
        ArgumentCaptor<ObjectNode> manifestCaptor = ArgumentCaptor.forClass(ObjectNode.class);
        verify(publisher)
                .publishStaged(
                        anyString(),
                        anyString(),
                        manifestCaptor.capture(),
                        org.mockito.ArgumentMatchers.eq(24L));
        assertThat(packageCaptor.getAllValues()).hasSize(24);
        assertThat(
                        packageCaptor.getAllValues().stream()
                                .filter(doc -> "ALL".equals(doc.getOffering()))
                                .findFirst()
                                .orElseThrow()
                                .getPayload())
                .isNotEmpty();
        assertThat(manifestCaptor.getValue().path("timezone").asText())
                .isEqualTo("Australia/Sydney");
        assertThat(manifestCaptor.getValue().path("travelDateWindow").path("through").asText())
                .isEqualTo(LocalDate.now(TimetableReadService.SYDNEY_ZONE).plusDays(6).toString());
        assertThat(packageCaptor.getAllValues())
                .allSatisfy(
                        document -> {
                            assertThat(document.getContentEncoding()).isEqualTo("gzip");
                            assertThat(document.getEtag()).startsWith("\"").endsWith("\"");
                        });
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
                                false,
                                Path.of("trains.zip"),
                                Path.of("metro.zip"),
                                Path.of("replacementbus.zip"),
                                Path.of("pcbc.zip"),
                                1));
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
        verify(publisher, org.mockito.Mockito.never())
                .publishStaged(anyString(), anyString(), any(), anyLong());
    }

    @Test
    void publishesReplacementBusAndPcbcAsSupportingTripsInTrainPackageOnly() throws Exception {
        GtfsDatePackageBuilder realBuilder = org.mockito.Mockito.spy(new GtfsDatePackageBuilder());
        doAnswer(
                        invocation ->
                                packageBody(
                                        invocation.getArgument(2),
                                        invocation.getArgument(3),
                                        invocation.getArgument(2).equals("SYDNEY_METRO")))
                .when(realBuilder)
                .build(any(), anyString(), anyString(), any(), anyString());
        buildService =
                new TimetableSnapshotBuildService(
                        realBuilder,
                        publisher,
                        new TimetableSnapshotProperties(
                                false,
                                Path.of("trains.zip"),
                                Path.of("metro.zip"),
                                Path.of("replacementbus.zip"),
                                Path.of("pcbc.zip"),
                                2_097_152));

        buildService.buildAndPublishForDate(
                Path.of("trains.zip"),
                "train-r1",
                Path.of("metro.zip"),
                "metro-r1",
                Path.of(
                        "docs/apis/v1-gtfs/schedules/buses/replacementbus/"
                                + "ReplacementBus_GTFS_PROD_20261028091300.zip"),
                "replacement-r1",
                Path.of("docs/apis/v1-gtfs/schedules/buses/pcbc/PCBC_GTFS_PROD_20261028085700.zip"),
                "pcbc-r1",
                LocalDate.of(2026, 10, 26));

        ArgumentCaptor<TimetablePackageDocument> packageCaptor =
                ArgumentCaptor.forClass(TimetablePackageDocument.class);
        verify(publisher, times(24)).stage(packageCaptor.capture());
        ArgumentCaptor<ObjectNode> manifestCaptor = ArgumentCaptor.forClass(ObjectNode.class);
        verify(publisher)
                .publishStaged(
                        anyString(),
                        anyString(),
                        manifestCaptor.capture(),
                        org.mockito.ArgumentMatchers.eq(24L));
        assertThat(manifestCaptor.getValue().path("offerings").findValuesAsText("key"))
                .containsExactly("SYDNEY_TRAINS", "SYDNEY_METRO")
                .doesNotContain("REPLACEMENT_BUS", "PCBC");
        assertThat(
                        manifestCaptor
                                .getValue()
                                .path("offerings")
                                .get(0)
                                .path("sources")
                                .findValuesAsText("feedKey"))
                .contains("replacementbus", "pcbc");

        TimetablePackageDocument trainPackage =
                packageCaptor.getAllValues().stream()
                        .filter(
                                document ->
                                        document.getOffering().equals("SYDNEY_TRAINS")
                                                && document.getTravelDate()
                                                        .equals(LocalDate.of(2026, 11, 1)))
                        .findFirst()
                        .orElseThrow();
        var body =
                MAPPER.readTree(
                        new GZIPInputStream(new ByteArrayInputStream(trainPackage.getPayload())));
        assertThat(body.withArray("offerings").toString())
                .doesNotContain("REPLACEMENT_BUS", "PCBC");
        assertThat(items(body.path("trips")))
                .anySatisfy(
                        trip -> {
                            assertThat(trip.path("id").asText())
                                    .isEqualTo("replacementbus:2757475:2026-11-01");
                            assertThat(trip.path("servicePurpose").asText())
                                    .isEqualTo("REPLACEMENT");
                        })
                .anySatisfy(
                        trip ->
                                assertThat(trip.path("servicePurpose").asText())
                                        .isEqualTo("PLANNED_CONTINGENCY"));
        assertThat(items(body.path("routes")))
                .anySatisfy(route -> assertThat(route.path("shortName").asText()).isEqualTo("9T2"))
                .anySatisfy(
                        route -> assertThat(route.path("shortName").asText()).isEqualTo("11T2"));
        assertThat(body.withArray("sources").findValuesAsText("feedKey"))
                .contains("replacementbus", "pcbc");
        assertThat(items(body.path("interchanges")))
                .anySatisfy(
                        interchange -> {
                            assertThat(interchange.path("fromStopId").asText())
                                    .isEqualTo("replacementbus:217938");
                            assertThat(interchange.path("toStopId").asText())
                                    .isEqualTo("sydneytrains:217933");
                        });
    }

    private ObjectNode packageBody(String offering, LocalDate date, boolean metro) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("travelDate", date.toString());
        body.putArray("offerings").add(offering);
        body.putArray("offeringStatuses").add(MAPPER.createObjectNode().put("key", offering));
        body.putArray("sources").add(MAPPER.createObjectNode().put("feedKey", offering));
        body.putArray("stops").add(MAPPER.createObjectNode().put("id", offering + "-stop"));
        if (offering.equals("SYDNEY_TRAINS")) {
            for (String id : List.of("217933", "216710", "217426", "212710", "213110")) {
                body.withArray("stops")
                        .add(MAPPER.createObjectNode().put("id", "sydneytrains:" + id));
            }
        }
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

    private List<JsonNode> items(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }
}
