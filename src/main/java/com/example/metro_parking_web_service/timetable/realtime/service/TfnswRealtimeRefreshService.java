/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.service;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import com.example.metro_parking_web_service.timetable.realtime.client.TfnswRealtimeAlertClient;
import com.example.metro_parking_web_service.timetable.realtime.client.TfnswRealtimeProtobufClient;
import com.example.metro_parking_web_service.timetable.realtime.config.TfnswRealtimeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "providers.tfnsw.realtime", name = "enabled", havingValue = "true")
public class TfnswRealtimeRefreshService {

    private final TfnswRealtimeProperties properties;
    private final TfnswConnectionProperties connection;
    private final TfnswRealtimeAlertClient alertClient;
    private final TfnswRealtimeProtobufClient protobufClient;
    private final GtfsRealtimeAlertNormalizer alertNormalizer;
    private final GtfsRealtimeBinaryNormalizer binaryNormalizer;
    private final RealtimeScheduleMatcher scheduleMatcher;
    private final RealtimeOverlayPublisher publisher;

    @Scheduled(
            fixedDelayString = "${providers.tfnsw.realtime.refresh-interval:PT1M}",
            initialDelayString = "PT30S")
    public void refreshFeeds() {
        if (connection.apikey() == null || connection.apikey().isBlank()) {
            log.warn("event=timetable_realtime_refresh_skipped reason=provider_api_key_missing");
            return;
        }
        refreshAlerts("alerts-sydneytrains", properties.api().alertsSydneyTrains());
        refreshAlerts("alerts-metro", properties.api().alertsMetro());
        refreshProtobuf("trip-updates-sydneytrains", properties.api().tripUpdatesSydneyTrains());
        refreshProtobuf("trip-updates-metro", properties.api().tripUpdatesMetro());
        refreshProtobuf(
                "vehicle-positions-sydneytrains", properties.api().vehiclePositionsSydneyTrains());
        refreshProtobuf("vehicle-positions-metro", properties.api().vehiclePositionsMetro());
    }

    private void refreshAlerts(String feedKey, String path) {
        if (path == null || path.isBlank()) {
            log.warn(
                    "event=timetable_realtime_refresh_skipped feed={} reason=provider_path_missing",
                    feedKey);
            return;
        }
        Instant attemptedAt = Instant.now();
        try {
            JsonNode feed = alertClient.fetch(feedKey, path);
            var normalized = alertNormalizer.normalize(feedKey, feed);
            Instant retrievedAt = Instant.now();
            publisher.publishAlerts(feedKey, normalized, retrievedAt);
            log.info(
                    "event=timetable_realtime_refresh_complete feed={} status=CURRENT"
                            + " alertCount={} protocolVersion={} providerGeneratedAt={}",
                    feedKey,
                    normalized.alerts().size(),
                    normalized.protocolVersion(),
                    normalized.providerGeneratedAt());
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            publisher.publishFailure(feedKey, "upstream_http_" + status, attemptedAt);
            log.error(
                    "event=timetable_realtime_refresh_failed feed={} upstreamStatus={}"
                            + " lastKnownGoodRetained=true",
                    feedKey,
                    status);
        } catch (Exception exception) {
            String reason =
                    exception.getMessage() != null
                                    && exception.getMessage().contains("Differential")
                            ? "unsupported_incrementality"
                            : "invalid_or_unavailable_feed";
            publisher.publishFailure(feedKey, reason, attemptedAt);
            log.error(
                    "event=timetable_realtime_refresh_failed feed={} reason={}"
                            + " failureType={} lastKnownGoodRetained=true",
                    feedKey,
                    reason,
                    exception.getClass().getSimpleName());
        }
    }

    private void refreshProtobuf(String feedKey, String path) {
        if (path == null || path.isBlank()) {
            log.warn(
                    "event=timetable_realtime_refresh_skipped feed={} reason=provider_path_missing",
                    feedKey);
            return;
        }
        Instant attemptedAt = Instant.now();
        String schemaKey = schemaKey(feedKey);
        try {
            byte[] bytes = protobufClient.fetch(feedKey, path);
            var normalized =
                    scheduleMatcher.join(binaryNormalizer.normalize(feedKey, bytes, Instant.now()));
            publisher.publishProtobuf(normalized);
            log.info(
                    "event=timetable_realtime_refresh_complete feed={} status=CURRENT"
                            + " tripUpdateCount={} vehiclePositionCount={} protocolVersion={}"
                            + " providerGeneratedAt={} responseBytes={}",
                    feedKey,
                    normalized.tripUpdates().size(),
                    normalized.vehiclePositions().size(),
                    normalized.protocolVersion(),
                    normalized.providerGeneratedAt(),
                    bytes.length);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            publisher.publishFailure(feedKey, schemaKey, "upstream_http_" + status, attemptedAt);
            log.error(
                    "event=timetable_realtime_refresh_failed feed={} upstreamStatus={}"
                            + " lastKnownGoodRetained=true",
                    feedKey,
                    status);
        } catch (Exception exception) {
            String reason =
                    exception.getMessage() != null
                                    && exception.getMessage().contains("Differential")
                            ? "unsupported_incrementality"
                            : "invalid_or_unavailable_feed";
            publisher.publishFailure(feedKey, schemaKey, reason, attemptedAt);
            log.error(
                    "event=timetable_realtime_refresh_failed feed={} reason={}"
                            + " failureType={} lastKnownGoodRetained=true",
                    feedKey,
                    reason,
                    exception.getClass().getSimpleName());
        }
    }

    private String schemaKey(String feedKey) {
        return feedKey.contains("sydneytrains")
                ? "tfnsw-gtfs-realtime-sydneytrains-1007-v1"
                : "tfnsw-gtfs-realtime-metro-variant-detected-at-runtime-v1";
    }
}
