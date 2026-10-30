/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import com.example.metro_parking_web_service.timetable.api.config.TimetableProviderProperties;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class TfnswScheduleFeedClient {

    private final RestClient restClient;

    private final TimetableProviderProperties properties;

    private final TfnswConnectionProperties connection;

    public TfnswScheduleFeedClient(
            @Qualifier("timetableProviderRestClient") RestClient restClient,
            TimetableProviderProperties properties,
            TfnswConnectionProperties connection) {
        this.restClient = restClient;
        this.properties = properties;
        this.connection = connection;
    }

    public FeedHead head(String feedKey) {
        String path = path(feedKey);
        log.info(
                "event=timetable_provider_request method=HEAD feed={} uri={} accept={}"
                        + " authorizationHeader=omitted",
                feedKey,
                connection.baseUrl() + path,
                MediaType.APPLICATION_OCTET_STREAM);
        var response =
                restClient
                        .method(HttpMethod.HEAD)
                        .uri(path)
                        .accept(MediaType.APPLICATION_OCTET_STREAM)
                        .retrieve()
                        .toBodilessEntity();
        return new FeedHead(response.getHeaders().getFirst("Last-Modified"));
    }

    public byte[] download(String feedKey) {
        String path = path(feedKey);
        log.info(
                "event=timetable_provider_request method=GET feed={} uri={} accept={}"
                        + " authorizationHeader=omitted",
                feedKey,
                connection.baseUrl() + path,
                MediaType.APPLICATION_OCTET_STREAM);
        byte[] zip =
                restClient
                        .get()
                        .uri(path)
                        .accept(MediaType.APPLICATION_OCTET_STREAM)
                        .retrieve()
                        .body(byte[].class);
        if (zip == null || zip.length < 4 || zip[0] != 'P' || zip[1] != 'K') {
            throw new IllegalStateException("TfNSW returned an invalid GTFS ZIP for " + feedKey);
        }
        return zip;
    }

    public long downloadTo(String feedKey, Path destination) {
        String path = path(feedKey);
        log.info(
                "event=timetable_provider_request method=GET feed={} uri={} accept={}"
                        + " authorizationHeader=omitted",
                feedKey,
                connection.baseUrl() + path,
                MediaType.APPLICATION_OCTET_STREAM);
        try {
            restClient
                    .get()
                    .uri(path)
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .exchange(
                            (request, response) -> {
                                if (!response.getStatusCode().is2xxSuccessful()) {
                                    throw new IllegalStateException(
                                            "TfNSW schedule download failed with HTTP "
                                                    + response.getStatusCode().value());
                                }
                                try (InputStream input = response.getBody();
                                        var output = Files.newOutputStream(destination)) {
                                    input.transferTo(output);
                                } catch (java.io.IOException exception) {
                                    throw new java.io.UncheckedIOException(exception);
                                }
                                return null;
                            });
            try (InputStream input = Files.newInputStream(destination)) {
                if (input.read() != 'P' || input.read() != 'K') {
                    throw new IllegalStateException(
                            "TfNSW returned an invalid GTFS ZIP for " + feedKey);
                }
            }
            long size = Files.size(destination);
            if (size < 4) {
                throw new IllegalStateException(
                        "TfNSW returned an invalid GTFS ZIP for " + feedKey);
            }
            return size;
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private String path(String feedKey) {
        String path =
                switch (feedKey) {
                    case "sydneytrains" -> properties.api().sydneyTrains();
                    case "metro" -> properties.api().metro();
                    case "replacementbus" -> properties.api().replacementBus();
                    case "pcbc" -> properties.api().pcbc();
                    default -> null;
                };
        if (path == null) throw new IllegalArgumentException("Unsupported TfNSW schedule feed");
        return path;
    }

    public record FeedHead(String lastModified) {}
}
