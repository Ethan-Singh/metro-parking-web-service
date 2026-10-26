/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.metro_parking_web_service.parking.analytics.dto.HistoryResolution;
import com.example.metro_parking_web_service.parking.analytics.dto.ParkingHistoryPayload;
import com.example.metro_parking_web_service.parking.analytics.service.ParkingQueryService;
import com.example.metro_parking_web_service.parking.client.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.client.document.ParkingDay;
import com.example.metro_parking_web_service.parking.client.repository.ParkingDayRepository;
import com.example.metro_parking_web_service.parking.client.service.ParkingDayIngestService;
import com.example.metro_parking_web_service.parking.server.dto.CarParkApiRecord;
import com.example.metro_parking_web_service.parking.server.dto.CarParkApiRecordMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class ParkingMongoIntegrationTest {

    private static final String DATABASE =
            "metro_parking_it_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE);
        properties.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private ParkingDayRepository parkingDayRepository;
    @Autowired private ParkingDayIngestService ingestService;
    @Autowired private ParkingQueryService queryService;
    @Autowired private CarParkApiRecordMapper recordMapper;
    @Autowired private ParkingPolicy parkingPolicy;

    @AfterEach
    void clearTestDatabase() {
        if (DATABASE.equals(mongoTemplate.getDb().getName())) {
            mongoTemplate.getDb().drop();
        }
    }

    @Test
    void persistsDailySamplesAndServesCompactHistoryFromMongo() {
        assertThat(mongoTemplate.getDb().getName()).isEqualTo(DATABASE);
        LocalDate sydneyDate = LocalDate.now(ZoneId.of("Australia/Sydney")).minusDays(1);
        List<CarParkApiRecord> providerRecords =
                List.of(record(sydneyDate, 9, 11, "62"), record(sydneyDate, 9, 19, "55"));
        assertThat(parkingPolicy.isFacilityAllowed(42)).isTrue();
        assertThat(recordMapper.toParking(providerRecords.getFirst())).isNotNull();
        ingestService.ingest(providerRecords);

        List<ParkingDay> stored = parkingDayRepository.findAll();
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getSydneyDate()).isEqualTo(sydneyDate.toString());
        assertThat(stored.getFirst().getSamples()).hasSize(1);
        assertThat(stored.getFirst().getSamples().getFirst().occupancy()).isEqualTo(55);
        assertThat(
                        parkingDayRepository
                                .findByFacilityIdAndSydneyDateBetweenOrderBySydneyDateAsc(
                                        42, sydneyDate.toString(), sydneyDate.toString()))
                .hasSize(1);
        assertThat(mongoTemplate.indexOps(ParkingDay.class).getIndexInfo())
                .anyMatch(index -> index.getName().equals("facility_sydney_date_unique"));

        ParkingHistoryPayload response =
                queryService.getHistoryPayload(
                        "central-station", sydneyDate, sydneyDate, HistoryResolution.AUTO);

        assertThat(response.resolution()).isEqualTo(HistoryResolution.TEN_MINUTE);
        assertThat(response.points()).hasSize(1);
        assertThat(response.points().getFirst().get(1)).isEqualTo(0.55);
        assertThat(response.points().getFirst().get(2)).isEqualTo(45);
        assertThat(response.points().getFirst().getFirst()).isInstanceOf(Long.class);
    }

    private CarParkApiRecord record(LocalDate date, int hour, int minute, String occupancy) {
        return new CarParkApiRecord(
                "42",
                "Central Station",
                "100",
                new CarParkApiRecord.Occupancy(occupancy),
                LocalDateTime.of(
                        date.getYear(), date.getMonth(), date.getDayOfMonth(), hour, minute));
    }
}
