/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("parkingDay")
@CompoundIndex(
        name = "facility_sydney_date_unique",
        def = "{'facilityId': 1, 'sydneyDate': 1}",
        unique = true)
@Data
public class ParkingDay {

    @Id private String id;
    private int facilityId;
    private String facilityName;
    private String sydneyDate;
    private List<ParkingSample> samples = new ArrayList<>();
    private BackfillState backfillState;
    private Instant retryAfter;
    private Instant leaseUntil;
    private Instant completedAt;
    private int retryCount;

    @Version private Long version;

    public enum BackfillState {
        PROCESSING,
        RETRY_WAIT,
        COMPLETE
    }

    public record ParkingSample(int bucketIndex, Instant sourceTime, int occupancy, int capacity) {}
}
