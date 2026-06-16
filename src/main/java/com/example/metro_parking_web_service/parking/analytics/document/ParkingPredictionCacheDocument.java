/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.document;

import com.example.metro_parking_web_service.parking.analytics.dto.PredictionPoint;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Data
public class ParkingPredictionCacheDocument {

    @Id private String id;
    private String slug;
    private List<PredictionPoint> predictions;
    private LocalDateTime cachedAt;
    private LocalDateTime validUntil;
}
