/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.client.service;

import com.example.metro_parking_web_service.parking.client.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import com.example.metro_parking_web_service.parking.client.dto.Parking;
import com.example.metro_parking_web_service.parking.client.dto.ParkingDocumentMapper;
import com.example.metro_parking_web_service.parking.client.dto.ParkingIdStrategy;
import com.example.metro_parking_web_service.parking.client.repository.ParkingRepository;
import com.example.metro_parking_web_service.parking.server.dto.ParkingResponse;
import com.example.metro_parking_web_service.parking.server.dto.ParkingResponseMapper;
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
            log.warn(
                    "event=parking_ingest decision=skip reason={}",
                    responses == null ? "null_responses" : "empty_responses");
            return;
        }

        List<Parking> filtered =
                responses.stream()
                        .map(parkingResponseMapper::toParking)
                        .filter(Objects::nonNull)
                        .filter(parkingPolicy::isParkingFacilityAllowed)
                        .toList();

        if (filtered.isEmpty()) {
            log.warn(
                    "event=parking_ingest decision=skip reason=no_eligible_records inputSize={}",
                    responses.size());
            return;
        }

        log.info(
                "event=parking_ingest decision=save inputSize={} filteredSize={}",
                responses.size(),
                filtered.size());
        save(filtered);
    }

    void save(List<Parking> parkings) {
        List<ParkingDocument> documents =
                parkings.stream()
                        .map(
                                parking -> {
                                    ParkingDocument doc =
                                            parkingDocumentMapper.toParkingDocument(parking);
                                    if (doc == null) {
                                        log.warn(
                                                "event=parking_document_mapping decision=skip"
                                                    + " reason=mapper_returned_null facilityId={}"
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
            log.warn(
                    "event=parking_save decision=skip reason=no_valid_documents inputSize={}",
                    parkings.size());
            return;
        }

        parkingRepository.saveAll(documents);
        log.info(
                "event=parking_save decision=success inputSize={} savedSize={}",
                parkings.size(),
                documents.size());
    }
}
