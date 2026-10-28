# Metro Parking Web Service planned work

This document records the planned timetable-route feature and other deferred
proposals. The public API contract is still to be defined. Validate product
behavior and provider access before treating a proposal as production behavior.

## Suggested workflow for new features

1. Explore the user flow in `metro-parking-app` with clearly labeled fixtures.
2. Agree on inputs, loading/empty/error behavior, accessibility, and acceptance
   criteria.
3. Inspect provider documentation and make controlled calls with approved
   credentials. Record observed request/response behavior without committing
   credentials or personal travel data.
4. Draft the public API contract in this repository's OpenAPI document before
   production integration. Define timezones, field semantics, limits, errors,
   and data provenance.
5. Implement the provider adapter and backend contract; then generate frontend
   types and replace the prototype adapter.
6. Validate provider behavior with sanitized fixtures and focused tests, and
   verify UI success, empty, and failure states.

## Planned timetable feature

### Timetable route search

Build TripView-style timetable route search using TfNSW schedule feeds and our
own routing. Users choose named stations/stops, travel date/time, and
depart-after or arrive-by. Coordinate/address routing, map display, and walking
legs belong to a future map-based Trip Planner and are deferred. The TfNSW
`/trip` API may be useful as a behavior reference, but it is not the intended
routing engine.

The requested network coverage is:

- **First milestone:** Sydney Trains and Sydney Metro routes between stations.
- **Planned expansion:** Sydney and regional buses; Sydney Ferries; Sydney and
  Newcastle light rail; NSW TrainLink regional trains and coaches; other
  timetable services in the TfNSW feed.
- **School buses:** school-bus routes (`route_type=712`) appear in the public
  Complete GTFS capture. Keep the first milestone focused on train routes;
  assess these records later as part of broadening the public timetable modes.

#### Confirmed implementation direction

- Build dedicated backend timetable ingestion, normalized schedule, and
  snapshot-publication components, plus a frontend routing module. Keep the
  backend components separate from `CarParkApiClient`, parking history, and
  parking sync state.
- Keep TfNSW credentials on the backend. The browser calls only our versioned
  API; it cannot choose upstream feed calls, realtime polling frequency, or
  provider endpoints.
- Fetch feeds on a controlled server schedule based on provider update
  cadence. Validate each full candidate snapshot before atomically publishing
  it. A failed or partial refresh keeps the last known good snapshot.
- Start with production Sydney Trains and Sydney Metro feeds. Prove stop search
  and route output using representative fixtures and hand-checked examples
  before broadening modes. Respect each feed's license and attribution terms.
- Define the versioned API data contract in OpenAPI before frontend
  integration. The backend publishes a compact normalized snapshot package
  with named stop IDs, service/route variants, explicit transfer links,
  Sydney-time event instants, actual date coverage, freshness, and source
  version. The frontend applies origin/destination, Sydney local date/time,
  depart-after/arrive-by, and mode filters and builds normalized timetable
  route results from that package. Do not expose raw GTFS tables or provider DTOs;
  retain feed namespace/provenance for safe realtime joins. Keep one production
  routing engine so transfer and time behavior cannot drift between tiers.
- Coordinate the timetable and realtime call budget with parking ingestion.
  The service operator controls upstream cadence and any reduction in parking
  polling; user traffic cannot increase provider calls.

#### Provider quota and polling budget

TfNSW currently documents the default Bronze plan as 60,000 API calls/day and
5 calls/second. It recommends daily schedule-feed updates and realtime requests
no more frequently than the feed's update interval (commonly 10–15 seconds).
The plan limits and feed cadence can change, so recheck them before rollout.

- Maintain a server-side daily call budget across timetable downloads,
  realtime alerts/updates, parking catalog/live/history calls, and retries.
  Confirm whether the provider applies quota per API, credential, or
  application; until confirmed, budget conservatively as if all TfNSW calls
  share one Bronze allowance.
- Reserve a defined portion for existing parking operations and retries, then
  allocate the remainder to timetable refresh and realtime features. Record
  estimated/actual calls by provider operation and alert before the safe budget
  is exhausted.
- Fetch static schedule data only on a controlled cadence (normally at most
  once per day per feed, subject to its published update schedule). Use
  conditional requests or feed-version checks if supported and verify whether
  those checks count toward quota.
- Choose a fixed realtime polling interval per feed, no faster than provider
  guidance; share one poll result among all clients and cache it server-side.
  If the budget is tight, reduce or suspend realtime polling and preserve
  static timetables and parking sync according to the configured priority.
