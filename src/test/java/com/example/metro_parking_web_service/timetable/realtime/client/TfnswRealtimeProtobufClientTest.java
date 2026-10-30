/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TfnswRealtimeProtobufClientTest {

    @Test
    void requestsAndReturnsBinaryProtobufBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://provider.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TfnswRealtimeProtobufClient client =
                new TfnswRealtimeProtobufClient(
                        builder.build(),
                        new TfnswConnectionProperties("https://provider.test", "apikey secret"));
        byte[] body = {10, 2, 49, 48};

        server.expect(requestTo("https://provider.test/v2/gtfs/realtime/metro"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, "application/x-google-protobuf"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_OCTET_STREAM));

        assertThat(client.fetch("trip-updates-metro", "/v2/gtfs/realtime/metro"))
                .containsExactly(body);
        server.verify();
    }

    @Test
    void rejectsMissingPathBeforeMakingRequest() {
        TfnswRealtimeProtobufClient client =
                new TfnswRealtimeProtobufClient(
                        RestClient.builder().build(),
                        new TfnswConnectionProperties("https://provider.test", ""));

        assertThatThrownBy(() -> client.fetch("vehicle-positions-metro", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TfNSW realtime protobuf path is not configured");
    }

    @Test
    void rejectsEmptyProviderResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://provider.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TfnswRealtimeProtobufClient client =
                new TfnswRealtimeProtobufClient(
                        builder.build(),
                        new TfnswConnectionProperties("https://provider.test", ""));
        server.expect(requestTo("https://provider.test/empty"))
                .andRespond(withSuccess(new byte[0], MediaType.APPLICATION_OCTET_STREAM));

        assertThatThrownBy(() -> client.fetch("trip-updates-metro", "/empty"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TfNSW returned an empty realtime protobuf feed");
        server.verify();
    }
}
