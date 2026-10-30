/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.timetable.api.service.TimetableReadService;
import com.example.metro_parking_web_service.timetable.api.service.TimetableReadService.Representation;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimetableControllerTest {

    private final TimetableReadService readService = mock(TimetableReadService.class);
    private TimetableController controller;

    @BeforeEach
    void setUp() {
        controller = new TimetableController(readService);
    }

    @Test
    void returnsManifestWithEtagAndJsonContentType() {
        when(readService.getManifest()).thenReturn(representation("{}", "\"manifest\"", null));

        var response = controller.getManifest(null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"manifest\"");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/json");
    }

    @Test
    void returnsNotModifiedWhenStrongOrWeakEtagMatches() {
        when(readService.getManifest()).thenReturn(representation("{}", "\"manifest\"", null));

        var response = controller.getManifest("W/\"manifest\"");

        assertThat(response.getStatusCode().value()).isEqualTo(304);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void returnsGzipBytesWhenClientAcceptsGzip() throws Exception {
        byte[] compressed = gzip("{\"trips\":[]}");
        when(readService.getPackage(LocalDate.of(2026, 10, 30), "SYDNEY_TRAINS"))
                .thenReturn(new Representation(compressed, "\"package\"", "gzip"));

        var response =
                controller.getPackage(
                        LocalDate.of(2026, 10, 30), "SYDNEY_TRAINS", null, "br, gzip");

        assertThat(response.getHeaders().getFirst("Content-Encoding")).isEqualTo("gzip");
        assertThat(response.getBody()).containsExactly(compressed);
    }

    @Test
    void decompressesForClientWithoutGzipAndMakesEtagWeak() throws Exception {
        byte[] compressed = gzip("{\"trips\":[]}");
        when(readService.getPackage(LocalDate.of(2026, 10, 30), null))
                .thenReturn(new Representation(compressed, "\"package\"", "gzip"));

        var response = controller.getPackage(LocalDate.of(2026, 10, 30), null, null, "identity");

        assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"trips\":[]}");
        assertThat(response.getHeaders().getETag()).isEqualTo("W/\"package\"");
    }

    @Test
    void rejectsCorruptStoredGzipRepresentation() {
        when(readService.getPackage(LocalDate.of(2026, 10, 30), null))
                .thenReturn(new Representation(new byte[] {1, 2}, "\"package\"", "gzip"));

        assertThatThrownBy(
                        () ->
                                controller.getPackage(
                                        LocalDate.of(2026, 10, 30), null, null, "identity"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored timetable package is not valid gzip data");
    }

    private Representation representation(String body, String etag, String encoding) {
        return new Representation(body.getBytes(StandardCharsets.UTF_8), etag, encoding);
    }

    private byte[] gzip(String body) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}
