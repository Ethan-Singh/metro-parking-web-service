/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import com.example.metro_parking_web_service.timetable.api.repository.TimetableFeedStateRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

@ExtendWith(MockitoExtension.class)
class TfnswScheduleRefreshServiceTest {

    private static final byte[] ZIP_BYTES = {'P', 'K', 3, 4};

    @Mock private TfnswScheduleFeedClient feedClient;
    @Mock private TimetableFeedStateRepository feedStateRepository;
    @Mock private TimetableSnapshotBuildService snapshotBuildService;

    @Test
    void skipsRefreshWhenApiKeyIsMissing() throws java.io.IOException {
        TfnswScheduleRefreshService service = service(null);

        service.refreshIfChanged();

        verify(feedClient, never()).head(anyString());
        verify(snapshotBuildService, never())
                .buildAndPublish(
                        any(),
                        anyString(),
                        any(),
                        anyString(),
                        any(),
                        anyString(),
                        any(),
                        anyString());
    }

    @Test
    void retainsCurrentSnapshotWhenProviderReturnsServerError() throws java.io.IOException {
        TfnswScheduleRefreshService service = service("apikey token");
        when(feedClient.head("sydneytrains"))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        service.refreshIfChanged();

        verify(snapshotBuildService, never())
                .buildAndPublish(
                        any(),
                        anyString(),
                        any(),
                        anyString(),
                        any(),
                        anyString(),
                        any(),
                        anyString());
        verify(feedStateRepository, never()).save(any());
    }

    @Test
    void buildsAndPublishesSnapshotWhenProviderFeedsAreAvailable() throws java.io.IOException {
        TfnswScheduleRefreshService service = service("apikey token");
        when(feedClient.head("sydneytrains"))
                .thenReturn(new TfnswScheduleFeedClient.FeedHead("train-revision"));
        when(feedClient.head("metro"))
                .thenReturn(new TfnswScheduleFeedClient.FeedHead("metro-revision"));
        when(feedClient.head("replacementbus"))
                .thenReturn(new TfnswScheduleFeedClient.FeedHead("replacementbus-revision"));
        when(feedClient.head("pcbc"))
                .thenReturn(new TfnswScheduleFeedClient.FeedHead("pcbc-revision"));
        when(feedStateRepository.findById(anyString())).thenReturn(Optional.empty());
        when(feedClient.downloadTo(anyString(), any()))
                .thenAnswer(
                        invocation -> {
                            java.nio.file.Files.write(invocation.getArgument(1), ZIP_BYTES);
                            return (long) ZIP_BYTES.length;
                        });

        service.refreshIfChanged();

        verify(snapshotBuildService)
                .buildAndPublish(
                        any(),
                        org.mockito.ArgumentMatchers.eq("train-revision"),
                        any(),
                        org.mockito.ArgumentMatchers.eq("metro-revision"),
                        any(),
                        org.mockito.ArgumentMatchers.eq("replacementbus-revision"),
                        any(),
                        org.mockito.ArgumentMatchers.eq("pcbc-revision"));
        verify(feedStateRepository, org.mockito.Mockito.times(4)).save(any());
    }

    @Test
    void skipsDownloadWhenBothProviderRevisionsAreUnchanged() throws java.io.IOException {
        TfnswScheduleRefreshService service = service("apikey token");
        var trains = state("sydneytrains", "same-revision");
        var metro = state("metro", "same-revision");
        var replacementBus = state("replacementbus", "same-revision");
        var pcbc = state("pcbc", "same-revision");
        when(feedClient.head("sydneytrains"))
                .thenReturn(new TfnswScheduleFeedClient.FeedHead("same-revision"));
        when(feedClient.head("metro"))
                .thenReturn(new TfnswScheduleFeedClient.FeedHead("same-revision"));
        when(feedClient.head("replacementbus"))
                .thenReturn(new TfnswScheduleFeedClient.FeedHead("same-revision"));
        when(feedClient.head("pcbc"))
                .thenReturn(new TfnswScheduleFeedClient.FeedHead("same-revision"));
        when(feedStateRepository.findById("sydneytrains")).thenReturn(Optional.of(trains));
        when(feedStateRepository.findById("metro")).thenReturn(Optional.of(metro));
        when(feedStateRepository.findById("replacementbus"))
                .thenReturn(Optional.of(replacementBus));
        when(feedStateRepository.findById("pcbc")).thenReturn(Optional.of(pcbc));

        service.refreshIfChanged();

        verify(feedClient, never()).downloadTo(anyString(), any());
        verify(snapshotBuildService, never())
                .buildAndPublish(
                        any(),
                        anyString(),
                        any(),
                        anyString(),
                        any(),
                        anyString(),
                        any(),
                        anyString());
    }

    private TfnswScheduleRefreshService service(String apiKey) {
        return new TfnswScheduleRefreshService(
                new TfnswConnectionProperties("https://provider.test", apiKey),
                feedClient,
                feedStateRepository,
                snapshotBuildService);
    }

    private com.example.metro_parking_web_service.timetable.api.document.TimetableFeedStateDocument
            state(String key, String revision) {
        var state =
                new com.example.metro_parking_web_service.timetable.api.document
                        .TimetableFeedStateDocument();
        state.setFeedKey(key);
        state.setProviderLastModified(revision);
        return state;
    }
}
