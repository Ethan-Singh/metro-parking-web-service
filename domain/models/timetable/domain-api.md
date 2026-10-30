# Timetable domain API

## Status

The v1 OpenAPI draft is
[`docs/apis/timetable-api-v1.yaml`](../../../docs/apis/timetable-api-v1.yaml).
The manifest and date-package operations are implemented. The realtime read
operation and Sydney Trains/Metro alert, trip-update, and vehicle-position
ingestion are implemented. The Opal fare operation remains design-only. Treat the OpenAPI as the intended
service-owned contract and keep its implemented package fields aligned with
the JSON produced by the snapshot builder. The models in this folder describe
normalized domain concepts, not protobuf messages or GTFS tables.

The manifest and date-package read endpoints are implemented. Captured-feed
bootstrap is opt-in with `--timetable.snapshot.bootstrap-enabled=true`; it
reads the configured production Sydney Trains, Metro, ReplacementBus, and PCBC ZIPs in
`application.yaml`, builds yesterday through today+6, and writes versioned gzip
packages to the timetable Mongo collections. It validates every package's
compressed-size target before publishing the active manifest. ReplacementBus
and PCBC are loaded only as supporting sources inside the Sydney Trains
package. The reviewed `9T2` Leppington–Glenfield replacement and `11T2`
Olympic Park–Ashfield contingency routes carry service purpose/context and
explicit stop interchanges; neither is a standalone offering. Each refresh currently materializes eight
dates and three stored representations per date (`ALL`, `SYDNEY_TRAINS`, and
`SYDNEY_METRO`). The public `ALL` package combines the two offerings and the
reviewed supporting legs inside Sydney Trains.
The current builder advertises the rolling eight-date window even when an
individual feed produces no trips for a date; intersecting each offering's
coverage with actual service validity remains an implementation follow-up.

The real provider refresh path uses the TfNSW Sydney Trains v1
`/v1/gtfs/schedule/sydneytrains` and Metro v2 `/v2/gtfs/schedule/metro`
endpoints. It checks `Last-Modified` with `HEAD` every six hours and downloads
all four feeds only on first load or when any revision changes. Set
`TFNSW_APIKEY` to the complete provider Authorization header value (for
example, `apikey <token>`) and `TIMETABLE_PROVIDER_ENABLED=true` to enable it.
The credential remains server-side. Downloaded source ZIPs are temporary build
inputs; only validated compressed client packages and small feed revision
state are kept. Refresh errors leave the active manifest and packages
unchanged. Current published package fields are `stops`, `routes`, `trips`,
`calls`, `interchanges`, `sources`, and offering status metadata. Supporting
trips carry `servicePurpose` and `serviceContext`; their IDs retain their own
feed namespace. Public stop,
route, and trip IDs are namespaced with the source feed key; static call times
are RFC 3339 instants with `Australia/Sydney` retained for display.

## Service boundary

The backend owns provider credentials, controlled feed refresh, validation,
normalization, and atomic publication of a last-known-good snapshot. The client
downloads an optimized date-specific package and performs stop lookup and
route calculation locally. Realtime alerts, trip updates, and vehicle positions
are a separately versioned overlay; fare data and rules have their own revision
lifecycle. Provider polling is backend-controlled and never triggered by a
client request.

## Package transport format

Keep the public package API as versioned HTTP REST with normalized JSON. The
date packages are already stored in gzip form and sent with
`Content-Encoding: gzip` when the client advertises support; otherwise the
controller returns decompressed JSON. Captured production packages measured
about 0.12 to 1.11 MB compressed, within the 2 MB target.

