/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.client;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class TfnswRealtimeProtobufClient {

    private static final MediaType PROTOBUF =
            MediaType.parseMediaType("application/x-google-protobuf");

    private final RestClient restClient;
    private final TfnswConnectionProperties connection;

    public TfnswRealtimeProtobufClient(
            @Qualifier("timetableProviderRestClient") RestClient restClient,
            TfnswConnectionProperties connection) {
        this.restClient = restClient;
        this.connection = connection;
    }

    public byte[] fetch(String feedKey, String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("TfNSW realtime protobuf path is not configured");
        }
        log.info(
                "event=timetable_realtime_provider_request method=GET feed={} uri={}"
                        + " accept={} authorizationHeader=omitted",
                feedKey,
                connection.baseUrl() + path,
                PROTOBUF);
        byte[] response = restClient.get().uri(path).accept(PROTOBUF).retrieve().body(byte[].class);
        if (response == null || response.length == 0) {
            throw new IllegalStateException("TfNSW returned an empty realtime protobuf feed");
        }
        return response;
    }
}
