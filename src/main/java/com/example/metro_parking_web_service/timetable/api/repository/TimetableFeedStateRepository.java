/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.repository;

import com.example.metro_parking_web_service.timetable.api.document.TimetableFeedStateDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TimetableFeedStateRepository
        extends MongoRepository<TimetableFeedStateDocument, String> {}
