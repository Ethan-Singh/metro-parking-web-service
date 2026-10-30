/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.controller;

import com.example.metro_parking_web_service.timetable.realtime.service.RealtimeOverlayReadService;
import com.example.metro_parking_web_service.timetable.realtime.service.RealtimeOverlayReadService.Representation;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/timetable/realtime")
@RequiredArgsConstructor
public class RealtimeOverlayController {

    private final RealtimeOverlayReadService readService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> getOverlay(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                    String ifNoneMatch) {
        Representation representation = readService.getOverlay();
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(representation.etag());
        headers.setCacheControl(CacheControl.noCache().cachePrivate());
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (matches(ifNoneMatch, representation.etag())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_MODIFIED)
                    .headers(headers)
                    .build();
        }
        return ResponseEntity.ok().headers(headers).body(representation.body());
    }

    private boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) return false;
        return Arrays.stream(ifNoneMatch.split(","))
                .map(String::trim)
                .anyMatch(
                        candidate ->
                                candidate.equals("*")
                                        || stripWeakPrefix(candidate)
                                                .equals(stripWeakPrefix(etag)));
    }

    private String stripWeakPrefix(String etag) {
        return etag.startsWith("W/") ? etag.substring(2) : etag;
    }
}
