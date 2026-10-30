/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import com.example.metro_parking_web_service.timetable.realtime.client.TfnswRealtimeAlertClient;
import com.example.metro_parking_web_service.timetable.realtime.client.TfnswRealtimeProtobufClient;
import com.example.metro_parking_web_service.timetable.realtime.config.TfnswRealtimeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

@ExtendWith(MockitoExtension.class)
class TfnswRealtimeRefreshServiceTest {

    @Mock private TfnswRealtimeAlertClient alertClient;
    @Mock private TfnswRealtimeProtobufClient protobufClient;
    @Mock private GtfsRealtimeAlertNormalizer alertNormalizer;
    @Mock private GtfsRealtimeBinaryNormalizer binaryNormalizer;
    @Mock private RealtimeOverlayPublisher publisher;

    @Test
    void refreshesBothConfiguredFeedsAndPublishesIndependentSourcePayloads() throws Exception {
        var service = service("apikey token");
        JsonNode feed = new ObjectMapper().readTree("{\"header\":{},\"entity\":[]}");
        var trains = normalized("2.0");
        var metro = normalized("1.0");
        when(alertClient.fetch("alerts-sydneytrains", "/alerts/trains")).thenReturn(feed);
        when(alertClient.fetch("alerts-metro", "/alerts/metro")).thenReturn(feed);
        when(alertNormalizer.normalize("alerts-sydneytrains", feed)).thenReturn(trains);
        when(alertNormalizer.normalize("alerts-metro", feed)).thenReturn(metro);
        when(protobufClient.fetch(any(), any())).thenReturn(new byte[] {10, 0});
        when(binaryNormalizer.normalize(any(), any(), any()))
                .thenAnswer(
                        invocation ->
                                normalizedBinary(
                                        invocation.getArgument(0), invocation.getArgument(2)));

        service.refreshFeeds();

        verify(publisher).publishAlerts(eq("alerts-sydneytrains"), eq(trains), any(Instant.class));
        verify(publisher).publishAlerts(eq("alerts-metro"), eq(metro), any(Instant.class));
        verify(publisher, org.mockito.Mockito.times(4)).publishProtobuf(any());
    }

