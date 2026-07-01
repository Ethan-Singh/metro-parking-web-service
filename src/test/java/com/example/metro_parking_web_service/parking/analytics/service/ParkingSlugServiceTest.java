/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.parking.analytics.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.metro_parking_web_service.parking.client.document.ParkingDocument;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class ParkingSlugServiceTest {

    @Mock private MongoTemplate mongoTemplate;

    private ParkingSlugService slugService;

    @BeforeEach
    void setUp() {
        slugService = new ParkingSlugService(mongoTemplate);
    }

    @Test
    void shouldGenerateSlugCorrectly() {
        String input = "Park & Ride - Ashfield";

        String result = slugService.toSlug(input);

        assertThat(result).isEqualTo("park-ride-ashfield");
    }

    @Test
    void shouldReturnEmptySlugForNull() {
        assertThat(slugService.toSlug(null)).isEqualTo("");
    }

    @Test
    void shouldResolveFacilityIdFromSlug_andPopulateCache() {
        String slug = "test-facility";

        when(mongoTemplate.findDistinct(
                        any(Query.class),
                        eq("facilityName"),
                        eq(ParkingDocument.class),
                        eq(String.class)))
                .thenReturn(List.of("Test Facility"));

        ParkingDocument doc = new ParkingDocument();
        doc.setFacilityId(42);

        when(mongoTemplate.findOne(any(Query.class), eq(ParkingDocument.class))).thenReturn(doc);

        int result = slugService.facilityIdFromSlug(slug);

        assertThat(result).isEqualTo(42);

        int cachedResult = slugService.facilityIdFromSlug(slug);

        assertThat(cachedResult).isEqualTo(42);

        verify(mongoTemplate, times(1))
                .findDistinct(
                        any(Query.class),
                        eq("facilityName"),
                        eq(ParkingDocument.class),
                        eq(String.class));
    }

    @Test
    void shouldThrowWhenSlugNotFound() {
        String slug = "unknown";

        when(mongoTemplate.findDistinct(
                        any(Query.class),
                        eq("facilityName"),
                        eq(ParkingDocument.class),
                        eq(String.class)))
                .thenReturn(List.of("Some Facility"));

        ParkingDocument doc = new ParkingDocument();
        doc.setFacilityId(1);

        when(mongoTemplate.findOne(any(Query.class), eq(ParkingDocument.class))).thenReturn(doc);

        assertThatThrownBy(() -> slugService.facilityIdFromSlug(slug))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown facility slug: unknown");
    }
}
