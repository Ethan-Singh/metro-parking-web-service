/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GtfsDatePackageBuilderTest {

    @TempDir java.nio.file.Path temporaryDirectory;

    private final GtfsDatePackageBuilder builder = new GtfsDatePackageBuilder();

    @Test
    void includesPriorServiceDateTripsWhoseAfterMidnightCallsFallOnTravelDate() throws Exception {
        var feed = createFeed();

        var body = builder.build(feed, "sydneytrains", "SYDNEY_TRAINS", LocalDate.of(2026, 10, 30));

        assertThat(body.path("timezone").asText()).isEqualTo("Australia/Sydney");
        assertThat(body.withArray("trips")).hasSize(1);
        assertThat(body.withArray("trips").get(0).path("serviceDate").asText())
                .isEqualTo("2026-10-29");
        assertThat(body.withArray("calls").get(0).path("arrival").asText())
                .isEqualTo("2026-10-30T00:10+11:00");
        assertThat(body.withArray("stops").get(0).path("id").asText())
                .isEqualTo("sydneytrains:stop-1");
        assertThat(body.withArray("routes").get(0).path("shortName").asText()).isEqualTo("T2");
    }

    @Test
    void appliesAddedAndRemovedCalendarExceptions() throws Exception {
        var feed = createFeed();

        var body = builder.build(feed, "sydneytrains", "SYDNEY_TRAINS", LocalDate.of(2026, 10, 31));

        assertThat(body.withArray("trips"))
                .extracting(
                        trip ->
                                trip.path("providerId").asText()
                                        + "#"
                                        + trip.path("serviceDate").asText())
                .containsExactly("trip-2#2026-10-30");
    }

    @Test
    void failsClearlyWhenRequiredGtfsTableIsMissing() throws Exception {
        java.nio.file.Path feed = temporaryDirectory.resolve("missing-stops.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(feed))) {
            add(zip, "routes.txt", "route_id,agency_id\nr1,SydneyTrains\n");
        }

        assertThatThrownBy(
                        () ->
                                builder.build(
                                        feed,
                                        "sydneytrains",
                                        "SYDNEY_TRAINS",
                                        LocalDate.of(2026, 10, 30)))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("stops.txt");
    }

    @Test
    void normalisesReviewedReplacementBusTripFromCapturedProductionFeed() throws Exception {
        var body =
                builder.buildSupporting(
                        java.nio.file.Path.of(
                                "docs/apis/v1-gtfs/schedules/buses/replacementbus/"
                                        + "ReplacementBus_GTFS_PROD_20261028091300.zip"),
                        "SYDNEY_TRAINS",
                        LocalDate.of(2026, 11, 1),
                        "captured-revision",
                        SupportingSchedulePolicy.routes().getFirst());

        assertThat(body.withArray("offerings").size()).isEqualTo(1);
        assertThat(body.withArray("trips"))
                .anySatisfy(
                        trip -> {
                            assertThat(trip.path("providerId").asText()).isEqualTo("2757475");
                            assertThat(trip.path("serviceDate").asText()).isEqualTo("2026-11-01");
                            assertThat(trip.path("servicePurpose").asText())
                                    .isEqualTo("REPLACEMENT");
                            assertThat(trip.path("serviceContext").path("affectedSegment").asText())
                                    .isEqualTo("Leppington to Glenfield");
                        });
        assertThat(body.withArray("routes"))
                .anySatisfy(
                        route -> {
                            assertThat(route.path("providerId").asText()).isEqualTo("7063_9T2");
                            assertThat(route.path("shortName").asText()).isEqualTo("9T2");
                            assertThat(route.path("mode").asText()).isEqualTo("BUS");
                        });
        assertThat(body.withArray("calls"))
                .anySatisfy(
                        call -> {
                            assertThat(call.path("stopId").asText())
                                    .isEqualTo("replacementbus:217938");
                            assertThat(call.path("departure").asText())
                                    .isEqualTo("2026-11-01T08:07+11:00");
                        })
                .anySatisfy(
                        call -> {
                            assertThat(call.path("stopId").asText())
                                    .isEqualTo("replacementbus:216711");
                            assertThat(call.path("arrival").asText())
                                    .isEqualTo("2026-11-01T08:36+11:00");
                        });
        assertThat(body.withArray("sources").get(0).path("role").asText()).isEqualTo("SUPPORTING");
    }

    private java.nio.file.Path createFeed() throws Exception {
        java.nio.file.Path feed = temporaryDirectory.resolve("sample-gtfs.zip");
        Map<String, String> files =
                Map.of(
                        "stops.txt",
                        "stop_id,stop_name,stop_lat,stop_lon,location_type,parent_station,platform_code\n"
                            + "stop-1,Central, -33.883,151.206,0,,1\n",
                        "routes.txt",
                        "route_id,agency_id,route_short_name,route_long_name,route_type,route_color,route_text_color\n"
                            + "route-1,SydneyTrains,T2,Inner West,2,009999,FFFFFF\n",
                        "trips.txt",
                        "route_id,service_id,trip_id,direction_id,trip_headsign\n"
                                + "route-1,weekday,trip-1,0,Ashfield\n"
                                + "route-1,special,trip-2,0,Special service\n",
                        "calendar.txt",
                        "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n"
                            + "weekday,1,1,1,1,1,0,0,20261001,20261231\n",
                        "calendar_dates.txt",
                        "service_id,date,exception_type\n"
                                + "special,20261030,1\n"
                                + "weekday,20261030,2\n",
                        "stop_times.txt",
                        "trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type,drop_off_type,timepoint\n"
                            + "trip-1,24:10:00,24:11:00,stop-1,1,0,0,1\n"
                            + "trip-1,24:20:00,24:21:00,stop-2,2,0,0,1\n"
                            + "trip-2,24:10:00,24:11:00,stop-1,1,0,0,1\n");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(feed))) {
            files.forEach((name, content) -> add(zip, name, content));
        }
        return feed;
    }

    private void add(ZipOutputStream zip, String name, String content) {
        try {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