gRPC would not make the current JSON representation smaller by itself. A
smaller wire format would require defining and versioning protobuf messages for
our own package contract, generating and maintaining browser-compatible
clients, and changing the API consumers. Standard browser clients also need a
gRPC-Web-compatible serving path; gRPC-Web uses a browser-specific protocol and
proxy path ([official browser guidance](https://github.com/grpc/grpc-web/blob/master/doc/browser-features.md)).
Keep JSON plus HTTP compression while the measured package size and client
parsing remain acceptable; revisit only if real package or performance
measurements show a bottleneck.

The draft also defines a separately versioned realtime overlay and normalised
Opal fare package. The overlay contains alerts, trip updates, and vehicle
positions for the initial Sydney Trains and Metro sources. It retains useful
optional metadata and provenance without exposing raw GTFS tables, protobuf
messages, or provider DTOs as the application contract. The
date package contains the searchable stops, so there is no per-keystroke
stop-search endpoint. Route alternatives are calculated by the client against
the package and realtime overlay; there is no duplicate backend journey solver.

## Draft contract decisions

- Each timetable offering reports its own state, supported facets, and covered
  Sydney dates. The only initial user-facing offerings are Sydney Trains and
  Sydney Metro. ReplacementBus and PCBC are supporting schedule feeds. The
  reviewed `9T2` and `11T2` routes are included inside the Sydney Trains
  package with purpose, affected segment, service window, and stop-link
  provenance. They are not standalone offerings. Retain
  their feed keys and provenance internally and in source metadata.
- Advertise yesterday through today plus six days, inclusive, intersected with
  actual service validity. A date package includes earlier service-date trips
  whose resolved calls fall on its travel date.
- A valid but unavailable date/offering returns an empty normalized package
  with explicit availability metadata, so it is distinguishable from an
  available day with no matching route. Malformed dates and offering values
  return a problem response.
- Use ETags and conditional requests. Allow HTTP compression. Keep ordinary
  HTTP cache policies appropriate to each representation: date packages can
  be cached by revision and revalidated through the date alias; manifest and
  realtime data should be revalidated frequently. Exact `max-age` values are
  operational configuration, not domain semantics.
- Target no more than 2 MB per compressed date package. Initial captured
  production builds for 29 Oct through 5 Nov 2026 ranged from about 0.12 MB to
  1.11 MB gzip before the supporting schedules were included. Re-measure the
  updated package against the target after building the supporting feeds.
- Store versioned, precompressed client package documents in the timetable's
  own MongoDB collection, separate from parking collections. Write immutable
  packages for a candidate snapshot first, validate them, then publish the
  manifest/active-snapshot pointer last. Retain the previous known-good
  snapshot for rollback. At the measured size, a date package fits comfortably
  within MongoDB's 16 MiB document limit when stored as compressed binary; do
  not store one document per stop-time row. Keep retention bounded and measure
  total use against the roughly 300 MB available to this service.
- Timetable dates and user-selected times are Sydney local. Scheduled events
  are absolute instants serialized with their RFC 3339 UTC offset, and carry
  the `Australia/Sydney` IANA timezone indicator and service-date provenance
  for local display. Route search is local; the API does not accept a client
  query time. Do not interpret timetable values in the device timezone or add
  other-city timezone behavior to this Sydney domain. The frontend may detect
  and explain when a selected Sydney local time falls in a DST gap or repeated
  hour; this does not change the stored event instants.
- Realtime has an independent version and per-source retrieval, attempt, and
  last-success timestamps. The contract does not impose a freshness threshold.
  Missing or stale live data must not hide scheduled times. Failed refreshes
  retain the last known good source data and report its state.
- Stop, route, trip, and alert identifiers retain feed provenance. Unmatched
  and ambiguous realtime references remain representable; do not join by a
  coincidental provider ID string or fabricate corrections to bad source data.
- The Opal fare endpoint serves normalized, versioned reference data with
  effective dates, limitations, and attribution. The client selects a fare
  profile (adult by default) and estimates from the returned rules. The
  maintained Opal XLSX is the fare source of truth; update the normalized
  package when a new workbook is published. The exact fare-rule schema still
  needs review against the fare domain model.

The static package contract has a working implementation and tests. Before
frontend integration, confirm its exact response examples against the
published endpoint. Realtime refresh fetches six sources per minute by default:
two JSON alert feeds and four binary trip-update/vehicle-position feeds. That
is 8,640 provider requests per day before retries. Binary feeds are decoded
with the pinned endpoint schema variants; the normalizer accepts only
`FULL_DATASET`, applies full-feed entity replacement, and retains provider
feed keys. A conservative schedule matcher uses the active published snapshot
and an explicit realtime-to-schedule feed mapping. It marks only unique
trip-ID matches as `MATCHED`, checks supplied route and service-date values,
and retains ambiguous or unmatched entities with their status. Stop updates
join only through exact provider stop ID and sequence within a matched trip.
The endpoint-specific schemas and extensions are described in
[`realtime-domain.puml`](realtime-domain.puml) and
[`docs/provider-docs/proto/README.md`](../../../docs/provider-docs/proto/README.md).

## Offering facets

Represent each service method as an independently versioned offering. The
minimum facets are listed in [`model-spec.md`](model-spec.md); supported facets
may grow when provider feeds and reviewed mappings supply reliable data. Bus
offerings may expose route, locality/suburb, and stop facets. Train offerings
use stations (with platforms available for boarding detail), ferries use
wharves, and light rail uses stops.

Saved plans, user naming, route constraints, and local route options belong to
the client. Store up to 20 saved query presets in versioned browser
`localStorage`; they are not part of the timetable API payload. Handle storage
being blocked or cleared. Keep secrets and sensitive data out of saved plans.
