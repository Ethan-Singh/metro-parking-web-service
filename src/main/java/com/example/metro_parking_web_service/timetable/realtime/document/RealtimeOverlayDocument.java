/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.document;

import java.time.Instant;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Atomically published normalized realtime overlay. */
@Document("timetableRealtimeOverlay")
@Data
public class RealtimeOverlayDocument {

    @Id private String id = "active";
    private String overlayVersion;
    private String etag;
    private Instant generatedAt;
    private String payloadJson;
}
