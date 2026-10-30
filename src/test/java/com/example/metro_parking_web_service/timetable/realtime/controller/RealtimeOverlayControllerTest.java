/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.timetable.realtime.service.RealtimeOverlayReadService;
import com.example.metro_parking_web_service.timetable.realtime.service.RealtimeOverlayReadService.Representation;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RealtimeOverlayControllerTest {

    private final RealtimeOverlayReadService readService = mock(RealtimeOverlayReadService.class);
    private RealtimeOverlayController controller;

    @BeforeEach
    void setUp() {
        controller = new RealtimeOverlayController(readService);
    }

    @Test
    void returnsOverlayAsPrivateRevalidatableJson() {
        when(readService.getOverlay())
                .thenReturn(
                        new Representation(
                                "{\"alerts\":[]}".getBytes(StandardCharsets.UTF_8), "\"rt-1\""));

        var response = controller.getOverlay(null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"rt-1\"");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/json");
        assertThat(response.getHeaders().getCacheControl()).contains("no-cache", "private");
    }

    @Test
    void returnsNotModifiedForMatchingWeakEtag() {
        when(readService.getOverlay())
                .thenReturn(new Representation("{}".getBytes(StandardCharsets.UTF_8), "\"rt-1\""));

        var response = controller.getOverlay("W/\"rt-1\"");

        assertThat(response.getStatusCode().value()).isEqualTo(304);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void supportsWildcardConditionalRequest() {
        when(readService.getOverlay())
                .thenReturn(new Representation("{}".getBytes(StandardCharsets.UTF_8), "\"rt-1\""));

        var response = controller.getOverlay("*");

        assertThat(response.getStatusCode().value()).isEqualTo(304);
    }
}
