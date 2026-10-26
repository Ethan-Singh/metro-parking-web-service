# Backend refactor implementation spec

## Goal

Reorganise the car park backend around provider, domain, application, persistence,
and API boundaries. Reduce MongoDB writes and history response size, simplify
backfill state, and make Sydney calendar dates distinct from UTC instants.
Trip Planner and Toll Calculator remain future capabilities.

## 1. Domain and package boundaries

Use car park as the first vertical slice:

- **Domain:** facility identity, `ParkingDay`, embedded sample values, history
  resolution policy, and Sydney date/time policy. Keep Spring and Mongo types
  out of domain behavior where practical.
- **Application:** live refresh, ingestion, history query, and backfill worker
  use cases. Depend on ports rather than concrete HTTP/database clients.
- **Provider adapter:** NSW request/response DTOs, HTTP client, and private
  `MessageDate` conversion.
- **Mongo adapter:** daily document repository, unique/index definitions,
  aggregation, and indexes.
- **API adapter:** controllers and response mapping. A shared
  `ApiTimestampMapper.toEpochMillis(Instant)` serves live and history.

Keep Trip Planner and Toll Calculator as separate future integrations; do not
create generic provider abstractions before a second integration needs them.

### Naming

Use `Parking` for this service's domain and `CarPark` for NSW provider types.
The current source names follow this split: `CarParkApiClient`,
`CarParkApiRecord`, `CarParkApiProperties`, `ParkingDay`,
`ParkingDayIngestService`, `ParkingFacilityCatalog`, `ParkingSyncScheduler`,
`ParkingController`, `ParkingQueryService`, and `LiveCarParkResponse`.
Keep one concise type per real boundary; do not add wrapper types that only
rename an unchanged value.

## 2. Time and calendar rules

- Persist event moments as `Instant` / BSON UTC dates. Never persist
  `LocalDateTime` as if it were an instant.
- The upstream `MessageDate` has no offset and represents Sydney local time.
  Interpret it in `Australia/Sydney` in a private provider mapper, then convert
  to `Instant` for UTC storage. Resolve the rare DST overlap using an upstream
  unambiguous field if available; otherwise document and apply one explicit
  overlap policy.
- Ignore the legacy upstream `time` field for date and time handling. The
  provider documentation says it is seconds since year 2000 and directs
  consumers to use `MessageDate` instead.
- Backfill and collection partition dates are Sydney `LocalDate` values. Store
  the Mongo key as `sydneyDate: "YYYY-MM-DD"` (or an equivalent date-only
  representation that is never interpreted as UTC midnight).
- Query documents by `(facilityId, sydneyDate)`. Do not group or select a
  Sydney day by truncating UTC timestamps. A Sydney day can span 23, 24, or 25
  hours.
- A sample's Sydney bucket instant is the instant for Sydney start-of-day plus
  `bucketIndex * 10 minutes`. Store source event time separately as UTC `Instant`.
- Serialize API moments as numeric epoch milliseconds. The frontend must format
  the instant in `Australia/Sydney` when that display is required.

## 3. Daily Mongo storage

Use one `ParkingDay` document per unique `(facilityId, sydneyDate)`, with a
bounded embedded sample array and backfill state on that same document. Add a
unique compound index on `{ facilityId: 1, sydneyDate: 1 }`.

Each sample represents one ten-minute Sydney bucket and contains a
`bucketIndex`, `sourceTime`, `occupancy`, and `capacity`. Upsert into the daily
document and retain the newest `sourceTime` for each bucket. `bucketIndex` is
relative to Sydney start of day. The maximum array length is 150 on a 25-hour
day (138, 144, or 150 buckets depending on daylight-saving transition).

This gives at most 31 daily documents per facility for the configured history
window, rather than one document per upstream event. Keep arrays bounded; a
single facility document with an array that grows across all dates is not the
partition. A daily document stays small and its date key matches the product's
Sydney calendar queries. Enforce a 1 MiB application-level maximum encoded BSON
size per `ParkingDay` document, leaving substantial headroom below MongoDB's
16 MiB BSON document limit. Reject or compact a write that would exceed this
service limit; never store the provider's full nested payload in a day record.
MongoDB advises against unbounded arrays and limits a BSON document to 16 MiB.

