/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.document;

import java.time.Instant;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Data
public class ParkingBackfillCursorDocument {

    @Id private String id = "cursor";
    private int lastFacilityId;
    private Instant updatedAt;
}
