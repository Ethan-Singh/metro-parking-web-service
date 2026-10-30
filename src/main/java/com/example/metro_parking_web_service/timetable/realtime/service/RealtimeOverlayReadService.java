/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.service;

import com.example.metro_parking_web_service.timetable.realtime.repository.RealtimeOverlayRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimeOverlayReadService {

    private final RealtimeOverlayRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Representation getOverlay() {
        return repository
                .findById("active")
                .filter(document -> document.getPayloadJson() != null)
                .map(
                        document -> {
                            return new Representation(
                                    document.getPayloadJson()
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                    document.getEtag());
                        })
                .orElseGet(this::emptyOverlay);
    }

    private Representation emptyOverlay() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("schemaVersion", "1");
        payload.put("overlayVersion", "unavailable");
        payload.put("generatedAt", java.time.Instant.now().toString());
        payload.putArray("sources");
        payload.putArray("alerts");
        payload.putArray("tripUpdates");
        payload.putArray("vehiclePositions");
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            return new Representation(body, '"' + sha256(body) + '"');
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Could not serialise empty realtime overlay", exception);
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Representation(byte[] body, String etag) {}
}
