/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.repository;

import com.example.metro_parking_web_service.timetable.realtime.document.RealtimeOverlayDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RealtimeOverlayRepository
        extends MongoRepository<RealtimeOverlayDocument, String> {}
