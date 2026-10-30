/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.document;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Immutable, precompressed package for one published snapshot and travel date. */
@Document("timetablePackage")
@Data
public class TimetablePackageDocument {

    @Id private String id;
    private String snapshotVersion;
    private LocalDate travelDate;

    /** ALL or one public timetable offering key. */
    private String offering;

    private String etag;
    private String contentEncoding = "gzip";
    private Instant generatedAt;
    private byte[] payload;
}
