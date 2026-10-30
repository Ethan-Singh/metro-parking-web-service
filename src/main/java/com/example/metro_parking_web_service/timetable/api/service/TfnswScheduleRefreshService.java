/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import com.example.metro_parking_web_service.timetable.api.document.TimetableFeedStateDocument;
import com.example.metro_parking_web_service.timetable.api.repository.TimetableFeedStateRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "providers.tfnsw.timetable", name = "enabled", havingValue = "true")
public class TfnswScheduleRefreshService {

    private static final String TRAINS = "sydneytrains";
    private static final String METRO = "metro";
    private static final String REPLACEMENT_BUS = "replacementbus";
    private static final String PCBC = "pcbc";

    private final TfnswConnectionProperties connectionProperties;
    private final TfnswScheduleFeedClient feedClient;
    private final TimetableFeedStateRepository feedStateRepository;
    private final TimetableSnapshotBuildService snapshotBuildService;

    @Scheduled(
            fixedDelayString = "${providers.tfnsw.timetable.refresh-interval:PT6H}",
            initialDelayString = "PT30S")
    public void refreshIfChanged() {
        if (connectionProperties.apikey() == null || connectionProperties.apikey().isBlank()) {
            log.warn("event=timetable_refresh_skipped reason=provider_api_key_missing");
            return;
        }

        try {
            String trainsLastModified = feedClient.head(TRAINS).lastModified();
            String metroLastModified = feedClient.head(METRO).lastModified();
            String replacementBusLastModified = feedClient.head(REPLACEMENT_BUS).lastModified();
            String pcbcLastModified = feedClient.head(PCBC).lastModified();
            TimetableFeedStateDocument previousTrains = state(TRAINS);
            TimetableFeedStateDocument previousMetro = state(METRO);
            TimetableFeedStateDocument previousReplacementBus = state(REPLACEMENT_BUS);
            TimetableFeedStateDocument previousPcbc = state(PCBC);
            boolean trainsUnchanged = unchanged(previousTrains, trainsLastModified);
            boolean metroUnchanged = unchanged(previousMetro, metroLastModified);
            boolean replacementBusUnchanged =
                    unchanged(previousReplacementBus, replacementBusLastModified);
            boolean pcbcUnchanged = unchanged(previousPcbc, pcbcLastModified);
            logFeedStatus(TRAINS, trainsUnchanged, trainsLastModified);
            logFeedStatus(METRO, metroUnchanged, metroLastModified);
            logFeedStatus(REPLACEMENT_BUS, replacementBusUnchanged, replacementBusLastModified);
            logFeedStatus(PCBC, pcbcUnchanged, pcbcLastModified);
            if (trainsUnchanged && metroUnchanged && replacementBusUnchanged && pcbcUnchanged) {
                log.info(
                        "event=timetable_provider_refresh_skipped reason=feeds_current"
                                + " trainsLastModified={} metroLastModified={}"
                                + " replacementBusLastModified={} pcbcLastModified={}",
                        printableRevision(trainsLastModified),
                        printableRevision(metroLastModified),
                        printableRevision(replacementBusLastModified),
                        printableRevision(pcbcLastModified));
                return;
            }

            Path trainsTemp = Files.createTempFile("timetable-sydneytrains-", ".zip");
            Path metroTemp = Files.createTempFile("timetable-metro-", ".zip");
            Path replacementBusTemp = Files.createTempFile("timetable-replacementbus-", ".zip");
            Path pcbcTemp = Files.createTempFile("timetable-pcbc-", ".zip");
            try {
                byte[] trainsBytes = feedClient.download(TRAINS);
                byte[] metroBytes = feedClient.download(METRO);
                byte[] replacementBusBytes = feedClient.download(REPLACEMENT_BUS);
                byte[] pcbcBytes = feedClient.download(PCBC);
                Files.write(trainsTemp, trainsBytes);
                Files.write(metroTemp, metroBytes);
                Files.write(replacementBusTemp, replacementBusBytes);
                Files.write(pcbcTemp, pcbcBytes);
                String trainsRevision = revision(trainsLastModified, trainsBytes);
                String metroRevision = revision(metroLastModified, metroBytes);
                String replacementBusRevision =
                        revision(replacementBusLastModified, replacementBusBytes);
                String pcbcRevision = revision(pcbcLastModified, pcbcBytes);

                snapshotBuildService.buildAndPublish(
                        trainsTemp,
                        trainsRevision,
                        metroTemp,
                        metroRevision,
                        replacementBusTemp,
                        replacementBusRevision,
                        pcbcTemp,
                        pcbcRevision);
                saveState(TRAINS, trainsLastModified, trainsBytes);
                saveState(METRO, metroLastModified, metroBytes);
                saveState(REPLACEMENT_BUS, replacementBusLastModified, replacementBusBytes);
                saveState(PCBC, pcbcLastModified, pcbcBytes);
                log.info(
                        "event=timetable_provider_refresh_complete trainsRevision={}"
                                + " metroRevision={} replacementBusRevision={} pcbcRevision={}"
                                + " trainsStatus={} metroStatus={} replacementBusStatus={}"
                                + " pcbcStatus={}",
                        trainsRevision,
                        metroRevision,
                        replacementBusRevision,
                        pcbcRevision,
                        trainsUnchanged ? "current" : "updated",
                        metroUnchanged ? "current" : "updated",
                        replacementBusUnchanged ? "current" : "updated",
                        pcbcUnchanged ? "current" : "updated");
            } finally {
                Files.deleteIfExists(trainsTemp);
                Files.deleteIfExists(metroTemp);
                Files.deleteIfExists(replacementBusTemp);
                Files.deleteIfExists(pcbcTemp);
            }
        } catch (RestClientResponseException exception) {
            log.error(
                    "event=timetable_provider_refresh_failed lastKnownGoodRetained=true"
                            + " upstreamStatus={} reason={}",
                    exception.getStatusCode().value(),
                    describeStatus(exception.getStatusCode().value()),
                    exception);
        } catch (Exception exception) {
            log.error(
                    "event=timetable_provider_refresh_failed lastKnownGoodRetained=true"
                            + " failureType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private TimetableFeedStateDocument state(String feedKey) {
        return feedStateRepository.findById(feedKey).orElse(null);
    }

    private void logFeedStatus(String feedKey, boolean unchanged, String lastModified) {
        log.info(
                "event=timetable_feed_check feed={} status={} providerLastModified={}",
                feedKey,
                unchanged ? "current" : "changed",
                printableRevision(lastModified));
    }

    private String describeStatus(int status) {
        return switch (status) {
            case 401, 403 -> "check_provider_api_key_or_access";
            case 429 -> "provider_rate_limit";
            case 500, 502, 503, 504 -> "provider_server_error";
            default -> "provider_request_failed";
        };
    }

    private boolean unchanged(TimetableFeedStateDocument previous, String lastModified) {
        return previous != null
                && lastModified != null
                && !lastModified.isBlank()
                && lastModified.equals(previous.getProviderLastModified());
    }

    private String revision(String lastModified, byte[] bytes) {
        return lastModified == null || lastModified.isBlank() ? sha256(bytes) : lastModified;
    }

    private String printableRevision(String lastModified) {
        return lastModified == null || lastModified.isBlank() ? "unavailable" : lastModified;
    }

    private void saveState(String key, String lastModified, byte[] content) {
        TimetableFeedStateDocument state = new TimetableFeedStateDocument();
        state.setFeedKey(key);
        state.setProviderLastModified(lastModified);
        state.setChecksum(sha256(content));
        state.setRetrievedAt(java.time.Instant.now());
        feedStateRepository.save(state);
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
