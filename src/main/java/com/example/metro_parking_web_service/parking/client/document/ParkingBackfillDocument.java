/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.document;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Data
public class ParkingBackfillDocument {

    @Id private int facilityId;
    private boolean backfillComplete;
    private LocalDate lastProcessedDate;
    private LocalDate backfillUntilDate;
    private Instant updatedAt;
    private boolean backfillInProgress;
}
