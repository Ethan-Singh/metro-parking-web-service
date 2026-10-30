/* (MISTLETOE MACHINATIONS)2026 */
package com.example.metro_parking_web_service.timetable.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds one compact public package without retaining the full stop_times table in memory. */
@Component
@RequiredArgsConstructor
public class GtfsDatePackageBuilder {

    private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ObjectNode build(Path zipPath, String feedKey, String offering, LocalDate travelDate)
            throws IOException {
        return build(zipPath, feedKey, offering, travelDate, zipPath.getFileName().toString());
    }

    public ObjectNode build(
            Path zipPath,
            String feedKey,
            String offering,
            LocalDate travelDate,
            String providerRevision)
            throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            Map<String, Map<String, String>> stops = index(readTable(zip, "stops.txt"), "stop_id");
            Map<String, Map<String, String>> routes =
                    index(readTable(zip, "routes.txt"), "route_id");
            Map<String, Map<String, String>> trips = index(readTable(zip, "trips.txt"), "trip_id");
            Map<String, Map<String, String>> calendars =
                    indexOptional(zip, "calendar.txt", "service_id");
            Map<String, Map<LocalDate, String>> exceptions = readExceptions(zip);

            Set<String> allowedRoutes = new HashSet<>();
            routes.forEach(
                    (id, route) -> {
                        String agency = route.getOrDefault("agency_id", "");
                        if (offering.equals("SYDNEY_METRO") || agency.equals("SydneyTrains")) {
                            allowedRoutes.add(id);
                        }
                    });
            trips.entrySet()
                    .removeIf(entry -> !allowedRoutes.contains(entry.getValue().get("route_id")));

            Map<String, List<LocalDate>> activeDaysByService =
                    activeDays(calendars, exceptions, travelDate);
            Map<String, List<LocalDate>> tripDays = new HashMap<>();
            trips.forEach(
                    (tripId, trip) -> {
                        List<LocalDate> active = activeDaysByService.get(trip.get("service_id"));
                        if (active != null && !active.isEmpty()) tripDays.put(tripId, active);
                    });

