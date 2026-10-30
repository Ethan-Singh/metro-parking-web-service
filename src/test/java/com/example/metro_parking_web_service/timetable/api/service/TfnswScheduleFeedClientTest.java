/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.metro_parking_web_service.provider.tfnsw.config.TfnswConnectionProperties;
import com.example.metro_parking_web_service.timetable.api.config.TimetableProviderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TfnswScheduleFeedClientTest {

    @Test
    void headRequestIncludesOctetStreamAcceptHeaderAndReturnsLastModified() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://provider.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TfnswScheduleFeedClient client =
                new TfnswScheduleFeedClient(
                        builder.build(),
                        new TimetableProviderProperties(
                                new TimetableProviderProperties.Api("/trains", "/metro"), true),
                        new TfnswConnectionProperties("https://provider.test", ""));

        server.expect(requestTo("https://provider.test/trains"))
                .andExpect(method(HttpMethod.HEAD))
                .andExpect(header(HttpHeaders.ACCEPT, "application/octet-stream"))
                .andRespond(
                        withSuccess()
                                .header(
                                        HttpHeaders.LAST_MODIFIED,
                                        "Fri, 30 Oct 2026 06:35:12 GMT"));

        assertThat(client.head("sydneytrains").lastModified())
                .isEqualTo("Fri, 30 Oct 2026 06:35:12 GMT");
        server.verify();
    }

    @Test
    void downloadsZipWithExpectedAcceptHeader() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://provider.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TfnswScheduleFeedClient client = client(builder.build());
        byte[] zip = {'P', 'K', 3, 4};

        server.expect(requestTo("https://provider.test/metro"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, "application/octet-stream"))
                .andRespond(withSuccess(zip, MediaType.APPLICATION_OCTET_STREAM));

        assertThat(client.download("metro")).containsExactly(zip);
        server.verify();
    }

    @Test
    void rejectsNonZipProviderResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://provider.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TfnswScheduleFeedClient client = client(builder.build());
        server.expect(requestTo("https://provider.test/metro"))
                .andRespond(withSuccess("not a zip", MediaType.APPLICATION_OCTET_STREAM));

        assertThatThrownBy(() -> client.download("metro"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TfNSW returned an invalid GTFS ZIP for metro");
        server.verify();
    }

    @Test
    void rejectsUnknownProviderFeedKey() {
        TfnswScheduleFeedClient client = client(RestClient.builder().build());

        assertThatThrownBy(() -> client.head("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported TfNSW schedule feed");
    }

    private TfnswScheduleFeedClient client(RestClient restClient) {
        return new TfnswScheduleFeedClient(
                restClient,
                new TimetableProviderProperties(
                        new TimetableProviderProperties.Api("/trains", "/metro"), true),
                new TfnswConnectionProperties("https://provider.test", ""));
    }
}