The saved `docs/apis/v1-carpark` examples show three provider uses:
`/carpark` without a facility returns the facility-name lookup;
`/carpark?facility=...` returns the latest occupancy record for one facility;
`/carpark/history?...` returns an array of historical occupancy records. Both
record endpoints include facility ID/name, capacity, occupancy, and
`MessageDate`. Map each record's Sydney-local `MessageDate` to its actual
`sydneyDate` and UTC `sourceTime`; do not infer the stored day from the history
request's `eventdate`. Discard provider-only fields such as zones and location.

Provider cadence varies by facility (counter-change/10-minute, 15-second, or
60-second updates). That is upstream behavior, not a domain enum or a local
schedule. The service's persisted history floor is ten minutes. Do not retain
or advertise `FIVE_MINUTE` resolution.

Start with an empty database using the new `parkingDay` collection and unique
index. No migration or legacy collection support is required.

## 4. Current occupancy and historical backfill

- Separate live refresh from historical backfill. For a live refresh, call
  `/carpark?facility={facilityId}` and ingest its single latest record into the
  `ParkingDay` selected by that record's Sydney-local `MessageDate`. Do not
  call the provider for each frontend request; serve the latest stored sample
  through the existing `/live` API. The facility-name lookup remains
  `/carpark` without a facility parameter.
- Use `/carpark/history?facility={facilityId}&eventdate={date}` only for
  historical retrieval. Treat `eventdate` as the provider's request key, not
  automatically as the Sydney date of the returned records. Always group
  returned records by the Sydney date derived from `MessageDate`.
- The provider documentation describes `eventdate` as a date but does not
  specify its timezone or how results behave around Sydney midnight. A saved
  response for `eventdate=2026-10-26` contains `MessageDate` values on
  `2026-10-27` Sydney time. Before finalizing backfill boundaries, probe
  adjacent `eventdate` values and compare all returned `MessageDate` values.
  If an upstream date partition spans Sydney dates, fetch every required
  provider date for a Sydney day and merge only records whose `MessageDate`
  belongs to that day. Do not mark a Sydney day complete after fetching only
  one partial provider date.
- Keep current occupancy and historical coverage as separate concerns even
  though their samples share the same daily Mongo collection. A successful
  live response updates the current sample; it does not mark historical
  coverage `COMPLETE`. Backfill completion means all provider date segments
  required for that Sydney day have been fetched successfully.
- Run all upstream calls through one centrally paced scheduler so catalog,
  live, and history calls share the same quota budget. Make at most one
  provider request per scheduler slot. Select the live polling interval and
  history-to-live slot allocation from the enabled facility count and the
  provider's published limits; keep those values configurable and document
  the resulting worst-case daily call count. Do not launch parallel requests
  that bypass this budget. A history day requiring adjacent provider dates
  consumes one slot per provider request and may take longer to backfill.
- Load the facility-name lookup from `/carpark`, cache it in memory, apply the
  configured excluded facility IDs, then rotate through the remaining IDs.
  Catalog refreshes consume a scheduler slot.
- `windowDays` is a positive count of inclusive Sydney calendar days. Backfill
  older Sydney dates from newest to oldest, while keeping provider `eventdate`
  selection separate from the Mongo `sydneyDate` key.
- Store historical coverage state on the matching `ParkingDay` document.
  Missing state means not requested; `PROCESSING` has a short `leaseUntil`,
  `RETRY_WAIT` has `retryAfter`, and `COMPLETE` has `completedAt`. Empty live
  responses must not mark a day complete. An empty historical response only
  counts as complete after the provider-date coverage for that Sydney day is
  known to be complete.
- Claim older dates atomically with a lease; an expired lease can be reclaimed.
  If ingestion succeeds but status completion fails, retry safely because
  daily bucket upserts are idempotent.
- Avoid a separate status collection, run document, or per-event record. If
  verifying provider-date coverage requires additional progress, keep the
  smallest necessary checkpoint on the existing daily document or cursor
  rather than adding one document per observation.

## 5. History API contract

No backward compatibility is required. Replace the current history response
with:

```json
{
  "from": "2026-10-01",
  "to": "2026-10-07",
  "resolution": "ONE_HOUR",
  "points": [[1790802000000, 0.42, 58]]
}
```

