/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.client;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class TfnswRealtimeAlertClient {

    private final RestClient restClient;
    private final TfnswConnectionProperties connection;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TfnswRealtimeAlertClient(
            @Qualifier("timetableProviderRestClient") RestClient restClient,
            TfnswConnectionProperties connection) {
        this.restClient = restClient;
        this.connection = connection;
    }

    public JsonNode fetch(String feedKey, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("TfNSW realtime alert path is not configured");
        }
        log.info(
                "event=timetable_realtime_provider_request method=GET feed={} uri={}"
                        + " accept={} authorizationHeader=omitted",
                feedKey,
                connection.baseUrl() + path + "?format=json",
                MediaType.APPLICATION_JSON_VALUE);
        byte[] response =
                restClient
                        .get()
                        .uri(
                                uriBuilder ->
                                        uriBuilder.path(path).queryParam("format", "json").build())
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(byte[].class);
        if (response == null || response.length == 0) {
            throw new IllegalStateException("TfNSW returned an invalid realtime alert feed");
        }
        try {
            JsonNode json = objectMapper.readTree(response);
            if (json == null || !json.path("header").isObject()) {
                throw new IllegalStateException("TfNSW returned an invalid realtime alert feed");
            }
            return json;
        } catch (IOException exception) {
            throw new IllegalStateException("TfNSW alert response was not JSON", exception);
        }
    }
}
