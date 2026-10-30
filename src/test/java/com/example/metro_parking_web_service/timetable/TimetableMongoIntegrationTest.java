/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.metro_parking_web_service.timetable.api.document.TimetableManifestDocument;
import com.example.metro_parking_web_service.timetable.api.document.TimetablePackageDocument;
import com.example.metro_parking_web_service.timetable.api.repository.TimetableManifestRepository;
import com.example.metro_parking_web_service.timetable.api.repository.TimetablePackageRepository;
import com.example.metro_parking_web_service.timetable.api.service.TimetableSnapshotPublisher;
import com.example.metro_parking_web_service.timetable.api.service.TimetableSnapshotPublisher.SnapshotCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TimetableMongoIntegrationTest {

    private static final String DATABASE =
            "timetable_it_" + UUID.randomUUID().toString().replace("-", "");
    private static final LocalDate TRAVEL_DATE = LocalDate.of(2026, 10, 30);

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE);
        properties.add("spring.data.mongodb.auto-index-creation", () -> "true");
    }

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private TimetableSnapshotPublisher publisher;
    @Autowired private TimetableManifestRepository manifestRepository;
    @Autowired private TimetablePackageRepository packageRepository;
    @Autowired private MockMvc mockMvc;

    @AfterEach
    void clearTestDatabase() {
        if (DATABASE.equals(mongoTemplate.getDb().getName())) {
            mongoTemplate.getDb().drop();
        }
    }

    @Test
    void publishesSnapshotAndServesManifestAndCompressedDatePackage() throws Exception {
        assertThat(mongoTemplate.getDb().getName()).isEqualTo(DATABASE);
        publisher.publish(candidate("snapshot-1", "\"manifest-v1\"", "\"package-v1\""));

        TimetableManifestDocument active = manifestRepository.findById("active").orElseThrow();
        assertThat(active.getSnapshotVersion()).isEqualTo("snapshot-1");
        assertThat(active.getEtag()).isEqualTo("\"manifest-v1\"");
        assertThat(packageRepository.findById("snapshot-1#2026-10-30#ALL")).isPresent();

        mockMvc.perform(get("/api/v1/timetable/manifest"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"manifest-v1\""));

        mockMvc.perform(
                        get("/api/v1/timetable/packages/2026-10-30")
                                .header("Accept-Encoding", "gzip"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"package-v1\""))
                .andExpect(header().string("Content-Encoding", "gzip"));

        mockMvc.perform(
                        get("/api/v1/timetable/packages/2026-10-30")
                                .header("If-None-Match", "\"package-v1\""))
                .andExpect(status().isNotModified());
    }

    @Test
    void publishingNewSnapshotKeepsPreviousVersionAndMovesActiveManifest() throws Exception {
        publisher.publish(candidate("snapshot-1", "\"manifest-v1\"", "\"package-v1\""));
        publisher.publish(candidate("snapshot-2", "\"manifest-v2\"", "\"package-v2\""));

        assertThat(manifestRepository.findById("active").orElseThrow().getSnapshotVersion())
                .isEqualTo("snapshot-2");
        assertThat(packageRepository.findById("snapshot-1#2026-10-30#ALL")).isPresent();
        assertThat(packageRepository.findById("snapshot-2#2026-10-30#ALL")).isPresent();
    }

    @Test
    void rejectsMalformedTravelDateAndUnsupportedOfferingAsBadRequests() throws Exception {
        mockMvc.perform(get("/api/v1/timetable/packages/not-a-date"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        get("/api/v1/timetable/packages/2026-10-30")
                                .param("offering", "NOT_AN_OFFERING"))
                .andExpect(status().isBadRequest());
    }

    private SnapshotCandidate candidate(String version, String manifestEtag, String packageEtag)
            throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("snapshotVersion", version);
        manifest.put("timezone", "Australia/Sydney");
        ObjectNode packagePayload = mapper.createObjectNode();
        packagePayload.put("packageVersion", version);
        packagePayload.put("travelDate", TRAVEL_DATE.toString());
        packagePayload.put("timezone", "Australia/Sydney");
        packagePayload.putArray("stops");
        packagePayload.putArray("routes");
        packagePayload.putArray("trips");
        packagePayload.putArray("calls");

        TimetablePackageDocument packageDocument = new TimetablePackageDocument();
        packageDocument.setSnapshotVersion(version);
        packageDocument.setTravelDate(TRAVEL_DATE);
        packageDocument.setOffering("ALL");
        packageDocument.setEtag(packageEtag);
        packageDocument.setContentEncoding("gzip");
        packageDocument.setGeneratedAt(Instant.now());
        packageDocument.setPayload(gzip(mapper.writeValueAsBytes(packagePayload)));

        return new SnapshotCandidate(version, manifestEtag, manifest, List.of(packageDocument));
    }

    private byte[] gzip(byte[] payload) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
            gzip.write(payload);
        }
        return buffer.toByteArray();
    }
}