Each point tuple is `[timeMs, occupancyRate, available]`. The chart needs time
and occupancy rate; the history summary also needs available spaces. Omit
occupancy, facility slug (already in the path), and duplicate timestamp text.

Supported names: `TEN_MINUTE`, `ONE_HOUR`, `SIX_HOUR`, `TWELVE_HOUR`, and
`TWENTY_FOUR_HOUR`. `AUTO` is the default selection policy:

| Requested date span | Default resolution | Approximate point count |
| --- | --- | ---: |
| Up to 1 day | `TEN_MINUTE` | 150 max |
| 2–7 days | `ONE_HOUR` | 168 max |
| 8–14 days | `SIX_HOUR` | 56 max |
| 15–31 days | `TWENTY_FOUR_HOUR` | 31 max |

Reject invalid ranges and cap the query span at 31 inclusive Sydney dates. If
an explicit resolution exceeds the 200-point ceiling, coarsen it and return the
actual resolution. Aggregate using Sydney-local boundaries, and derive
occupancy rate and available from bucket occupancy and capacity. Cache keys
include facility, clamped dates, and actual resolution.

Daily commuter summaries are served separately from chart history so they do
not depend on the selected chart resolution. The contract is defined in
[`domain/contracts/daily-parking-metrics-v1.md`](../contracts/daily-parking-metrics-v1.md).
It returns expected-free estimates near 7am/8am and per-day occupancy milestones
for up to three inclusive Sydney dates. The query uses the existing
`(facilityId, sydneyDate)` index and reads at most three daily documents.

The live API returns the newest stored sample, with current-day samples
refreshed from `/carpark?facility=...` and historical samples populated by the
backfill path. Use `LiveCarParkResponse` for the snapshot DTO and `/live` for
its route. The all-facilities endpoint returns each allowed facility's newest
stored sample; history uses the compact range response described above. When
no historical observations exist, return an empty `points` array and `null`
daily metrics rather than inventing values. Define an explicit no-data
response for live facilities that have not received a sample yet.

## 6. Implementation sequence

1. Add explicit Sydney date/bucket and timestamp conversion helpers.
2. Add `ParkingDay` daily document, bounded embedded samples, unique index, and
   empty-database startup.
3. Align provider, ingestion, analytics, and Mongo code with the package
   boundaries above.
4. Add a scheduled per-facility live adapter using `/carpark?facility=...` and
   persist each observation in its Sydney-day bucket without changing
   historical coverage state.
5. Verify `/history` `eventdate` behavior across Sydney midnight using
   adjacent request dates and `MessageDate` values; encode the confirmed
   provider-date-to-Sydney-day coverage rule in the backfill worker.
6. Separate leased history backfill from live refresh, keep all provider calls
   under one quota-aware scheduler, and complete a Sydney date only after its
   required provider-date segments succeed.
7. Replace analytics granularity branching with the resolution policy and
   Sydney-aligned aggregation.
8. Replace history/live timestamp serialization with epoch milliseconds
   and introduce the compact tuple response.
9. Update configuration and operational docs. Frontend changes follow in a
   separate pass: tuple types, daily metric contract types, explicit Sydney
   formatting, actual resolution labels, and mobile chart behavior.

## Acceptance criteria

- No event moment is stored or returned as a local-time string; API timestamps
  are epoch milliseconds.
- Mongo has one document per facility/Sydney date and at most 150 ten-minute
  samples per daily document; encoded document size is at most
  1 MiB, below MongoDB's 16 MiB limit.
- Upserts retain the newest source sample per Sydney bucket.
- Live refresh uses the provider's single-facility current endpoint; frontend
  requests never call the upstream API directly.
- Backfill retries failures, stores coverage separately from live refresh,
  survives restarts, and only records an empty completed Sydney day after all
  required provider-date segments are confirmed empty.
- Provider history request dates are not assumed to equal Sydney storage dates;
  returned rows are grouped by Sydney `MessageDate` and boundary behavior is
  covered by tests.
- A seven-day AUTO query returns hourly tuples; a 31-day query returns
  24-hour tuples; responses stay within 200 points.
- Provider schemas and provider cadence details do not leak into the API/domain
  contract.
