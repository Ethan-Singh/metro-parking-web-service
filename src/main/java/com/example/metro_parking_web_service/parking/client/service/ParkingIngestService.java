/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import com.example.metro_parking_web_service.parking.client.dto.Parking;
import com.example.metro_parking_web_service.parking.client.dto.ParkingDocumentMapper;
import com.example.metro_parking_web_service.parking.client.dto.ParkingIdStrategy;
import com.example.metro_parking_web_service.parking.client.dto.ParkingResponseMapper;
import com.example.metro_parking_web_service.parking.client.repository.ParkingRepository;
import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingIngestService {

    private final ParkingPolicy parkingPolicy;
    private final ParkingResponseMapper parkingResponseMapper;
    private final ParkingDocumentMapper parkingDocumentMapper;
    private final ParkingIdStrategy parkingIdStrategy;
    private final ParkingRepository parkingRepository;

    public void ingest(List<ParkingResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            log.warn("ingest.skip responses={}", responses == null ? "null" : "empty");
            return;
        }

        List<Parking> filtered =
                responses.stream()
                        .map(parkingResponseMapper::toParking)
                        .filter(Objects::nonNull)
                        .filter(parkingPolicy::isParkingAllowed)
                        .toList();

        if (filtered.isEmpty()) {
            log.warn("ingest.filtered.empty originalSize={}", responses.size());
            return;
        }

        save(filtered);
    }

    void save(List<Parking> parkingList) {
        List<ParkingDocument> documents =
                parkingList.stream()
                        .map(
                                parking -> {
                                    ParkingDocument doc =
                                            parkingDocumentMapper.toParkingDocument(parking);
                                    if (doc == null) {
                                        log.warn(
                                                "parkingDocumentMapper.null facilityId={}"
                                                        + " sourceTimestamp={}",
                                                parking.facilityId(),
                                                parking.sourceTimestamp());
                                        return null;
                                    }
                                    doc.setId(
                                            parkingIdStrategy.generateId(
                                                    parking.facilityId(),
                                                    parking.sourceTimestamp()));
                                    return doc;
                                })
                        .filter(Objects::nonNull)
                        .toList();

        if (documents.isEmpty()) {
            log.warn("save.noValidDocuments inputSize={}", parkingList.size());
            return;
        }

        parkingRepository.saveAll(documents);
        log.info("save.complete inputSize={} savedSize={}", parkingList.size(), documents.size());
    }
}
