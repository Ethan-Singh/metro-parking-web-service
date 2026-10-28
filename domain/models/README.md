# Domain models

These models and specs describe the current parking backend and draft timetable
routing domain. The timetable draft is based on the captured Sydney Trains,
Metro, Complete GTFS, and GTFS-Realtime provider data in `docs/apis/`.

- [System context](system-context.puml)
- [Car park domain and daily storage](parking-domain.puml)
- [Backfill flow](backfill-flow.puml)
- [Car park API contract](carpark-api.puml)
- [Timetable routing domain (draft)](timetable-domain.puml)
- [Timetable model notes (draft)](timetable-model-spec.md)
- Opal fare calculation domain: `src/main/java/com/example/metro_parking_web_service/timetable/opal/domain/`
- [Toll calculation domain](toll-domain.puml)

## Decisions

- Store one `ParkingDay` per `(facilityId, sydneyDate)` with a bounded array of
  ten-minute samples and backfill state on the same document. Do not keep one
  ever-growing array per facility.
- Keep the Sydney date key date-only (`YYYY-MM-DD`); store sample event moments
  as UTC `Instant`. Compute local bucket instants from Sydney start-of-day.
- A Sydney day has 138, 144, or 150 ten-minute buckets depending on daylight
  saving, so the embedded list is bounded at 150.
- Provider update cadence varies by facility. It is an upstream characteristic,
  not a domain enum or our storage schedule. Persist no finer than ten minutes;
  exclude `FIVE_MINUTE`.
- Use `TEN_MINUTE`, `ONE_HOUR`, `SIX_HOUR`, `TWELVE_HOUR`, and
  `TWENTY_FOUR_HOUR` for history resolutions.
- Convert the provider's offset-free `MessageDate` from Sydney local time into
  `Instant`; emit numeric epoch milliseconds from the API.
- Return compact history tuples with timestamp, occupancy rate, and available
  spaces. Backfill scans today's Sydney date backwards over the configured
  window and keeps state on the day document.

The provider's varying update frequencies are described in
[Car Park API v2.3](../../docs/provider-docs/car-park-api/car-park-api-documentation-v2.3.pdf).
