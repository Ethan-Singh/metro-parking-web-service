/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.document;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public class ParkingDocument {

    @Id private String facilityId;
    private String facilityName;
    private String spots;
    private String occupancy;
    private LocalDateTime messageDate;
    private UUID eventId;
    private Instant timestamp;
}