            Set<String> selectedTripInstances = findTripsCallingOnDate(zip, tripDays, travelDate);
            Map<String, List<CallRow>> callsByInstance =
                    collectTripCalls(zip, selectedTripInstances, tripDays);
            return serializePackage(
                    feedKey,
                    offering,
                    travelDate,
                    stops,
                    routes,
                    trips,
                    callsByInstance,
                    providerRevision);
        }
    }

    private Set<String> findTripsCallingOnDate(
            ZipFile zip, Map<String, List<LocalDate>> tripDays, LocalDate travelDate)
            throws IOException {
        Set<String> selected = new HashSet<>();
        forEachRow(
                zip,
                "stop_times.txt",
                row -> {
                    List<LocalDate> serviceDates = tripDays.get(row.get("trip_id"));
                    if (serviceDates == null) return;
                    for (LocalDate serviceDate : serviceDates) {
                        if (eventFallsOn(row.get("arrival_time"), serviceDate, travelDate)
                                || eventFallsOn(
                                        row.get("departure_time"), serviceDate, travelDate)) {
                            selected.add(instanceKey(row.get("trip_id"), serviceDate));
                        }
                    }
                });
        return selected;
    }

    private Map<String, List<CallRow>> collectTripCalls(
            ZipFile zip, Set<String> selected, Map<String, List<LocalDate>> tripDays)
            throws IOException {
        Map<String, List<CallRow>> result = new TreeMap<>();
        forEachRow(
                zip,
                "stop_times.txt",
                row -> {
                    List<LocalDate> serviceDates = tripDays.get(row.get("trip_id"));
                    if (serviceDates == null) return;
                    for (LocalDate serviceDate : serviceDates) {
                        String key = instanceKey(row.get("trip_id"), serviceDate);
                        if (selected.contains(key)) {
                            result.computeIfAbsent(key, ignored -> new ArrayList<>())
                                    .add(new CallRow(row, serviceDate));
                        }
                    }
                });
        result.values()
                .forEach(
                        calls ->
                                calls.sort(
                                        Comparator.comparingInt(
                                                call -> integer(call.row().get("stop_sequence")))));
        return result;
    }

    private ObjectNode serializePackage(
            String feedKey,
            String offering,
            LocalDate travelDate,
            Map<String, Map<String, String>> stops,
            Map<String, Map<String, String>> routes,
            Map<String, Map<String, String>> trips,
            Map<String, List<CallRow>> callsByInstance,
            String providerRevision) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("schemaVersion", "1");
        body.put("travelDate", travelDate.toString());
        body.put("timezone", SYDNEY.getId());
        body.put("availability", "AVAILABLE");
        body.putArray("offerings").add(offering);
        ArrayNode statuses = body.putArray("offeringStatuses");
        ObjectNode status = statuses.addObject();
        status.put("key", offering);
        status.put("state", "AVAILABLE");
        status.putArray("coveredDates").add(travelDate.toString());
        status.putArray("minimumFacets").add("STATION");
        status.putArray("supportedFacets").add("STATION");
        status.putObject("packageVersions");
        status.putArray("sources");

        ArrayNode sources = body.putArray("sources");
        ObjectNode source = sources.addObject();
        source.put("feedKey", feedKey);
        source.put("role", "PRIMARY");
        source.put("associatedOffering", offering);
        source.put("status", "CURRENT");
        source.put("providerRevision", providerRevision);

        Set<String> usedStops = new HashSet<>();
        Set<String> usedRoutes = new HashSet<>();
        ArrayNode tripsOut = body.putArray("trips");
        ArrayNode callsOut = body.putArray("calls");
        for (Map.Entry<String, List<CallRow>> entry : callsByInstance.entrySet()) {
            String[] keyParts = entry.getKey().split("\\|", 2);
            String providerTripId = keyParts[0];
            Map<String, String> trip = trips.get(providerTripId);
            if (trip == null) continue;
            String publicTripId = feedKey + ":" + providerTripId + ":" + keyParts[1];
            String providerRouteId = trip.get("route_id");
            String publicRouteId = feedKey + ":" + providerRouteId;
            usedRoutes.add(providerRouteId);
            ObjectNode tripOut = tripsOut.addObject();
            tripOut.put("id", publicTripId);
            tripOut.put("providerId", providerTripId);
            tripOut.put("routeId", publicRouteId);
            tripOut.put("serviceDate", keyParts[1]);
            tripOut.put("headsign", trip.getOrDefault("trip_headsign", ""));
            tripOut.put("directionId", trip.getOrDefault("direction_id", ""));

            List<CallRow> calls = entry.getValue();
            tripOut.put("stopCallStart", callsOut.size());
            tripOut.put("stopCallCount", calls.size());
            for (CallRow call : calls) {
                Map<String, String> row = call.row();
                String providerStopId = row.get("stop_id");
                usedStops.add(providerStopId);
                ObjectNode callOut = callsOut.addObject();
                callOut.put("tripId", publicTripId);
                callOut.put("sequence", integer(row.get("stop_sequence")));
                callOut.put("stopId", feedKey + ":" + providerStopId);
                String arrival = row.get("arrival_time");
                String departure = row.get("departure_time");
                callOut.put(
                        "arrival",
                        eventInstant(arrival.isBlank() ? departure : arrival, call.serviceDate()));
                callOut.put(
                        "departure",
                        eventInstant(
                                departure.isBlank() ? arrival : departure, call.serviceDate()));
                callOut.put("timezone", SYDNEY.getId());
                if (row.containsKey("pickup_type"))
                    callOut.put("pickupType", integer(row.get("pickup_type")));
                if (row.containsKey("drop_off_type"))
                    callOut.put("dropOffType", integer(row.get("drop_off_type")));
                if (row.containsKey("timepoint"))
                    callOut.put("timepoint", row.get("timepoint").equals("1"));
            }
        }

        ArrayNode routesOut = body.putArray("routes");
        for (String providerId : usedRoutes.stream().sorted().toList()) {
            Map<String, String> route = routes.get(providerId);
            if (route == null) continue;
            ObjectNode output = routesOut.addObject();
            output.put("id", feedKey + ":" + providerId);
            output.put("offering", offering);
            output.put("feedKey", feedKey);
            output.put("providerId", providerId);
            output.put("shortName", route.getOrDefault("route_short_name", ""));
            output.put("name", route.getOrDefault("route_long_name", ""));
            output.put("description", route.getOrDefault("route_desc", ""));
            output.put("mode", offering.equals("SYDNEY_METRO") ? "METRO" : "RAIL");
            output.put("servicePurpose", "REGULAR");
            putIfNotBlank(output, "color", route.get("route_color"));
            putIfNotBlank(output, "textColor", route.get("route_text_color"));
        }

        Set<String> allStops = new HashSet<>(usedStops);
        for (String providerId : usedStops) {
            Map<String, String> stop = stops.get(providerId);
            if (stop != null && !stop.getOrDefault("parent_station", "").isBlank()) {
                allStops.add(stop.get("parent_station"));
            }
        }
        ArrayNode stopsOut = body.putArray("stops");
        for (String providerId : allStops.stream().sorted().toList()) {
            Map<String, String> stop = stops.get(providerId);
            if (stop == null) continue;
            String locationType = stop.getOrDefault("location_type", "0");
            ObjectNode output = stopsOut.addObject();
            output.put("id", feedKey + ":" + providerId);
            output.put("name", stop.getOrDefault("stop_name", ""));
            output.put("kind", "1".equals(locationType) ? "STATION" : "PLATFORM");
            output.put("feedKey", feedKey);
            output.put("providerId", providerId);
            putIfNotBlank(
                    output,
                    "parentStopId",
                    stop.get("parent_station") == null
                            ? null
                            : feedKey + ":" + stop.get("parent_station"));
            putIfNotBlank(output, "platformCode", stop.get("platform_code"));
            if (!stop.getOrDefault("stop_lat", "").isBlank()) {
                output.put(
                        "coordinates",
                        objectMapper
                                .createObjectNode()
                                .put("latitude", decimal(stop.get("stop_lat")))
                                .put("longitude", decimal(stop.get("stop_lon"))));
            }
        }
        body.putArray("interchanges");
        body.put("generatedAt", java.time.Instant.now().toString());
        body.put("packageVersion", "candidate");
        return body;
    }

    private Map<String, List<LocalDate>> activeDays(
            Map<String, Map<String, String>> calendars,
            Map<String, Map<LocalDate, String>> exceptions,
            LocalDate travelDate) {
        Map<String, List<LocalDate>> active = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : calendars.entrySet()) {
            for (int offset = 0; offset <= 2; offset++) {
                LocalDate serviceDate = travelDate.minusDays(offset);
                Map<String, String> calendar = entry.getValue();
                String weekday = serviceDate.getDayOfWeek().name().toLowerCase();
                boolean runs = "1".equals(calendar.get(weekday));
                String start = calendar.get("start_date");
                String end = calendar.get("end_date");
                runs &=
                        start != null
                                && end != null
                                && !serviceDate.isBefore(parseDate(start))
                                && !serviceDate.isAfter(parseDate(end));
                String exception =
                        exceptions.getOrDefault(entry.getKey(), Map.of()).get(serviceDate);
                if ("1".equals(exception)) runs = true;
                if ("2".equals(exception)) runs = false;
                if (runs)
                    active.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                            .add(serviceDate);
            }
        }
        exceptions.forEach(
                (serviceId, dates) -> {
                    LocalDate serviceDate = travelDate;
                    for (int offset = 0;
                            offset <= 2;
                            offset++, serviceDate = travelDate.minusDays(offset)) {
                        if ("1".equals(dates.get(serviceDate))) {
                            List<LocalDate> serviceDates =
                                    active.computeIfAbsent(serviceId, ignored -> new ArrayList<>());
                            if (!serviceDates.contains(serviceDate)) serviceDates.add(serviceDate);
                        }
                        if ("2".equals(dates.get(serviceDate))) {
                            List<LocalDate> serviceDates = active.get(serviceId);
                            if (serviceDates != null) serviceDates.remove(serviceDate);
                        }
                    }
                });
        return active;
    }

    private Map<String, Map<LocalDate, String>> readExceptions(ZipFile zip) throws IOException {
        Map<String, Map<LocalDate, String>> result = new HashMap<>();
        ZipEntry entry = zip.getEntry("calendar_dates.txt");
        if (entry == null) return result;
        forEachRow(
                zip,
                "calendar_dates.txt",
                row ->
                        result.computeIfAbsent(row.get("service_id"), ignored -> new HashMap<>())
                                .put(parseDate(row.get("date")), row.get("exception_type")));
        return result;
    }

    private Map<String, Map<String, String>> indexOptional(ZipFile zip, String file, String key)
            throws IOException {
        return zip.getEntry(file) == null ? Map.of() : index(readTable(zip, file), key);
    }

    private List<Map<String, String>> readTable(ZipFile zip, String file) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        forEachRow(zip, file, rows::add);
        return rows;
    }

    private void forEachRow(ZipFile zip, String file, RowConsumer consumer) throws IOException {
        ZipEntry entry = zip.getEntry(file);
        if (entry == null) throw new IOException("GTFS feed is missing required file " + file);
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;
            List<String> headers = parseCsv(headerLine);
            if (!headers.isEmpty()) headers.set(0, headers.getFirst().replace("\uFEFF", ""));
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> values = parseCsv(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < values.size() ? values.get(i) : "");
                }
                consumer.accept(row);
            }
        }
    }

    private Map<String, Map<String, String>> index(List<Map<String, String>> rows, String key) {
        Map<String, Map<String, String>> result = new HashMap<>();
        rows.forEach(row -> result.put(row.get(key), row));
        return result;
    }

    private List<String> parseCsv(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(ch);
            }
        }
        values.add(value.toString());
        return values;
    }

    private boolean eventFallsOn(String value, LocalDate serviceDate, LocalDate travelDate) {
        return value != null
                && !value.isBlank()
                && serviceDate
                        .atStartOfDay()
                        .plusSeconds(seconds(value))
                        .toLocalDate()
                        .equals(travelDate);
    }

    private String eventInstant(String value, LocalDate serviceDate) {
        return LocalDateTime.of(serviceDate, java.time.LocalTime.MIDNIGHT)
                .plusSeconds(seconds(value))
                .atZone(SYDNEY)
                .toOffsetDateTime()
                .toString();
    }

    private long seconds(String value) {
        if (value == null || value.isBlank()) return 0;
        String[] parts = value.split(":");
        return Long.parseLong(parts[0]) * 3600
                + Long.parseLong(parts[1]) * 60
                + Long.parseLong(parts[2]);
    }

    private int integer(String value) {
        return value == null || value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private double decimal(String value) {
        return Double.parseDouble(value);
    }

    private void putIfNotBlank(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) node.put(field, value);
    }

    private LocalDate parseDate(String value) {
        if (value.contains("-")) return LocalDate.parse(value);
        return LocalDate.of(
                Integer.parseInt(value.substring(0, 4)),
                Integer.parseInt(value.substring(4, 6)),
                Integer.parseInt(value.substring(6, 8)));
    }

    private String instanceKey(String tripId, LocalDate serviceDate) {
        return tripId + "|" + serviceDate;
    }

    private record CallRow(Map<String, String> row, LocalDate serviceDate) {}

    @FunctionalInterface
    private interface RowConsumer {
        void accept(Map<String, String> row) throws IOException;
    }
}
