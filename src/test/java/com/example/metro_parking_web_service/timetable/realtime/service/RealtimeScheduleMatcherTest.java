/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.timetable.api.document.TimetableManifestDocument;
import com.example.metro_parking_web_service.timetable.api.document.TimetablePackageDocument;
import com.example.metro_parking_web_service.timetable.api.repository.TimetableManifestRepository;
import com.example.metro_parking_web_service.timetable.api.repository.TimetablePackageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealtimeScheduleMatcherTest {

    private static final String VERSION = "snapshot-test";
    private final ObjectMapper mapper = new ObjectMapper();

    @Mock private TimetableManifestRepository manifestRepository;
    @Mock private TimetablePackageRepository packageRepository;

    private RealtimeScheduleMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new RealtimeScheduleMatcher(manifestRepository, packageRepository);
    }

    @Test
    void uniquelyMatchesTripAndExactStopCallWithinItsFeed() throws Exception {
        publishPackages(List.of(packageDocument("trip-a", "2026-10-28", "route-a", "stop-1", 3)));
        var input = feed("trip-updates-sydneytrains", "trip-a", "route-a", "2026-10-28", true);

        var joined = matcher.join(input);

        var trip = joined.tripUpdates().get(0);
        assertEquals("MATCHED", trip.path("matchStatus").asText());
        assertEquals("sydneytrains:trip-a:2026-10-28", trip.path("scheduleTripId").asText());
        assertEquals("sydneytrains:route-a", trip.path("scheduleRouteId").asText());
        assertEquals(VERSION, trip.path("scheduleSnapshotVersion").asText());
        assertEquals("MATCHED", trip.path("stopUpdates").get(0).path("matchStatus").asText());
        assertEquals(
                "sydneytrains:stop-1",
                trip.path("stopUpdates").get(0).path("scheduleStopId").asText());
        var vehicle = joined.vehiclePositions().get(0);
        assertEquals("MATCHED", vehicle.path("matchStatus").asText());
        assertEquals("sydneytrains:trip-a:2026-10-28", vehicle.path("scheduleTripId").asText());
        assertEquals("MATCHED", vehicle.path("stopMatchStatus").asText());
        assertEquals("sydneytrains:stop-1", vehicle.path("scheduleStopId").asText());
    }

    @Test
    void missingStartDateWithMultipleServiceInstancesIsAmbiguous() throws Exception {
        publishPackages(
                List.of(
                        packageDocument("trip-a", "2026-10-27", "route-a", "stop-1", 3),
                        packageDocument("trip-a", "2026-10-28", "route-a", "stop-1", 3)));
        var joined =
                matcher.join(feed("trip-updates-sydneytrains", "trip-a", "route-a", null, false));

        var trip = joined.tripUpdates().get(0);
        assertEquals("AMBIGUOUS", trip.path("matchStatus").asText());
        assertEquals(2, trip.path("scheduleTripCandidates").size());
        assertFalse(trip.has("scheduleTripId"));
    }

    @Test
    void routeMismatchAndCrossFeedIdentifiersRemainUnmatched() throws Exception {
        publishPackages(List.of(packageDocument("trip-a", "2026-10-28", "route-a", "stop-1", 3)));

        var wrongRoute =
                matcher.join(
                        feed(
                                "trip-updates-sydneytrains",
                                "trip-a",
                                "route-b",
                                "2026-10-28",
                                false));
        var wrongFeed =
                matcher.join(feed("trip-updates-metro", "trip-a", "route-a", "2026-10-28", false));

        assertEquals("UNMATCHED", wrongRoute.tripUpdates().get(0).path("matchStatus").asText());
        assertEquals("UNMATCHED", wrongFeed.tripUpdates().get(0).path("matchStatus").asText());
    }

    @Test
    void doesNotMatchStopCallWithoutAnExactSequenceAndStopPair() throws Exception {
        publishPackages(List.of(packageDocument("trip-a", "2026-10-28", "route-a", "stop-1", 3)));
        var joined =
                matcher.join(
                        feed(
                                "trip-updates-sydneytrains",
                                "trip-a",
                                "route-a",
                                "2026-10-28",
                                false));

        assertEquals(
                "UNMATCHED",
                joined.tripUpdates()
                        .get(0)
                        .path("stopUpdates")
                        .get(0)
                        .path("matchStatus")
                        .asText());
    }

    private void publishPackages(List<TimetablePackageDocument> packages) {
        TimetableManifestDocument manifest = new TimetableManifestDocument();
        manifest.setId("active");
        manifest.setSnapshotVersion(VERSION);
        when(manifestRepository.findById("active")).thenReturn(Optional.of(manifest));
        when(packageRepository.findAllBySnapshotVersionAndOffering(VERSION, "ALL"))
                .thenReturn(packages);
    }

    private TimetablePackageDocument packageDocument(
            String providerTripId,
            String serviceDate,
            String providerRouteId,
            String providerStopId,
            int sequence)
            throws Exception {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode trips = root.putArray("trips");
        trips.addObject()
                .put("id", "sydneytrains:" + providerTripId + ":" + serviceDate)
                .put("providerId", providerTripId)
                .put("routeId", "sydneytrains:" + providerRouteId)
                .put("serviceDate", serviceDate);
        root.putArray("calls")
                .addObject()
                .put("tripId", "sydneytrains:" + providerTripId + ":" + serviceDate)
                .put("sequence", sequence)
                .put("stopId", "sydneytrains:" + providerStopId);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            mapper.writeValue(gzip, root);
        }
        TimetablePackageDocument document = new TimetablePackageDocument();
        document.setSnapshotVersion(VERSION);
        document.setOffering("ALL");
        document.setPayload(bytes.toByteArray());
        document.setContentEncoding("gzip");
        return document;
    }

    private GtfsRealtimeBinaryNormalizer.NormalizedFeed feed(
            String feedKey, String tripId, String routeId, String startDate, boolean matchingStop) {
        ArrayNode updates = mapper.createArrayNode();
        ObjectNode trip = updates.addObject();
        trip.put("feedKey", feedKey);
        trip.put("tripId", tripId);
        trip.put("routeId", routeId);
        trip.put("matchStatus", "UNMATCHED");
        if (startDate != null) trip.put("startDate", startDate);
        if (matchingStop) {
            trip.putArray("stopUpdates").addObject().put("stopId", "stop-1").put("stopSequence", 3);
        } else {
            trip.putArray("stopUpdates")
                    .addObject()
                    .put("stopId", "stop-other")
                    .put("stopSequence", 99);
        }
        ArrayNode vehicles = mapper.createArrayNode();
        ObjectNode vehicle =
                vehicles.addObject()
                        .put("feedKey", feedKey.replace("trip-updates", "vehicle-positions"))
                        .put("tripId", tripId)
                        .put("routeId", routeId)
                        .put("stopId", matchingStop ? "stop-1" : "stop-other")
                        .put("stopSequence", matchingStop ? 3 : 99)
                        .put("matchStatus", "UNMATCHED");
        if (startDate != null) vehicle.put("startDate", startDate);
        return new GtfsRealtimeBinaryNormalizer.NormalizedFeed(
                feedKey,
                "schema",
                "2.0",
                "FULL_DATASET",
                Instant.parse("2026-10-28T10:00:00Z"),
                Instant.parse("2026-10-28T10:01:00Z"),
                updates,
                vehicles);
    }
}