- Prevent retries from multiplying calls during provider outages; do not let
  frontend polling fan out into upstream calls. Any change to parking
  frequency must be evaluated against its existing freshness requirements.
- Add configurable server-side limits and operational metrics, not user-facing
  controls for provider cadence. Keep keys out of logs and redact auth headers.

#### Timetable sources and coverage to establish

The initial routing source is the production **Public Transport - Timetables -
For Realtime** schedule feeds: Sydney Trains and Metro v2. [Timetables Complete
GTFS](https://opendata.transport.nsw.gov.au/data/dataset/timetables-complete-gtfs)
provides static stops, routes, calendars, stop times, and shapes across
operators, including routes not in real-time feeds. The separate [Timetables
Complete TransXChange](https://opendata.transport.nsw.gov.au/dataset/timetables-complete-transxchange)
dataset provides static timetables in XML for all operators, including regional
and private operators and services absent from real-time feeds. TfNSW's
developer guide says this is also where all school bus routes are included;
that guide's statement about school bus coverage in GTFS is explicitly
time-qualified. The current Complete GTFS sample includes route type 712, so
the older documentation is stale on this point; verify continuing production
coverage and usage policy before exposing those routes to riders.

The [Timetables - For Realtime GTFS feeds](https://opendata.transport.nsw.gov.au/dataset/public-transport-timetables-realtime)
are a smaller static subset for operators with real-time support. The catalog
lists Buses, Ferries, Light Rail, Trains, Metro, and Regional Bus Services
(TCB). Separate schedule API endpoints cover modes/operators; the saved Swagger
[`getschedule-v2_1.0.json`](provider-docs/public-transport/timetables/realtime-v2/getschedule-v2_1.0.json)
is already in this repository. Inspect its current paths and resource details
for exact Sydney versus regional operator coverage. NSW TrainLink's technical
specification documents regional rail and coach route types; verify where both
appear in current complete and realtime feeds.

GTFS-RT alerts are in scope as a distinct overlay; the captured JSON already
has route-ID examples matching the initial schedules. Trip updates and vehicle
positions are later additions, after their identifiers are reconciled with
the matching static feed. Both use GTFS-Realtime Protocol Buffers.

| Dataset | Role | Initial decision |
| --- | --- | --- |
| [Timetables Complete GTFS](https://opendata.transport.nsw.gov.au/data/dataset/timetables-complete-gtfs) | Broad static comparison and future-coverage feed. Its identifiers do not match realtime. | Use for coverage research and later modes. Do not fill gaps in or merge IDs with the initial realtime-compatible schedules. |
| [Timetables Complete TransXChange](https://opendata.transport.nsw.gov.au/dataset/timetables-complete-transxchange) | Static XML timetable source for all operators, including routes not available in realtime; documented school-bus coverage. | Inspect to determine whether needed regional or school services are absent from GTFS. Prefer avoiding a second parser unless required; do not treat school routes as generally boardable without confirming policy. |
| [Public Transport - Timetables - For Realtime](https://opendata.transport.nsw.gov.au/dataset/public-transport-timetables-realtime) | Realtime-compatible mode/operator schedules, including Sydney Trains. | Authoritative for the initial Sydney Trains route graph and its realtime joins. |
| [Public Transport - Timetables - For Realtime v2](https://opendata.transport.nsw.gov.au/data/dataset/public-transport-timetables-realtime-v2) | Newer schedule feed family; Metro v2 is captured. | Authoritative for initial Metro schedule and realtime joins. Use the corresponding mode schedule for later realtime-covered modes. |
| [Transport Routes](https://opendata.transport.nsw.gov.au/data/dataset/transport-routes) | TfNSW describes this API as a master route/service-by-operator reference list, with operator details, colors, and icons. | New candidate for route/line display and badge metadata, especially contracted buses. Capture its API response and documentation; verify route/operator coverage and whether IDs map to GTFS before using it in the domain. |
| [Historical GTFS and GTFS Realtime](https://opendata.transport.nsw.gov.au/dataset/historical-gtfs-and-gtfs-realtime) | Historical GTFS-Realtime data; current documentation describes Metro and Ferry coverage. | Optional source for replay fixtures and observed-service validation, not a live timetable or runtime dependency. Check current data availability and retention before relying on it. |
| [Public Transport - Realtime Trip Update](https://opendata.transport.nsw.gov.au/data/dataset/public-transport-realtime-trip-update) | Broader v1 updates for buses, ferries, light rail, NSW Trains, Metro, and regional bus services. | Candidate for later-mode delay predictions. The provider says v2 supersedes v1 for Sydney Trains, Metro, and Inner West Light Rail; confirm mode-specific endpoint and IDs before use. |
| [Public Transport - Realtime Vehicle Positions](https://opendata.transport.nsw.gov.au/data/dataset/public-transport-realtime-vehicle-positions) | Broader v1 vehicle positions for buses, ferries, light rail, NSW Trains, Metro, and regional bus services. | Optional later live map/vehicle context. Do not add to first milestone; use v2 for the modes it supersedes and verify schedule joins first. |
| [Public Transport - Realtime Trip Update v2](https://opendata.transport.nsw.gov.au/data/dataset/public-transport-realtime-trip-update-v2) | Delay and stop-time updates for Metro, Sydney Trains, and Inner West Light Rail. | Later prediction layer after static routing; confirm operator coverage and identifier joins before release. |
| [Public Transport - Realtime Vehicle Positions v2](https://opendata.transport.nsw.gov.au/data/dataset/public-transport-realtime-vehicle-positions-v2) | Vehicle positions for Sydney Trains, Metro, and Inner West Light Rail. | Optional later map/context layer; verify schedule joins and quota before adding. |
| [Public Transport - Realtime Alerts v2](https://opendata.transport.nsw.gov.au/data/dataset/public-transport-realtime-alerts-v2) | Stop, trip, and route-level service alerts across bus, train, ferry, light rail, Metro, and coaches. | Include relevant alerts with route results where joins validate; preserve freshness and provenance. |

The catalog also lists [Public Transport - Location Facilities and
Operators](https://opendata.transport.nsw.gov.au/data/dataset/public-transport-location-facilities-and-operators).
Its location and operator CSVs may help investigate station/interchange
metadata, but TfNSW says the Gateway API is deprecated and the data is no
longer updated. Prefer current timetable stop data and reviewed mappings; use
those files only as a cross-check, not as a live dependency.

The available [sample/test GTFS package with school bus routes](https://opendata.transport.nsw.gov.au/dataset/temporary-andor-sample-gtfs-data)
is old test data and should not be used in production. The current Complete
GTFS capture is the relevant evidence for route type 712; still verify access
terms and whether school services are intended for general trip planning.

#### Routing behavior and data model to decide

- Build timetable routes from published stop times, trips, routes, calendars, and
  calendar exceptions. Respect service dates, timezone, overnight times beyond
  midnight, and trips crossing daylight-saving transitions.
- Search station/stop names from timetable data. Keep provider stop IDs and
  feed/operator namespaces stable; duplicate stop IDs across feeds must not
  create accidental transfers or merges.
- Support depart-after and arrive-by results, a bounded number of alternatives,
  direct routes and transfers, transfer wait, total duration, and per-leg
  service/operator/stop details. Specify whether the first release is direct
  train-only or allows transfers to Metro and other included modes.
- Keep one optional routing adjustment: additional minimum transfer wait time.
  Automatic uses the minimum feasible time supported by known pathway and
  reviewed interchange data, with a documented safety minimum when physical
  traversal time is unknown. The user setting adds wait without undercutting
  known walking time. Keep walking and connection wait distinct in results.
- Support optional via-stops. Resolve selected stop IDs against the optimized
  travel-date package and compute the constrained timetable route in the frontend
  worker.
- Retain accessibility data where feeds provide it, but defer accessibility
  filters and UI until the required APIs and transfer detail are established.
  Do not claim an itinerary is accessible on incomplete evidence.
- Decide how transfers are inferred: GTFS parent stations, pathways, explicit
  transfer data, or a curated interchange mapping. Do not infer walkable
  transfers solely from similar stop names or proximity.
- Stage modes so routing quality can be checked on a bounded operator set
  before adding every bus and coach. Establish a reliable timetable refresh and
  atomic feed-swap process; user searches should not fetch large timetable
  bundles synchronously.
- Keep realtime alert overlays distinct from static schedules from the start.
  The captured JSON has initial Sydney Trains and Metro route-ID matches;
  broaden joins after validating stop/trip targets and active-period behavior.
  Add trip delay predictions after trip-update joins are verified.
- Document that schedules are published timetable data, not guarantees of
  real-time operation. Show live delay status only when a relevant realtime
  update exists and is fresh.

#### Provider docs and APIs that are still relevant

The saved [TfNSW Trip Planner Swagger](provider-docs/public-transport/trip-planner/tripplanner_v1_swag_efa11_20251002.yml)
describes `/stop_finder`, `/trip`, `/departure_mon`, `/coord`, and `/add_info`.
For this feature, `/stop_finder` and `/departure_mon` may inform station search
and departure-board behavior. `/trip` accepts coordinates and delegates route
calculation to TfNSW, so it is not the target routing approach. Confirm whether
any API use or response copying is allowed if using it only for comparison.

The [Opal Fares dataset](provider-docs/Opal%20Fares/) now has the June 2026
distance/fare workbook and business-rules PDF. TfNSW publishes these as
downloadable reference data, not a standalone fare-calculation API. Normalize
the applicable rail/Metro tables and rule revision into a separately versioned
cacheable fare package. Fare estimates need an explicit fare category/payment
type and service time. Default to Adult Opal and let users choose another
supported fare profile; persist this small preference in browser storage.
Daily/weekly caps and transfer benefits may depend on the rider's other taps
and eligibility, so show estimates with limitations unless that history is
modeled. Add the Regional Bus Fares data when regional buses enter scope.
The backend now has a small in-process `timetable.opal.domain` module with fare
category, payment method, mode, and fare-period enums; an Adult Opal default
profile; the v1.7 rule constants; a fare-table snapshot shape; and an
itinerary transfer-discount calculator. Keep it as a domain module in this
service rather than a microservice. The calculator consumes already-priced
same-mode sections from the versioned fare data; it is not a route solver.
Workbook ingestion, stop-pair mapping, and hand-checked tariff examples remain
to be completed before exposing production estimates. Decide in the API
contract whether clients submit itinerary sections to this calculator or use
the fare package locally; preserve one authoritative fare-calculation path.

The existing [Car Park API](https://opendata.transport.nsw.gov.au/data/dataset/car-park-api)
can later connect a Park&Ride facility to nearby rail stations and show live
parking availability as an optional trip origin workflow. The catalog says its
documentation was updated on 11 August 2026 and mentions an all-car-parks
occupancy operation; compare it with the saved API specification before use.

The [Toll Calculator API](https://opendata.transport.nsw.gov.au/data/dataset/toll-calculator-api)
and [Live Traffic Hazards](https://opendata.transport.nsw.gov.au/data/dataset/live-traffic-hazards)
are road-oriented and are out of scope for this timetable-first release.

#### Proposed implementation boundaries (to decide before coding)

- Add a dedicated timetable ingestion, normalized schedule model, and routing
  service; keep them separate from `CarParkApiClient` and parking history.
- Start by ingesting the production Sydney Trains and Metro feeds and prove
  stop search and route results against hand-checked examples before broadening
  modes. Use representative fixtures and respect each dataset's license.
- Publish a versioned API for compact normalized schedule packages, stable stop
  identifiers, explicit transfer edges, snapshot freshness, and actual covered
  dates. The frontend applies Sydney local date/time, depart/arrive selection,
  and mode filters to compute timetable routes. Define timezone and DST behavior,
  package limits, freshness, and errors in OpenAPI before wiring the frontend.
- Do not expose raw GTFS tables or provider DTOs as the app contract. Keep feed
  namespace and provenance internally so static schedules and live updates can
  be joined safely.
- Publish realtime alerts as a separate overlay with affected entity, validity
  window, source, and retrieval time. The frontend attaches matching alerts to
  locally computed timetable routes and keeps realtime values distinct from
  timetable times.
  Surface when alert data is unavailable or stale; verify stop/trip matching
  and active-period edge cases before release.
- Treat provider route rows as service variants; directional variants are
  acceptable, and a canonical branded line is not required in the first
  contract. Preserve provider names and colors where present.
- Drop the parking-to-line badges as a product feature; they can imply that a
  particular route is always available from a car park. Do not store a
  facility-to-line catalogue. If Park&Ride station context is needed later,
  use the provider-defined TSN to resolve the stop and derive route variants
  from the current timetable snapshot.
- The backend owns provider ingestion, calendar resolution, timezone handling,
  snapshot validation, and optimized snapshot generation. Publish a compact,
  versioned normalized domain package for the frontend; do not send raw GTFS
  files or provider DTOs as the app contract.
- The frontend handles interactive route search against a selected travel-date
  package in a Web Worker and caches packages in IndexedDB. The manifest
  advertises Sydney yesterday through today+6 inclusive (eight dates), subject
  to feed validity; fetch one travel date at a time and include earlier
  service-day trips whose stop events land on that date. Wednesday therefore
  covers Tuesday through the following Tuesday. Outside-validity requests show
  a clear unavailable-date state.
- Include static train crowding categories only where the source feed provides
  a dated trip/stop profile; keep realtime carriage crowding predictions in
  the live overlay. These are qualitative signals, not passenger counts, and
  must show source/freshness. Do not imply Metro coverage until its feed proves
  it.
- Include Opal fare estimates as a separate client domain/package, versioned
  independently from dated schedule packages. The initial fare profile must
  default to Adult Opal and allow the user to choose another supported fare
  category/payment type; label caps or personal transfer benefits that cannot
  be calculated without the rider's broader tap history.
- User searches must never trigger provider calls. Keep full compressed source
  bundles and the last-known-good snapshot on persistent backend storage. The
  current 1 GB droplet/512 MB Atlas setup makes one Mongo document per stop-time
  row a poor fit; the production feeds contain about 1.61 million stop-time
  rows. A compact indexed-call estimate from current captures is 0.47–0.75 MB
  gzip per date for calls alone; the eight-date bundle is about 4.9 MB before
  metadata. Treat <2 MB as the compressed per-date target, then measure the
  completed package, browser transfer/decode, phone memory, worker search
  latency, and backend index heap. Use version/checksum checks and split by
  mode only if a daily package exceeds the target.
- Keep realtime alerts and trip updates separate from the static snapshot,
  with independent retrieval timestamps. Saved timetable search inputs can use browser
  local storage; cookies should remain small.
- If the frontend owns production timetable route search, make it the single routing
  implementation. Specify query-time DST behavior, transfer data, date
  coverage, snapshot freshness, package limits, and errors in OpenAPI before
  wiring the frontend. Do not maintain a second backend route solver with
  diverging results.

#### Acceptance criteria to agree

- Users can search and select station/stop names without entering coordinates,
  then request departures-after or arrivals-by for a Sydney local date/time.
- Initial timetable routes show departure/arrival, service/line, transfer
  stops, and total elapsed time with clear no-route and invalid-time states.
- Relevant realtime delays and service/event alerts are shown beside affected
  timetable routes with their source and freshness; static schedule data remains
  clearly distinguishable from realtime information.
- Users can adjust minimum transfer wait time and see crowding/fare estimates
  only where snapshot sources support those values; estimates expose their
  fare profile, rule revision, and limitations.
- Users can optionally request a timetable route through selected via-stops; the
  frontend computes it from the same cached timetable package.
- Validity dates, weekday/weekend service, exceptions, after-midnight service
  times, and timezone behavior produce correct results.
- The first documented release scope states exactly which operators are
  included. Train routes are the first priority; public school-bus records can
  be evaluated later, with coverage and licensing confirmed before inclusion.
- Timetable refresh failures do not replace the last known good snapshot;
  stale schedules are observable and surfaced appropriately.
- The OpenAPI contract and app flow agree before real feed-backed frontend
  integration. Realtime delay indicators remain distinct from static schedule
  times.

#### First provider inspection batch

Use the Data Hub Swagger UI with the registered API key in its Authorization
header (`apikey <TOKEN>`). These schedule endpoints return ZIP/binary data, not
JSON objects. Make only a few manual calls for this inspection; do not set up a
polling loop yet.

| What to inspect | Dataset / Swagger | Operation to try |
| --- | --- | --- |
| Sydney Trains static schedule | [Timetables - For Realtime](https://opendata.transport.nsw.gov.au/data/dataset/public-transport-timetables-realtime); [schedule Swagger UI](https://opendata.transport.nsw.gov.au/dataset/public-transport-timetables-realtime/resource/9b3bfa13-0053-4008-8575-e30151f05d54/view/2c71bc94-97cd-457c-ba6e-4e5758bc0ecd) | `GET https://api.transport.nsw.gov.au/v1/gtfs/schedule/sydneytrains` |
| Sydney Metro static schedule | [Timetables - For Realtime v2](https://opendata.transport.nsw.gov.au/data/dataset/public-transport-timetables-realtime-v2); saved Swagger [`getschedule-v2_1.0.json`](provider-docs/public-transport/timetables/realtime-v2/getschedule-v2_1.0.json) | `GET https://api.transport.nsw.gov.au/v2/gtfs/schedule/metro` |
| All-operator static comparison feed | [Timetables Complete GTFS](https://opendata.transport.nsw.gov.au/data/dataset/timetables-complete-gtfs); saved Swagger [`prod_fixed_gtfs_2.0.json`](provider-docs/public-transport/timetables/complete-gtfs/prod_fixed_gtfs_2.0.json) | `GET https://api.transport.nsw.gov.au/v1/publictransport/timetables/complete/gtfs` |
| Agency and identifier reconciliation | [Reference Tables for TfNSW GTFS feeds](https://opendata.transport.nsw.gov.au/dataset/reference-tables-tfnsw-gtfs-feeds) | Download the latest overlapping-agency and train-run-number CSVs. |
| Route/line reference metadata | [Transport Routes](https://opendata.transport.nsw.gov.au/data/dataset/transport-routes) | Capture the API response and schema/docs; compare operator, route number, variant, mode, colors/icons, and IDs with the matching GTFS `agency.txt` and `routes.txt`. |
| Sydney Trains and Metro service alerts | [Realtime Alerts v2](https://opendata.transport.nsw.gov.au/data/dataset/public-transport-realtime-alerts-v2); saved Swagger [`v2-gtfs-alerts-swaggerprod_1.yaml`](provider-docs/public-transport/realtime/alerts-v2/v2-gtfs-alerts-swaggerprod_1.yaml) | `GET https://api.transport.nsw.gov.au/v2/gtfs/alerts/all?format=json` once for a readable sample; mode-specific routes are also available. |
| Sydney Trains and Metro live trip updates | [Realtime Trip Update v2](https://opendata.transport.nsw.gov.au/data/dataset/public-transport-realtime-trip-update-v2); saved Swagger [`getrealtime_v2_03.yml`](provider-docs/public-transport/realtime/trip-updates-v2/getrealtime_v2_03.yml) | `GET https://api.transport.nsw.gov.au/v2/gtfs/realtime/sydneytrains` and `/metro`. This is a useful second pass for delays after static schedules and alerts. |

For each ZIP schedule response, preserve the request URL, status, response
headers (`Content-Type`, `Content-Length`, `Last-Modified` if present), download
time, and ZIP file list. For a compact review, include headers plus a few
representative rows from `agency.txt`, `stops.txt`, `routes.txt`, `trips.txt`,
`stop_times.txt`, `calendar.txt`, `calendar_dates.txt`, and any `transfers.txt`,
`pathways.txt`, or other extension tables present. Include rows for one
hand-checked train route and one Metro route; keep IDs intact so we can
reason about feed namespaces. Do not paste the API token.

Realtime alert and trip-update responses are GTFS-Realtime Protocol Buffer
messages. The alert sample at
[`gtfsr-alerts_20261028211837.json`](apis/v2-gtfs/alerts-all/gtfsr-alerts_20261028211837.json)
was retrieved with `format=json`. Readable v2 trip-update captures are now in
[`v2-gtfs/realtime/`](apis/v2-gtfs/realtime/); their trip, route, and stop IDs
were compared against the corresponding static feeds. Preserve response
timestamps and any available request/response metadata with future captures.

The saved Complete GTFS Swagger explicitly warns that its identifiers do not
match the GTFS-Realtime APIs. The Reference Tables explain overlapping agencies
and duplicate train runs, including guidance to prefer NSW Trains for certain
services also present in Sydney Trains. Keep the per-feed identifiers and
agency mappings with every sample; do not merge records by ID or display name
until the sample data and reference tables show a valid mapping.

Do not use the old sample school-bus GTFS package as production input. Once the
first feeds are understood, inspect current Complete TransXChange coverage for
regional coaches and school buses. The TfNSW `/trip` operation is not
needed for this batch; use it only later as an optional comparison against a
hand-built route, never as the timetable routing implementation.

## Deferred proposals

### Occupancy forecast

- Treat any forecast as experimental and distinguish it clearly from observed
  occupancy.
- Defer it substantially. A credible model needs much more history and must
  account for weekday/weekend patterns, public and school holidays, and
  occupancy fluctuations; current compact ten-minute history is not sufficient
  evidence by itself.
- If forecasting becomes a product requirement, first define the additional
  data retention and storage budget, then prefer a documented backend method
  with an uncertainty range and testable inputs over client-side extrapolation.
