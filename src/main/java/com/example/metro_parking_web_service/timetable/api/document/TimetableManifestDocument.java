/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.document;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Active public timetable manifest. Its payload is the published API representation. */
@Document("timetableManifest")
@Data
public class TimetableManifestDocument {

    @Id private String id = "active";
    private String etag;
    private Instant publishedAt;
    private ObjectNode payload;
}
