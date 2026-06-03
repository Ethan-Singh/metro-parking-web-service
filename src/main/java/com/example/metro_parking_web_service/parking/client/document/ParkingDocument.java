/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.document;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@CompoundIndex(
        name = "facilityId_sourceTimestamp_unique",
        def = "{'facilityId': 1, 'sourceTimestamp': 1}",
        unique = true)
@Data
public class ParkingDocument {

    @Id private String id;
    private int facilityId;
    private String facilityName;
    private int spots;
    private int occupancy;
    private LocalDateTime sourceTimestamp;
    @CreatedDate private LocalDateTime createdAt;
}
