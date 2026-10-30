/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.repository;

import com.example.metro_parking_web_service.timetable.api.document.TimetableManifestDocument;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TimetableManifestRepository
        extends MongoRepository<TimetableManifestDocument, String> {
    List<TimetableManifestDocument> findByIdStartingWithOrderByPublishedAtDesc(String prefix);
}
