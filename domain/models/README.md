# Domain models

These models and the [implementation spec](implementation-spec.md) describe
the proposed backend refactor, based on the current car park provider contract
and the data the existing frontend consumes.

- [System context](system-context.puml)
- [Car park domain and daily storage](parking-domain.puml)
- [Backfill flow](backfill-flow.puml)
- [Car park API contract](carpark-api.puml)
- [Future journey domain](journey-domain.puml)

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
[Car Park API v2.3](../../docs/parking-docs/Car%20park%20API/car-park-api-documentation-v2.3.pdf).
