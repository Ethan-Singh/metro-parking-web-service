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
- The upstream `MessageDate` has no offset. Interpret it in
  `Australia/Sydney` in a private provider mapper, then convert to `Instant`.
  Resolve the rare DST overlap using an upstream unambiguous field if available;
  otherwise document and apply one explicit overlap policy.
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

The saved `docs/apis/v1-carpark` examples are compatible with this mapping:
`/carpark` returns a facility-name lookup; `/carpark?facility=...` and
`/carpark/history?...` return records containing facility ID/name, spots,
occupancy, and `MessageDate`. The history response is an array of those
records. Map the ID and date from the provider record/request, retain only the
latest occupancy/capacity per ten-minute Sydney bucket, and discard nested
provider-only fields such as zones and location. Confirm the ambiguous provider
`time` field's units/meaning before using it to resolve DST; it is not currently
assumed to be epoch time.

Provider cadence varies by facility (counter-change/10-minute, 15-second, or
60-second updates). That is upstream behavior, not a domain enum or a local
schedule. The service's persisted history floor is ten minutes. Do not retain
or advertise `FIVE_MINUTE` resolution.

Start with an empty database using the new `parkingDay` collection and unique
index. No migration or legacy collection support is required.

## 4. Backfill state and flow

- Keep one scheduled history-sync loop and make exactly one `/history` call per
  work item. It uses the same endpoint for today's refresh and older-date
  backfill; today's response is the live chart tail as well as that date's
  stored history. Do not call `/full-list` for live occupancy.
- Load the facility-name lookup from `/carpark`, cache it in memory, apply the
  configured excluded facility IDs, then rotate through the remaining IDs.
  Refresh this small lookup infrequently; each refresh consumes a loop slot.
- Use one scheduler loop at a 2.2-second fixed delay and make at most one
  provider request in each slot. Four of every five history requests poll a
  rotating facility for today's Sydney date; the fifth selects the newest
  eligible older facility/date. Both use `/history`; no separate live-feed or
  backfill request path exists.
- This caps provider traffic, including a cold start, at about 39,273 requests
  per day and below one request every two seconds, with catalog refreshes
  replacing history slots. During the initial backlog, about 7,850 slots/day
  go to older dates, enough to fetch 1,500 facility/date pairs in roughly five
  hours. Do not retry history requests immediately; persist retry state and
  use a later scheduled slot. This remains below the stated 60,000/day quota
  and 5/second rate limit.
- `windowDays` is a positive count of inclusive Sydney calendar days. The
  older-date scan spans `today - 1` through `today - (windowDays - 1)` because
  today's date is refreshed continuously by the same history loop.
- Store status fields directly on the matching `ParkingDay` document. Missing
  state means not requested; `PROCESSING` has a short `leaseUntil`,
  `RETRY_WAIT` has `retryAfter`, and `COMPLETE` has `completedAt`.
- A successful empty response still creates/completes the day document with an
  empty sample array. Failures remain retryable; never mark them complete.
- `COMPLETE` prevents redundant older-date backfill, but today's polling
  continues after completion so the current chart remains fresh. Claim older
  dates atomically with a lease; an expired lease can be reclaimed.
- No separate status collection, global cursor, per-facility cursor, phase, or
  run document. If the process fails after sample ingestion but before marking
  complete, retry is safe because daily bucket upserts are idempotent.

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

The live API returns the newest point already stored from today's history
response, so it does not require a second upstream feed. Use
`LiveCarParkResponse` for the current snapshot DTO and `/live` for its route.
The all-facilities endpoint returns the newest stored sample for each allowed
facility; history uses the compact range response described above.

## 6. Implementation sequence

1. Add explicit Sydney date/bucket and timestamp conversion helpers.
2. Add `ParkingDay` daily document, bounded embedded samples, unique index, and
   empty-database startup.
3. Align provider, ingestion, analytics, and Mongo code with the package
   boundaries above.
4. Replace cursor backfill and `/full-list` live ingestion with the unified,
   quota-aware history sync loop and leased state on daily documents.
5. Replace analytics granularity branching with the resolution policy and
   Sydney-aligned aggregation.
6. Replace history/live timestamp serialization with epoch milliseconds
   and introduce the compact tuple response.
7. Update configuration and operational docs. Frontend changes follow in a
   separate pass: tuple types, daily metric contract types, explicit Sydney
   formatting, actual resolution labels, and mobile chart behavior.

## Acceptance criteria

- No event moment is stored or returned as a local-time string; API timestamps
  are epoch milliseconds.
- Mongo has one document per facility/Sydney date and at most 150 ten-minute
  samples per daily document; encoded document size is at most
  1 MiB, below MongoDB's 16 MiB limit.
- Upserts retain the newest source sample per Sydney bucket.
- Backfill retries failures, records successful empty dates, survives restarts,
  and needs no separate status collection.
- A seven-day AUTO query returns hourly tuples; a 31-day query returns
  24-hour tuples; responses stay within 200 points.
- Provider schemas and provider cadence details do not leak into the API/domain
  contract.
