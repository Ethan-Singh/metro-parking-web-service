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

class TfnswRealtimeAlertClientTest {

    @Test
    void requestsJsonRepresentationAndParsesJsonEvenWithProviderBinaryContentType() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://provider.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TfnswRealtimeAlertClient client =
                new TfnswRealtimeAlertClient(
                        builder.build(),
                        new TfnswConnectionProperties("https://provider.test", "apikey secret"));
        String json = "{\"header\":{\"gtfsRealtimeVersion\":\"2.0\"},\"entity\":[]}";

        server.expect(requestTo("https://provider.test/v2/gtfs/alerts/metro?format=json"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, "application/json"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_OCTET_STREAM));

        assertThat(client.fetch("alerts-metro", "/v2/gtfs/alerts/metro").path("entity")).isEmpty();
        server.verify();
    }

    @Test
    void rejectsMissingProviderPathBeforeMakingRequest() {
        TfnswRealtimeAlertClient client =
                new TfnswRealtimeAlertClient(
                        RestClient.builder().build(),
                        new TfnswConnectionProperties("https://provider.test", ""));

        assertThatThrownBy(() -> client.fetch("alerts-metro", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TfNSW realtime alert path is not configured");
    }

    @Test
    void rejectsResponseWithoutRealtimeHeader() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://provider.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TfnswRealtimeAlertClient client =
                new TfnswRealtimeAlertClient(
                        builder.build(),
                        new TfnswConnectionProperties("https://provider.test", ""));
        server.expect(requestTo("https://provider.test/alerts?format=json"))
                .andRespond(withSuccess("{\"entity\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetch("alerts-metro", "/alerts"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TfNSW returned an invalid realtime alert feed");
        server.verify();
    }
}
