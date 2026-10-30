/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import com.example.metro_parking_web_service.timetable.api.config.TimetableProviderProperties;
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
