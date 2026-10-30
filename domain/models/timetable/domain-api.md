# Timetable API implementation and frontend handoff

## Implemented endpoints

The service-owned contract is [`docs/apis/timetable-api-v1.yaml`](../../../docs/apis/timetable-api-v1.yaml).

| Request | Purpose | Status |
| --- | --- | --- |
| `GET /api/v1/timetable/manifest` | Active version, date window, two user-facing offerings, and source revisions | Implemented |
| `GET /api/v1/timetable/packages/{travelDate}?offering=SYDNEY_TRAINS` | One normalized Sydney-date package; `offering` may be omitted for `ALL` | Implemented |
| `GET /api/v1/timetable/realtime` | Independently-versioned alerts, trip updates, and vehicle positions | Implemented |
| `GET /api/v1/timetable/fares/opal` | Normalized Opal fare rules | Contract/design only; not ready for app integration |

The app must call the backend; it must not call TfNSW directly or receive
provider credentials. Local service base URL is `http://localhost:8080`.
Packages use HTTP compression and ETags. Follow the response's
`Content-Encoding`; use `If-None-Match` when revalidating cached content. The
backend's current date window is yesterday through today+6, inclusive.

## Package shape and route display

The package contains `offerings`, `offeringStatuses`, `sources`, `stops`,
`routes`, `trips`, `calls`, and `interchanges`. IDs retain feed provenance.
The manifest exposes only `SYDNEY_TRAINS` and `SYDNEY_METRO`.

Trips reference a contiguous range in `calls` using `stopCallStart` and
`stopCallCount`. These are array indexes, not IDs. Each call has distinct
`arrival` and `departure` RFC 3339 timestamps with a Sydney offset, plus its
feed-scoped `stopId`. Resolve the stop label from `stops`. A call may have equal
arrival and departure values, as in the captured bus schedule.

The 1 Nov 2026 Sydney Trains package was checked on the running service after
the supporting-feed update. It contained 2,534 trips, 106 supporting trips,
and these routes:

- ReplacementBus `9T2`, route ID `replacementbus:7063_9T2`, purpose
  `REPLACEMENT`.
- PCBC `11T2`, route ID `pcbc:700015_11T2`, purpose `PLANNED_CONTINGENCY`.

For example, 9T2 trip `replacementbus:2757467:2026-11-01` has the full loop:
Glenfield Railway Pde 09:48 -> Edmondson Park Stand B 10:00 -> Leppington Stand A
10:10 -> Edmondson Park Stand A 10:24 -> Glenfield Railway Pde 10:42. It carries
`serviceContext` with the affected rail segment, station endpoint IDs, source
reference, and reviewed service window. Do not infer that a disruption is
currently active from this static context; use the realtime alert overlay for
live status.

`interchanges` includes reviewed links from supporting bus stops to train
station IDs and carries provenance. Preserve these as explicit transfer
relationships. The UI can render supporting legs differently using
`servicePurpose`, while keeping the route's provider name and colour where
available.

## Frontend integration sequence

1. Fetch and cache the manifest; offer the dates and the two available modes.
2. Fetch the selected date package, passing one `offering` value. Search the
   package's `stops` locally; do not add a per-keystroke server endpoint.
3. Display trips by resolving their call slices and stop IDs. Keep service
   arrival and departure separate, and display all intermediate calls.
4. Fetch realtime separately and overlay it only when entity status and
   feed-scoped identity permit a join. Keep scheduled times visible when live
   data is absent or stale.
5. Calculate route alternatives in the app. The backend publishes timetable
   data; it does not implement a journey solver.

Before release, check the actual CORS origin and authentication behavior for
the app's local and deployed URLs. Verify package caching and gzip behavior
through the app's HTTP client. Fare UI should wait until the Opal endpoint and
estimate contract are implemented.

## Backend operations and deferred work

Schedule refresh checks all four provider feeds (Sydney Trains, Metro,
ReplacementBus, PCBC) and publishes only a complete validated snapshot. A
failed refresh keeps the last known good snapshot. Realtime has its own
polling/version lifecycle. The production realtime poll is one minute by
default; quota review and any cadence change are deferred until request counts
are measured.

Bounded retention for older timetable package versions is not implemented yet.
Complete these backend follow-ups after initial app integration:

- Test the app against the running API and compare its requests/responses to
  the OpenAPI contract.
- Add bounded package-version retention and confirm it preserves active and
  last-known-good snapshots.
- Measure provider request totals and package sizes after supporting feeds;
  retain the one-minute realtime cadence until those measurements are known.

See [`provider-apis.md`](provider-apis.md) for feed provenance and
[`model-spec.md`](model-spec.md) for the broader domain decisions and provider
evidence.
