/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.metro_parking_web_service.parking.api.dto.HistoryResolution;
import com.example.metro_parking_web_service.parking.api.dto.ParkingHistoryPayload;
import com.example.metro_parking_web_service.parking.api.service.ParkingQueryService;
import com.example.metro_parking_web_service.parking.ingestion.config.ParkingPolicy;
import com.example.metro_parking_web_service.parking.ingestion.document.ParkingDay;
import com.example.metro_parking_web_service.parking.ingestion.repository.ParkingDayRepository;
import com.example.metro_parking_web_service.parking.ingestion.service.ParkingDayIngestService;
import com.example.metro_parking_web_service.parking.ingestion.service.ParkingFacilityCatalog;
import com.example.metro_parking_web_service.parking.provider.dto.CarParkApiRecord;
import com.example.metro_parking_web_service.parking.provider.dto.CarParkApiRecordMapper;
import com.example.metro_parking_web_service.parking.provider.service.CarParkApiClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
    @Autowired private ParkingFacilityCatalog parkingFacilityCatalog;
    @MockitoBean private CarParkApiClient parkingClient;

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

        when(parkingClient.fetchFacilities()).thenReturn(Map.of("42", "Central Station"));
        assertThat(parkingFacilityCatalog.refresh()).isTrue();
        ParkingHistoryPayload response =
                queryService.getHistoryPayload(
                        "central-station", sydneyDate, sydneyDate, HistoryResolution.AUTO);

        assertThat(response.resolution()).isEqualTo(HistoryResolution.TEN_MINUTE);
        assertThat(response.points()).hasSize(1);
        assertThat(response.points().getFirst().get(1)).isEqualTo(0.55);
        assertThat(response.points().getFirst().get(2)).isEqualTo(45);
        assertThat(response.points().getFirst().getFirst()).isInstanceOf(Long.class);
    }

    @Test
    void catalogRefreshMakesNewFacilitiesAppearWithoutRestarting() {
        LocalDate sydneyDate = LocalDate.now(ZoneId.of("Australia/Sydney")).minusDays(1);
        ingestService.ingest(
                List.of(
                        record(42, "Central Station", sydneyDate, 9, 11, "62"),
                        record(486, "Park&Ride - Ashfield", sydneyDate, 9, 11, "175")));
        when(parkingClient.fetchFacilities())
                .thenReturn(
                        Map.of("42", "Central Station"),
                        Map.of("42", "Central Station", "486", "Park&Ride - Ashfield"));

        assertThat(parkingFacilityCatalog.refresh()).isTrue();
        assertThat(queryService.getAllLiveCarParks())
                .extracting(response -> response.facilityName())
                .containsExactly("Central Station");

        assertThat(parkingFacilityCatalog.refresh()).isTrue();
        assertThat(queryService.getAllLiveCarParks())
                .extracting(response -> response.facilityName())
                .containsExactly("Central Station", "Park&Ride - Ashfield");
        assertThat(queryService.getLiveCarPark("park-ride-ashfield").facilityName())
                .isEqualTo("Park&Ride - Ashfield");
    }

    @Test
    void storesHistorySampleByMessageDateWhenItFallsAfterRequestedEventDate() {
        LocalDate eventDate = LocalDate.now(ZoneId.of("Australia/Sydney")).minusDays(1);
        LocalDate messageDate = eventDate.plusDays(1);
        CarParkApiRecord historySample = record(messageDate, 9, 48, "175");

        // eventDate is only the upstream query key; the record's MessageDate determines storage.
        ingestService.ingest(List.of(historySample));

        ParkingDay storedDay =
                parkingDayRepository
                        .findByFacilityIdAndSydneyDate(42, messageDate.toString())
                        .orElseThrow();
        assertThat(storedDay.getSamples()).hasSize(1);
        assertThat(storedDay.getSamples().getFirst().occupancy()).isEqualTo(175);
        assertThat(parkingDayRepository.findByFacilityIdAndSydneyDate(42, eventDate.toString()))
                .isEmpty();
    }

    @Test
    void storesHistorySampleByMessageDateWhenItFallsBeforeRequestedEventDate() {
        LocalDate eventDate = LocalDate.now(ZoneId.of("Australia/Sydney"));
        LocalDate messageDate = eventDate.minusDays(1);
        CarParkApiRecord historySample = record(messageDate, 23, 48, "173");

        // A mismatched eventdate must not redirect this sample from its MessageDate.
        ingestService.ingest(List.of(historySample));

        ParkingDay storedDay =
                parkingDayRepository
                        .findByFacilityIdAndSydneyDate(42, messageDate.toString())
                        .orElseThrow();
        assertThat(storedDay.getSamples()).hasSize(1);
        assertThat(storedDay.getSamples().getFirst().occupancy()).isEqualTo(173);
        assertThat(parkingDayRepository.findByFacilityIdAndSydneyDate(42, eventDate.toString()))
                .isEmpty();
    }

    private CarParkApiRecord record(LocalDate date, int hour, int minute, String occupancy) {
        return record(42, "Central Station", date, hour, minute, occupancy);
    }

    private CarParkApiRecord record(
            int facilityId,
            String facilityName,
            LocalDate date,
            int hour,
            int minute,
            String occupancy) {
        return new CarParkApiRecord(
                Integer.toString(facilityId),
                facilityName,
                "100",
                new CarParkApiRecord.Occupancy(occupancy),
                LocalDateTime.of(
                        date.getYear(), date.getMonth(), date.getDayOfMonth(), hour, minute));
    }
}
