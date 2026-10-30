/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.document;

import java.time.Instant;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Last successfully published revision for one provider schedule feed. */
@Document("timetableFeedState")
@Data
public class TimetableFeedStateDocument {

    @Id private String feedKey;
    private String providerLastModified;
    private String checksum;
    private Instant retrievedAt;
}
