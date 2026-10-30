/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import com.example.metro_parking_web_service.timetable.api.config.TimetableProviderProperties;
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

    private final TimetableProviderProperties properties;
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
            TimetableFeedStateDocument previousTrains =
                    feedStateRepository.findById(TRAINS).orElse(null);
            TimetableFeedStateDocument previousMetro =
                    feedStateRepository.findById(METRO).orElse(null);
            if (unchanged(previousTrains, trainsLastModified)
                    && unchanged(previousMetro, metroLastModified)) {
                log.debug("event=timetable_refresh_skipped reason=feeds_unchanged");
                return;
            }

            Path trainsTemp = Files.createTempFile("timetable-sydneytrains-", ".zip");
            Path metroTemp = Files.createTempFile("timetable-metro-", ".zip");
            try {
                byte[] trainsBytes = feedClient.download(TRAINS);
                byte[] metroBytes = feedClient.download(METRO);
                Files.write(trainsTemp, trainsBytes);
                Files.write(metroTemp, metroBytes);
                String trainsRevision = revision(trainsLastModified, trainsBytes);
                String metroRevision = revision(metroLastModified, metroBytes);

                snapshotBuildService.buildAndPublish(
                        trainsTemp, trainsRevision, metroTemp, metroRevision);
                saveState(TRAINS, trainsLastModified, trainsBytes);
                saveState(METRO, metroLastModified, metroBytes);
                log.info(
                        "event=timetable_provider_refresh_complete trainsRevision={}"
                                + " metroRevision={}",
                        trainsRevision,
                        metroRevision);
            } finally {
                Files.deleteIfExists(trainsTemp);
                Files.deleteIfExists(metroTemp);
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