    @Test
    void providerFailureForOneFeedDoesNotPreventOtherFeedRefresh() throws Exception {
        var service = service("apikey token");
        JsonNode feed = new ObjectMapper().readTree("{\"header\":{},\"entity\":[]}");
        var metro = normalized("1.0");
        when(alertClient.fetch("alerts-sydneytrains", "/alerts/trains"))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));
        when(alertClient.fetch("alerts-metro", "/alerts/metro")).thenReturn(feed);
        when(alertNormalizer.normalize("alerts-metro", feed)).thenReturn(metro);
        when(protobufClient.fetch(any(), any())).thenReturn(new byte[] {10, 0});
        when(binaryNormalizer.normalize(any(), any(), any()))
                .thenAnswer(
                        invocation ->
                                normalizedBinary(
                                        invocation.getArgument(0), invocation.getArgument(2)));

        service.refreshFeeds();

        verify(publisher).publishFailure(eq("alerts-sydneytrains"), eq("upstream_http_503"), any());
        verify(publisher).publishAlerts(eq("alerts-metro"), eq(metro), any());
    }

    @Test
    void skipsProviderCallsWhenCredentialIsMissing() {
        service("").refreshFeeds();

        verify(alertClient, never()).fetch(any(), any());
        verify(protobufClient, never()).fetch(any(), any());
        verify(publisher, never()).publishAlerts(any(), any(), any());
    }

    @Test
    void skipsIndividuallyUnconfiguredFeedPaths() {
        var service = serviceWithPaths("apikey token", null, " ", null, null, null, null);

        service.refreshFeeds();

        verify(alertClient, never()).fetch(any(), any());
        verify(protobufClient, never()).fetch(any(), any());
        verify(publisher, never()).publishFailure(any(), any(), any());
    }

    @Test
    void reportsUnsupportedDifferentialProtobufAndContinuesOtherFeeds() throws Exception {
        var service = service("apikey token");
        JsonNode feed = new ObjectMapper().readTree("{\"header\":{},\"entity\":[]}");
        var alerts = normalized("2.0");
        when(alertClient.fetch(any(), any())).thenReturn(feed);
        when(alertNormalizer.normalize(any(), eq(feed))).thenReturn(alerts);
        when(protobufClient.fetch(any(), any())).thenReturn(new byte[] {10, 0});
        when(binaryNormalizer.normalize(any(), any(), any()))
                .thenAnswer(
                        invocation -> {
                            String feedKey = invocation.getArgument(0);
                            if (feedKey.equals("trip-updates-metro")) {
                                throw new IllegalArgumentException(
                                        "Differential GTFS-Realtime feeds are not supported");
                            }
                            return normalizedBinary(feedKey, invocation.getArgument(2));
                        });

        service.refreshFeeds();

        verify(publisher)
                .publishFailure(
                        eq("trip-updates-metro"),
                        eq("tfnsw-gtfs-realtime-metro-variant-detected-at-runtime-v1"),
                        eq("unsupported_incrementality"),
                        any());
        verify(publisher, org.mockito.Mockito.times(3)).publishProtobuf(any());
    }

    @Test
    void classifiesUnexpectedAlertNormalizationErrorsWithoutStoppingOtherFeeds() throws Exception {
        var service = service("apikey token");
        JsonNode feed = new ObjectMapper().readTree("{\"header\":{},\"entity\":[]}");
        var goodAlerts = normalized("2.0");
        when(alertClient.fetch(any(), any())).thenReturn(feed);
        when(alertNormalizer.normalize("alerts-sydneytrains", feed))
                .thenThrow(new IllegalStateException("bad alert structure"));
        when(alertNormalizer.normalize("alerts-metro", feed)).thenReturn(goodAlerts);
        when(protobufClient.fetch(any(), any())).thenReturn(new byte[] {10, 0});
        when(binaryNormalizer.normalize(any(), any(), any()))
                .thenAnswer(
                        invocation ->
                                normalizedBinary(
                                        invocation.getArgument(0), invocation.getArgument(2)));

        service.refreshFeeds();

        verify(publisher)
                .publishFailure(
                        eq("alerts-sydneytrains"), eq("invalid_or_unavailable_feed"), any());
        verify(publisher).publishAlerts(eq("alerts-metro"), eq(goodAlerts), any());
        verify(publisher, org.mockito.Mockito.times(4)).publishProtobuf(any());
    }

    private TfnswRealtimeRefreshService service(String apiKey) {
        return serviceWithPaths(
                apiKey,
                "/alerts/trains",
                "/alerts/metro",
                "/trips/trains",
                "/trips/metro",
                "/vehicles/trains",
                "/vehicles/metro");
    }

    private TfnswRealtimeRefreshService serviceWithPaths(
            String apiKey,
            String alertsSydneyTrains,
            String alertsMetro,
            String tripUpdatesSydneyTrains,
            String tripUpdatesMetro,
            String vehiclePositionsSydneyTrains,
            String vehiclePositionsMetro) {
        return new TfnswRealtimeRefreshService(
                new TfnswRealtimeProperties(
                        new TfnswRealtimeProperties.Api(
                                alertsSydneyTrains,
                                alertsMetro,
                                tripUpdatesSydneyTrains,
                                tripUpdatesMetro,
                                vehiclePositionsSydneyTrains,
                                vehiclePositionsMetro),
                        true,
                        java.time.Duration.ofMinutes(1)),
                new TfnswConnectionProperties("https://provider.test", apiKey),
                alertClient,
                protobufClient,
                alertNormalizer,
                binaryNormalizer,
                publisher);
    }

    private GtfsRealtimeBinaryNormalizer.NormalizedFeed normalizedBinary(
            String feedKey, Instant retrievedAt) {
        var mapper = new ObjectMapper();
        return new GtfsRealtimeBinaryNormalizer.NormalizedFeed(
                feedKey,
                "schema",
                "2.0",
                "FULL_DATASET",
                Instant.now(),
                retrievedAt,
                mapper.createArrayNode(),
                mapper.createArrayNode());
    }

    private GtfsRealtimeAlertNormalizer.NormalizedAlerts normalized(String version) {
        return new GtfsRealtimeAlertNormalizer.NormalizedAlerts(
                version, "FULL_DATASET", Instant.now(), List.of());
    }
}
