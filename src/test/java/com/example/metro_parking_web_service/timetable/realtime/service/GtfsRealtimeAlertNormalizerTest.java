/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GtfsRealtimeAlertNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GtfsRealtimeAlertNormalizer normalizer = new GtfsRealtimeAlertNormalizer();

    @Test
    void normalizesAlertFieldsTranslationsTargetsAndEpochTimes() throws Exception {
        var feed =
                objectMapper.readTree(
                        """
                        {
                          "header": {"gtfsRealtimeVersion":"2.0","incrementality":"FULL_DATASET","timestamp":"1793182717"},
                          "entity": [
                            {"id":"alert-1","alert":{
                              "activePeriod":[{"start":"1793166120"}],
                              "informedEntity":[{"agencyId":"SydneyTrains","routeId":"NTH_1a","directionId":1}],
                              "effect":"SIGNIFICANT_DELAYS",
                              "headerText":{"translation":[{"text":"Train delays","language":"en"},{"text":"<p>Train delays</p>","language":"en/html"}]},
                              "descriptionText":{"translation":[{"text":"Allow extra time","language":"en"}]},
                              "url":{"translation":[{"text":"https://transportnsw.info/alerts/example","language":"en"}]}
                            }},
                            {"id":"deleted-1","isDeleted":true,"alert":{"effect":"NO_SERVICE"}}
                          ]
                        }
                        """);

        var result = normalizer.normalize("alerts-sydneytrains", feed);

        assertThat(result.protocolVersion()).isEqualTo("2.0");
        assertThat(result.incrementality()).isEqualTo("FULL_DATASET");
        assertThat(result.providerGeneratedAt()).isEqualTo(Instant.ofEpochSecond(1793182717));
        assertThat(result.alerts()).hasSize(1);
        var alert = result.alerts().getFirst();
        assertThat(alert.path("id").asText()).isEqualTo("alert-1");
        assertThat(alert.path("feedKey").asText()).isEqualTo("alerts-sydneytrains");
        assertThat(alert.path("cause").asText()).isEqualTo("UNKNOWN_CAUSE");
        assertThat(alert.path("effect").asText()).isEqualTo("SIGNIFICANT_DELAYS");
        assertThat(alert.path("header").asText()).isEqualTo("Train delays");
        assertThat(alert.path("description").asText()).isEqualTo("Allow extra time");
        assertThat(alert.path("url").asText())
                .isEqualTo("https://transportnsw.info/alerts/example");
        assertThat(alert.path("activePeriods").get(0).path("startsAt").asText())
                .isEqualTo(Instant.ofEpochSecond(1793166120).toString());
        assertThat(alert.path("activePeriods").get(0).has("endsAt")).isFalse();
        assertThat(alert.path("informedEntities").get(0).path("routeId").asText())
                .isEqualTo("NTH_1a");
        assertThat(alert.path("informedEntities").get(0).path("directionId").asInt()).isEqualTo(1);
    }

    @Test
    void rejectsDifferentialFeedUntilMergeSemanticsAreImplemented() throws Exception {
        var feed =
                objectMapper.readTree(
                        """
                        {"header":{"gtfsRealtimeVersion":"2.0","incrementality":"DIFFERENTIAL"},"entity":[]}
                        """);

        assertThatThrownBy(() -> normalizer.normalize("alerts-metro", feed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Differential GTFS-Realtime alert feeds are not supported yet");
    }

    @Test
    void rejectsInvalidUnixTimestamp() throws Exception {
        var feed =
                objectMapper.readTree(
                        """
                        {"header":{"gtfsRealtimeVersion":"2.0","incrementality":"FULL_DATASET","timestamp":"bad"},"entity":[]}
                        """);

        assertThatThrownBy(() -> normalizer.normalize("alerts-metro", feed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("GTFS-Realtime timestamp is not Unix seconds");
    }
}
