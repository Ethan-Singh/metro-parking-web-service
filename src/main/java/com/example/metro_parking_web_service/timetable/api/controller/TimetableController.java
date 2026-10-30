/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.controller;

import com.example.metro_parking_web_service.timetable.api.service.TimetableReadService;
import com.example.metro_parking_web_service.timetable.api.service.TimetableReadService.Representation;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/timetable")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableReadService timetableReadService;

    @GetMapping(value = "/manifest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> getManifest(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                    String ifNoneMatch) {
        return response(timetableReadService.getManifest(), ifNoneMatch, false);
    }

    @GetMapping(value = "/packages/{travelDate}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> getPackage(
            @PathVariable LocalDate travelDate,
            @RequestParam(required = false) String offering,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            @RequestHeader(value = HttpHeaders.ACCEPT_ENCODING, required = false)
                    String acceptEncoding) {
        Representation representation = timetableReadService.getPackage(travelDate, offering);
        boolean acceptsGzip = acceptsGzip(acceptEncoding);
        return response(representation, ifNoneMatch, acceptsGzip);
    }

    private ResponseEntity<byte[]> response(
            Representation representation, String ifNoneMatch, boolean acceptGzip) {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(representation.etag());
        headers.setCacheControl(CacheControl.noCache().cachePrivate());
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (matches(ifNoneMatch, representation.etag())) {
            return new ResponseEntity<>(headers, org.springframework.http.HttpStatus.NOT_MODIFIED);
        }

        byte[] body = representation.body();
        if (representation.contentEncoding() != null
                && representation.contentEncoding().equalsIgnoreCase("gzip")) {
            headers.add(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
            if (acceptGzip) {
                headers.set(HttpHeaders.CONTENT_ENCODING, "gzip");
            } else {
                body = gunzip(body);
                headers.setETag(weakEtag(representation.etag()));
            }
        }
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private boolean acceptsGzip(String acceptEncoding) {
        if (acceptEncoding == null) return false;
        return Arrays.stream(acceptEncoding.split(","))
                .map(String::trim)
                .anyMatch(
                        value ->
                                value.equalsIgnoreCase("gzip")
                                        || value.toLowerCase().startsWith("gzip;"));
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

    private String weakEtag(String etag) {
        return etag.startsWith("W/") ? etag : "W/" + etag;
    }

    private String stripWeakPrefix(String etag) {
        return etag.startsWith("W/") ? etag.substring(2) : etag;
    }

    private byte[] gunzip(byte[] compressed) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            gzip.transferTo(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Stored timetable package is not valid gzip data", exception);
        }
    }
}
