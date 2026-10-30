# Timetable domain model (draft)

This is a domain design draft based on the provider captures in
[`docs/apis/`](../../../docs/apis/) and the official feed specifications in
[`docs/provider-docs/`](../../../docs/provider-docs/). It describes normalized
concepts, not Mongo collections, GTFS DTOs, or the public API schema. Keep the
provider boundary separate from the public contract.

See [domain.puml](domain.puml) for the overview and the [timetable README](README.md)
for the focused schedule, route-planning, realtime, and fare diagrams.

## Evidence in the current captures

The current captured feeds are enough to start modeling the first two networks:

| Capture | Observed shape |
| --- | --- |
| Sydney Trains schedule, captured 27 Oct 2026 | 2 agencies (`Sydney Trains`, `NSW Trains`), 137 route records, 84,770 trips, 1,214 stops, 142 service-calendar rows, and route type `2`. It has TfNSW extension tables for occupancies and vehicle composition. |
| Sydney Metro schedule, captured 26 Oct 2026 | 1 agency, 1 route record (`M1`, route type `401`), 3,117 trips, 93 stops, 9 service-calendar rows, an empty `calendar_dates.txt`, and Metro-specific trip/stop-time fields. |
| ReplacementBus schedule, captured 28 Oct 2026 | Route `7063_9T2` (`9T2`, “Leppington, then all stations to Glenfield and return”), with Sunday 1 Nov 2026 service calling at Leppington, Edmondson Park, and Glenfield. TripView's 08:06–08:36 example matches the route and arrival; captured static departure is 08:07. |
| PCBC schedule, captured 28 Oct 2026 | Separate planned-contingency feed; keep its IDs and services isolated from ReplacementBus and the general bus network. |
| Timetable stop locality fields | All four captured Sydney Trains, Metro, ReplacementBus, and PCBC `stops.txt` files have empty `stop_desc`. Their names may describe a station, platform, road, or bus stand, but do not consistently provide a suburb. The saved Trip Planner response identifies Central as `Central Station, Sydney`, with parent `Sydney` of type `locality`. Its Swagger defines `StopFinderLocation.name`, `disassembledName`, and parent types including `locality` and `suburb`. |
| Complete GTFS, captured 28 Oct 2026 | 690 agencies, 10,256 route records, 212,230 trips, 171,276 stops, calendar exceptions, and pathways. Observed route types include `2`, `4`, `106`, `204`, `205`, `401`, `700`, `712`, `714`, and `900`; `712` rows are described as school buses. |

The Trip Planner Swagger documents stop modes for train, metro, light rail,
bus, coach, ferry, and school bus, plus a hierarchical location result with
`name`, `disassembledName`, `parent`, and locality/suburb types. This confirms
the provider contract can represent the requested distinctions. The saved
Trip Planner body is a Central departure-monitor response, not a standalone
Stop Finder capture; it confirms one Central-to-Sydney locality example only.
The Complete GTFS capture contains routes for the relevant candidate modes,
but remains coverage evidence and is not merged into the authoritative
For-Realtime train/Metro snapshots.

The 28 Oct Complete GTFS capture is the latest full-network snapshot. The older
26 Oct expanded copy under `provider-docs` has been removed. Its Swagger and
pathway documentation remain there.

The dedicated Sydney Trains feed has two agencies in `agency.txt`, and its 137
route rows reference both `SydneyTrains` (81) and `NSWTrains` (56). Do not
assume one feed equals one operator. Metro and Sydney Trains both contain a
parent stop with ID `200060` for Central Station, and Metro has child platform
stops. Keep provider stop references scoped to their feed. The Car Park API
specification defines TSN as a unique TfNSW-network identifier for a stop or
group of stops; use TSN as the canonical cross-feed key where the GTFS data
provides a corresponding value, while retaining each feed-scoped StopRef.

The reference-table capture and the Complete GTFS Swagger both warn about
overlap and ID differences between complete and realtime feeds. For initial
routing, the authoritative inputs are the production **Timetables - For
Realtime** schedule feeds (Sydney Trains and v2 Metro in these captures).
Complete GTFS is useful for coverage research and later modes, but must not be
used to fill gaps in these schedules or joined to their realtime IDs. Retain
source feed key and version so identifiers cannot silently cross namespaces.

## Proposed normalized concepts

### Versioned schedule snapshot

Publish an immutable `ScheduleSnapshot` assembled from the required provider
feed revisions. Keep the source feed key, captured time, `Last-Modified` (when
provided), and checksum with each `FeedRevision`. Validate required tables and
cross-references before the active-snapshot pointer changes. A failed or partial
refresh leaves the previous active snapshot in service.

The initial public offerings are Sydney Trains and Sydney Metro. The release
manifest also identifies `ReplacementBus` and `PCBC` as separate supporting
source feeds, each with its own `FeedRevision`; they are not standalone
offerings. The current reviewed integration includes ReplacementBus route
`7063_9T2` as `REPLACEMENT` on the Leppington–Glenfield segment from 08:00 to
14:00 Sydney time, and PCBC route `700015_11T2` as `PLANNED_CONTINGENCY` on
the Olympic Park–Ashfield segment from 02:00 to 10:00. Both remain inside the
Sydney Trains offering and carry feed-scoped route/trip/stop IDs plus a
`serviceContext` with the reviewed segment, affected rail stops, evidence URL,
and window. Reviewed bus-stop to rail-station links are emitted as explicit
interchanges with provenance; names alone never create a link. The
Leppington–Glenfield mapping is validated against the captured production
ReplacementBus ZIP and the 1 Nov 2026 example. Keep the PCBC schedule
supporting-only as well. An empty but successfully fetched feed is a valid
revision with zero services; a failed or invalid feed retains its
last-known-good revision. Publish a new combined snapshot only when the
required source revisions validate. Do not broaden this to the general bus
network or merge these namespaces with Complete GTFS. Do not make later mode
feeds mandatory; use an explicit manifest for the feed set required by each
release. Retain the full active
backend snapshot and last-known-good snapshot. Publish a separate
compact, versioned client package with its timezone, available travel-date
range, included modes, source revisions, and checksum. The browser can cache
that package in IndexedDB and compare its version/checksum before downloading a
replacement.

For a Sydney-local Wednesday, advertise the rolling travel-date window from
Tuesday (yesterday) through the following Tuesday, inclusive: `today - 1` to
`today + 6`, eight dates total. The manifest must intersect that window with
each feed's actual service validity. Fetch/cache a package for the selected
travel date only; that package includes earlier service-date trips whose
resolved calls fall on that date. This preserves after-midnight arrivals while
keeping daily payloads small.

### Realtime protocol envelope and normalized overlay

The realtime endpoint captures are binary GTFS-Realtime, not JSON. Decode each
source with the matching pinned `gtfs-realtime.proto` base schema and its
provider extension variant; the three captured TfNSW proto files are not
interchangeable. The schema choice belongs to each realtime source definition,
not to a guessed route, trip, or identifier value. See the exact captured
variants and field-number differences in
[`docs/provider-docs/proto/README.md`](../../../docs/provider-docs/proto/README.md).

Normalize the feed envelope as source state: retain `gtfs_realtime_version`,
`incrementality`, provider header timestamp (converted from Unix seconds to an
`Instant`), retrieval/attempt/last-success times, schema key, and source
status. The captured feeds use `FULL_DATASET`; still model `DIFFERENTIAL` and
deleted entities so a future provider change cannot silently leave obsolete
alerts or vehicles in the published overlay. Apply full replacement or
differential deletion only inside that source's namespace. Publish an
independent overlay version and retain last-known-good data per failed source.
Timestamps are evidence; do not invent a freshness threshold or turn missing
updates into zero delay.

The normalized alert, trip-update, and vehicle-position records preserve
feed-scoped provider IDs and represent `MATCHED`, `UNMATCHED`, and `AMBIGUOUS`
schedule joins explicitly. Keep GTFS-Realtime `scheduleRelationship` distinct
from join status: values such as `SCHEDULED`, `ADDED`, `UNSCHEDULED`,
`CANCELED`, `REPLACEMENT`, `SKIPPED`, and `NO_DATA` describe provider service
semantics, not whether our mapping succeeded. Alerts retain all informed entity
selectors and active periods; absent start/end bounds remain unbounded, and
their inclusive/exclusive interpretation must be verified against captured
examples before activation. Trip updates retain provider time/delay and
uncertainty at trip and stop level, plus optional occupancy. Vehicle positions
retain event timestamp, coordinates/status, and schedule match outcome.

Map the captured TfNSW extensions into optional domain fields rather than
exposing generated protobuf types. Preserve vehicle attributes (air
conditioning, wheelchair capacity, model, prior-trip indicator, special
attributes), vehicle-level occupancy, and ordered carriage details (position,
name, occupancy, quiet-carriage, toilet, and luggage-rack attributes) when the
source schema supplies them. The Sydney Trains capture contains vehicle and
consist data; the Metro capture contains vehicle descriptors. Keep these
attributes as provider observations: do not infer user accessibility or
promise a boarding position from them. Accessibility presentation remains
deferred until source coverage and semantics are validated.

### Stops, agencies, lines, and route variants

- `StopRef = (feedKey, providerStopId)` is the internal stop identity. GTFS
  `stop_id` alone is not a cross-feed key.
- Represent cross-feed identity separately as a `NetworkStop` that can have
  an optional TfNSW TSN and multiple member StopRefs. A TSN may identify a
  station or broader stop group; membership alone does not mean two stops are
  interchangeable or walk-connected.
- `Stop` preserves provider long and disassembled names, location type, parent
  reference, platform code, optional coordinates, and boarding accessibility.
  Parent stations are the default search result; platform stops remain
  available to show the actual boarding point. Attach optional typed locality
  metadata (`LOCALITY` or `SUBURB`) with its provider/source reference when
  available. The Trip Planner Stop Finder response is one possible enrichment
  source: its `name` may include the locality, its `disassembledName` omits it,
  and its parent has an explicit locality/suburb type. Keep these source names
  and parent labels separately instead of parsing commas in `stop_name`;
  commas in current feeds mark platform, road, or stand details. The captured
  schedule `stop_desc` fields are empty, so do not claim locality coverage for
  all stops. Any Stop Finder enrichment must be cached and refreshed under the
  provider call budget; it is not a per-keystroke frontend pass-through.
- `AgencyRef = (feedKey, providerAgencyId)` scopes `agency_id` in the same way.
  The provider names and agency IDs are data, not a closed enum.
- Keep `TransitMode` and `TransitLine` separate. Normalize known route types
  for filtering/display while preserving the provider route type and mapping
  provenance. Interpret route types in their feed/agency context: the Complete
  capture describes `106` as regional trains, `205` as temporary coaches, `700`
  as buses, `712` as school buses, `714` as temporary buses, and `900` as light
  rail; `204` is a broader “Regional Trains and Coaches Network” description.
  Do not infer an exact product mode from that `204` value alone. Metro `401`
  is also present in the capture.
- Treat each provider `routes.txt` row as a `RouteVariant`, allowing different
  directions or destinations to appear separately. Do not require canonical
  branded-line grouping for the first release. Preserve provider route ID,
  short/long name, description, mode, and colors; a later display grouping can
  be derived only where feed evidence supports it. A route variant does not
  promise that every stop on the route is served by every trip.
- Derive a compact `ServicePurpose` (`REGULAR`, `REPLACEMENT`,
  `PLANNED_CONTINGENCY`) per trip from the feed namespace and carry it into
  the client package. Treat these feeds as bus transit services. This lets the
  app style and label replacement legs without inferring purpose from route
  names or promising a branded line. Keep the complete ordered call sequence
  and optional provider fields once in the normalized package so expanded legs
  can show every served stop; route legs reference boarding/alighting call
  indexes instead of duplicating full call objects. Do not discard fields that could
  improve user clarity: normalize recognized semantics and preserve useful
  source metadata/provenance as optional fields. Do not infer which train leg a
  bus replaces from names alone; preserve that relationship only when an alert,
  trip update, or reviewed mapping establishes it.
- TfNSW's separate [Transport Routes API](https://opendata.transport.nsw.gov.au/data/dataset/transport-routes)
  is described as a master route/service-by-operator reference list and
  includes colors and icons. Evaluate it as supplemental presentation and
  operator metadata, especially for contracted bus services. Do not replace
  GTFS route rows or assume identifiers map until the API response is compared
  against `agency.txt` and `routes.txt` for the captured feeds.

### Services, trips, and stop calls

- `ServiceCalendar` combines weekday flags and inclusive start/end dates with
  `calendar_dates` additions (`exception_type=1`) and removals
  (`exception_type=2`). A feed may omit the exception file or provide an empty
  one. A service ID can be date-specific, so it is snapshot data rather than a
  permanent public identifier.
- `ScheduledTrip` references a feed-scoped trip, route variant, service, and
  optional shape. Preserve headsign, direction, accessibility, and supported
  feed extensions without requiring every operator to provide them. Trips
  carry `servicePurpose` (`REGULAR`, `REPLACEMENT`, or
  `PLANNED_CONTINGENCY`); supporting trips also carry a reviewed
  `serviceContext`. That context identifies the affected offering/feed and
  segment, rail endpoint stop IDs, evidence reference, and Sydney-local
  effective window. It is static schedule context and does not claim a live
  disruption is active.
- `ScheduledStopCall` is keyed by trip and stop sequence. Keep arrival and
  departure as seconds after the GTFS service-day midnight, not Java
  `LocalTime`: GTFS permits values beyond `24:00:00`. Keep pickup/drop-off,
  timepoint, and optional note fields where present.
- Resolve service dates and event times on the backend in the agency timezone
  (`Australia/Sydney` in the current captures), then publish dated event
  instants in the client package. The frontend accepts Sydney local query
  values and converts them using one documented DST policy: reject nonexistent
  spring-forward times and require an explicit UTC offset for repeated
  fall-back times. Preserve service-day seconds in the full snapshot; never
  reinterpret a `24+` value as a same-day `LocalTime`.

### Station pathways and interchanges

The Complete GTFS capture contains `pathways.txt` and `levels.txt`; the
dedicated rail/Metro first-release feeds do not contain a `transfers.txt`
file. Model
provider pathways as directed or bidirectional station connections with mode,
traversal time, and accessibility attributes when available. A missing pathway
is not proof that two nearby or similarly named stops are walk-connected.

Cross-feed stop grouping should use TSN mappings when present, plus reviewed
interchange mappings for feed stops without TSN data. Model actual transfer
eligibility separately as an explicit `InterchangeLink`, with direction,
minimum traversal/connection time, and provenance. A shared TSN alone must not
create a route edge. The captured Car Park
response has 34 distinct TSNs: 22 match Sydney Trains `stop_id`/`stop_code`
values, and five match Metro `stop_id` values (Metro `stop_code` is blank for
those stops). The provider spec also documents manually created parent TSN
`2093117` for the Manly Vale bus stops because it is absent from GTFS. This
shows that TSN can represent a group or provider-defined parent that is not a
literal GTFS stop row. Validate parent/platform and transfer semantics before
adding an interchange link; never merge stops or create route edges merely
because their names or coordinates are similar.

### Timetable route results

Separate a saved plan from a computed route option. A `SavedTimetablePlan` is
the rider's reusable intent: display name, origin, destination, ordered via
stops, mode/network scope, route constraints, and optional transfer-wait
override. It is client-owned query input, not a provider schedule or a computed
route result. Keep a versioned collection capped at 20 plans. Store the
collection in versioned `localStorage`. It normally requires no
permission prompt, persists across browser sessions on the same browser
profile, and is not attached automatically to HTTP requests. Browsers or users
can still block or clear it, and it does not sync across devices; handle storage
unavailability gracefully. Saved plans are readable and editable by same-origin
JavaScript, so do not store secrets or sensitive data and prevent script
injection through normal application security controls. Validate the collection
when loading and handle unknown schema versions gracefully. Renaming or
extending a plan changes only this saved input. Keep global defaults such as
fare profile and preferred transfer wait in client settings, with an optional
plan-level override.

A cookie was considered for the small collection. `Secure` and
`SameSite=Strict` control transport and cross-site sending, but do not prevent
same-origin JavaScript from reading a script-accessible cookie; making it
`HttpOnly` would prevent the UI from managing it. Matching cookies also travel
with HTTP requests. Prefer `localStorage` for these client-only presets because
it avoids that automatic request transmission; this is a data-minimization
choice, not protection from same-origin script injection.

Each search creates a `TimetableRouteQuery` from a saved plan plus the selected
Sydney date/time and depart-after or arrive-by direction. The browser runs it
against the cached client package in a Web Worker; internal references use
stable public stop IDs, resolved to feed-scoped StopRefs in the package. The
query includes a bounded result count and a route policy: `AUTO` permits all
matching routes; an optional fixed-route policy restricts it to selected
provider route variants. Do not require a canonical branded line to express a
route constraint.

Return a bounded, ranked list of `TimetableRouteOption` values, not a promise of
every possible path. Each option contains ordered `TransitLeg` and
`TransferLeg` values, departure/arrival, elapsed time, transfer/walking/wait
breakdowns, fare estimate where available, active alerts, and the exact
schedule and realtime revisions used. Preserve enough fields for a compact
summary, a detailed stop list, and a timeline/waterfall view. The UI can group
options by provider route variants and show approximate journey times; it must
not label a route group as a guaranteed branded line when the feed does not
establish that identity.

Transfer-wait adjustment is the only additional routing preference in the
initial contract. The default uses the minimum feasible connection threshold
supported by known station/platform pathways and reviewed interchange data,
with a documented safety minimum where physical traversal data is absent. An
optional `additionalTransferWaitSeconds` is added to that threshold at each
change; it must not shorten physical traversal time. Keep transfer walking
time, minimum connection threshold, and actual waiting time distinct; actual
wait is derived from scheduled arrival/departure instants. Defer
must-use/prefer/avoid route rules, custom weighting, and other
advanced controls beyond `AUTO` or a fixed route-variant set. Via-stops are an
ordered list on the saved plan and are resolved against the same optimized
travel-date package.

### Timetable offerings and search facets

Model each timetable method as its own `TimetableOffering` in the published
manifest. An offering has an independent lifecycle (`PLANNED`, `ACTIVE`, or
`DEPRECATED`), version, feed revisions, service-date coverage, minimum search
facets, and currently supported search facets. This lets one method be
introduced, suspended, or deprecated as its provider API changes without
changing other offerings. The user-facing service category is the offering
identity; “by station”, “by route”, and similar wording describe its minimum
search facets, not separate offering names.

| Timetable offering | Minimum search facets |
| --- | --- |
| Sydney Trains and Metro | Station |
| Sydney regional buses | Route, locality, stop |
| Sydney ferries | Wharf |
| Sydney and Newcastle light rail | Stop |
| NSW TrainLink regional trains | Station |
| NSW TrainLink regional coaches | Route, locality, stop |
| Sydney and regional school buses | Route, locality, stop |

These are minimum product requirements, not a closed list. Each active
offering may publish additional `supportedSearchFacets` when its current feeds
and reviewed mappings provide reliable identifiers and search data. The
client must only expose facets supported by that offering's published
snapshot. A combined Sydney Trains/Metro card may group two provider feeds
under one offering. Bus localities are optional metadata: the four currently
captured dedicated schedule stop files have empty `stop_desc`, so locality
search requires a verified enrichment such as Stop Finder parent metadata.
Station, wharf, and generic stop are distinct semantic facets even when they
resolve to normalized `Stop` records.

The Complete GTFS capture contains candidate route records for regional bus,
coach, ferry, light rail, regional train, and school-bus types. It proves those
mode categories occur in that captured dataset, not that each desired
operator-specific offering has validated current coverage or may be published
to users. Establish those offerings from their chosen authoritative feeds and
review their licenses before activating them. School buses therefore remain a
supported offering in the domain model but not an automatically enabled
product offering.

The timetable landing-page cards and their icons/layout are presentation, not
domain data. The manifest provides offering availability, coverage, and
facets so the app can decide which choices to show or disable.

A compact route card, expandable stop list, timeline/waterfall, date strip, and
alert banner are alternate views of the same route option and package. The
model should supply ordered timed events, transfer/walking/wait totals, platform
details, fare estimate, alert impact, and source freshness once, leaving the UI
free to choose how much detail to reveal. Relative text such as "in 12 minutes"
is derived from the event instant and current time, not stored in the timetable.

Date-strip navigation selects another covered travel date and runs a new query
against that date's package. The saved plan persists across dates and snapshot
refreshes. Public stop IDs used by saved plans must be stable across compatible
snapshot versions; if a stop is removed or remapped, report it as unresolved
and let the user choose a replacement instead of silently routing to another
stop.

Use explicit search outcomes for invalid local time, unavailable date/network,
unresolved saved stop, no matching stops, and no route. A no-route result is a
normal search outcome, not a server error. Route alternatives are capped and
their ranking/diversity rules are part of the future API contract. “Fewest
alerts” may be shown as an advisory or sort criterion only when active alerts
are fresh and their targets resolve to the affected route legs.

Retain stop/trip accessibility values and pathway step-free metadata wherever
the feeds provide them, so the domain can support this later. Do not expose an
accessibility filter in the initial UI or claim a route is accessible when
feed/pathway evidence is incomplete.

### Fares and occupancy

The Opal Fares dataset is a downloadable Excel workbook of distance tables and
fare values plus a business-rules PDF, not a fare-calculation API. Preserve
both as independently versioned inputs. The captured workbook's current fare
values take effect 29 June 2026; the companion rules document is v1.7. Normalize
the workbook into a separate, cacheable `OpalFareDataSnapshot` containing
stop-pair/distance-to-fare-band mappings and fare values. Keep rules revision
separate so a workbook refresh does not require a timetable snapshot refresh.
The captured files are the [fare workbook](../../../docs/provider-docs/Opal%20Fares/open-data-opal-distance-tables-2026-06.xlsx)
and [v1.7 business rules](../../../docs/provider-docs/Opal%20Fares/opal-fares-business-rules-and-information-v1.7.pdf).
The Data Hub lists the dataset under Creative Commons Attribution; retain TfNSW
attribution and the source revision when republishing normalized fare data.

The small `timetable.opal.domain` module contains fare categories, payment
methods, Opal fare modes/periods, an `OpalFareProfile`, v1.7 rule constants,
and `OpalTimetableFareCalculator`. Adult Opal is the default profile; the user
can choose another supported profile, saved in browser storage. Contactless
uses Adult pricing, and single-trip tickets support only Adult or Child/Youth.
Train, Metro, and Intercity Rail share an Opal fare family; Stockton Ferry uses
the Bus fare family. The date/time rule classifies peak/off-peak periods for
Sydney Trains/Metro, Bus, Light Rail, and Intercity services; Ferry uses its
standard fare period. Fridays, weekends, and public holidays receive off-peak
periods for eligible modes.

The Java timetable fare calculator applies the v1.7 intermodal transfer discount (Adult $2.00;
other listed Opal categories $1.00) when mode changes occur within the
applicable transfer window: 60 minutes normally, 130 minutes for the Manly
Ferry case. Train/Metro/Intercity transfers are intra-modal. The normalized
fare-table lookup must provide the price for each continuous same-mode section
using its origin/destination and first tap-on fare period; intra-modal longest
distance treatment belongs in that lookup. Keep Airport Station Access Fees
separate from fare discounts. Amounts are integer Australian cents.

This estimate cannot know the rider's prior taps, eligibility, accumulated
daily/weekly caps, weekly reward state, or transfer history. Return the profile,
fare-data revision, rule revision, and limitations. A date-calculation caller
must provide NSW public-holiday knowledge. Validate the workbook mapping and
hand-calculated station pairs before publishing fare values. The frontend can
cache the separate fare package. The Java calculator currently applies transfer
rules to already-priced same-mode sections; workbook import, stop-pair fare
resolution, and API wiring remain open for the next contract pass. Keep one
authoritative execution path when that contract is set; do not duplicate fare
calculation independently in the frontend and backend. Keep current parking
occupancy in the existing parking domain; transit crowding is a separate
optional signal on transit legs.

Keep transit crowding distinct from car-park occupancy. The captured Sydney
Trains `occupancies.txt` provides trip/stop-sequence occupancy categories on
service-calendar patterns; GTFS-Realtime trip updates can provide predictive
carriage occupancy categories. Normalize the static profile into the dated
package only for matching active trips, and carry live carriage predictions in
the realtime overlay with their retrieval time. These are qualitative crowding
signals, not passenger counts or guaranteed future loads. Do not infer Metro
occupancy coverage from the Sydney Trains extension.

The client package is a rich, versioned public data product, not a thin route
result. Keep it separate from provider DTOs and internal persistence. Include
the normalized stop hierarchy and aliases, route/agency metadata, every
date-valid trip and ordered stop call, platform/boarding details, service
purpose, shapes or shape references where useful, transfer links with their
evidence, and supported accessibility/crowding fields. Preserve useful
provider descriptions and optional fields in normalized form so the UI can
choose what to show. Keep provider IDs and provenance in the package where
needed to resolve realtime data, but do not make raw GTFS tables or provider
DTOs the app contract. Include snapshot version, feed revisions, capture and
freshness metadata, coverage, and explicit unavailable/stale states. Measure
payload size and worker performance; optimize representation only after
preserving the information required for clear results and expandable trip
details.

## Realtime overlay

Model alerts and trip updates separately from scheduled values. An alert has a
provider-scoped ID, header/description, cause/effect, active period(s), and one
or more affected agency/route/trip/stop references. A trip update contains a
provider-namespace-scoped trip descriptor (trip/route IDs, optional start
date/time, and schedule relationship) plus stop updates keyed by stop sequence
and/or provider stop ID, with optional arrival/departure delay or predicted
time. The overlay snapshot owns provider-generated and retrieval timestamps.
`TimetableLeg` keeps scheduled times and realtime values distinct; a missing
update is not a zero delay. Added or replacement trips may have no static trip
to join, so preserve the relationship and provider descriptor instead of
discarding them. The v1 bus trip-update feed is broader than either static
`ReplacementBus` or `PCBC` feed, so resolve its IDs through an explicit
provider-namespace mapping before joining. Record the match result and preserve
unmatched updates; identical ID strings from different feeds are not proof of
identity. Apply the same scoped matching rule to alert targets and stop IDs.

The realtime overlay has its own provider-generated and retrieved timestamps
and a freshness state. A displayed prediction carries the time it was received.
Only call a service "last reported on time" when a fresh update supports that
claim; otherwise show the static scheduled time without implying a live report.
When a prediction exists, keep it visually and contractually distinct from the
scheduled departure/arrival.

Model `RealtimeVehiclePosition` separately from stop-time predictions. Preserve
provider namespace, optional trip and vehicle IDs, coordinates, bearing, speed,
current stop sequence/status, vehicle timestamp, feed timestamp, and retrieval
time where supplied. Match trip IDs only within a verified provider namespace;
keep positions unmatched when no reliable static trip link exists. Vehicle
positions add moving-vehicle locations, while stop names/platforms come from the
static timetable and departure changes come from trip updates. Poll each feed
on a controlled backend cadence; route or stop UI requests must never fan out
into provider calls.

TfNSW publishes a v1 GTFS-Realtime vehicle-position feed for buses, NSW Trains,
and regional bus services, and a v2 feed for Sydney Trains, Metro, and Inner
West Light Rail. The local v2 Swagger documents Sydney Trains and Metro and
supports checking `Last-Modified` with `HEAD` before downloading changed data.
No vehicle-position responses have been captured yet. The broad bus feed is a
candidate for replacement-bus locations, but confirm that replacement and PCBC
trip IDs actually appear and join to the corresponding static feed before
showing those vehicles. Keep schedule feed IDs and realtime feed namespaces
separate until verified.

The captured `ReplacementBus` route `7063_9T2` has a Sunday 1 Nov trip with
calls Leppington Stand A 08:07, Edmondson Park Stand A 08:19, and Glenfield
Railway Pde 08:36 (plus earlier calls on the loop). TripView's example shows
08:06 to 08:36, labeled `9T2`; preserve the static 08:07 schedule and keep any
realtime/predicted departure distinct. The one-minute difference remains
unresolved until a matching v1 bus trip update is captured. This example
confirms that a replacement route can be a loop and its named boarding stop
can be an intermediate call, so routing must evaluate every eligible pair of
stop calls, not only the first and last stops.

The JSON sample at
[`gtfsr-alerts_20261028211837.json`](../../../docs/apis/v2-gtfs/alerts-all/gtfsr-alerts_20261028211837.json)
confirms the GTFS-RT envelope and alert fields: feed timestamp and
`FULL_DATASET`; entity IDs; optional active-period start/end epoch seconds;
informed agency/route/trip/stop/direction references; cause/effect; URL; and
translated header/description text. It contains 370 entities, and active
periods include start-only as well as bounded periods. Route references include
`SMNW`/`SMNW_M1` and `SydneyTrains`/`APS_*`, matching the corresponding
production schedule namespaces. Some stop alerts name multiple agencies for
one stop ID. These are useful join examples, not proof of a global ID contract;
continue validating by feed/operator and test active-period boundaries. Keep
HTML translations distinct from plain text and treat absent period bounds as
unbounded. The captured trip-update feeds now provide complementary join
evidence (summarized below).

For the first release, relevant route/stop/trip alerts should be attached to
affected timetable routes with source and freshness. Polling cadence and aggregate
provider quotas belong to backend operations policy, not this domain model or
the user request.

## Decisions and remaining investigation

- Use the captured production Sydney Trains and Metro **For Realtime** feeds
  for initial routing. Do not merge Complete GTFS records into these datasets.
- Include `ReplacementBus` and `PCBC` as supporting schedule feeds in the first
  release. The reviewed `9T2` and `11T2` service relationships are normalised
  inside the Sydney Trains package; neither is independently offered as a
  regional bus route. Preserve source namespaces and reviewed stop-link
  provenance.
  The captured reference table maps these feed keys to v1 bus trip updates and
  v2 bus alerts. Keep all source namespaces distinct; do not classify every
  bus operator in Complete GTFS as a replacement service.
- Route variants may remain directional; canonical branded-line grouping is
  optional. Preserve provider route names and colors without promising a line
  identity the feed does not establish.
- Use provider-defined TSN as the canonical cross-feed stop/group key where
  present. Map each TSN to one or more feed-scoped StopRefs and add explicit
  parent/interchange mappings where a TSN has no GTFS row. Confirm station,
  platform, and transfer semantics before routing across the mapping.
- Alerts JSON has 370 entities. Observed target shapes include route/direction
  (1,668 references), route/direction/stop (1,275), trip (94), and stop (64).
  Active periods include start-only and bounded ranges. Exact-ID comparison
  against the captured schedules matched 958/1,308 Sydney Trains route targets,
  100/100 NSW Trains route targets, all four Metro route targets, and all
  captured Sydney Trains/Metro stop targets. Only 3/77 Sydney Trains trip
  targets matched `trips.txt` directly, and none of 17 NSW Trains trip targets
  did. Treat route/stop examples as confirmed joins; investigate trip ID
  transformation and the unmatched Sydney Trains routes before broad rollout.
  The unmatched route targets are 22 `BNK_*`/`IWL_*` IDs, absent from the
  captured Sydney Trains schedule; do not attach them to a route unless a
  matching schedule feed is loaded.
  The GTFS-RT spec defines active ranges as start-inclusive and end-exclusive,
  with an omitted bound extending infinitely. No more alert capture is needed
  now; validate target matching and boundary rules in fixtures.
- Captured trip updates are in
  [`docs/apis/v2-gtfs/realtime/`](../../../docs/apis/v2-gtfs/realtime/). Exact
  schedule comparison matched all 31 Metro trip descriptors, route IDs, and
  stop IDs. Sydney Trains matched 519/521 trip IDs and all 521 route IDs and
  3,333 stop updates. The two trip-ID exceptions are an explicit `CANCELED`
  trip on `NTH_1b` and an `ADDED` trip on `CCN_1a`; both route IDs exist in
  the schedule. This supports direct trip/route/stop joins for scheduled trips
  while preserving the realtime relationship for added/canceled service.
- The captured stop times already prove after-midnight values are common:
  127,974 Sydney Trains time fields exceed 24:00 (maximum 31:35:06) and 4,864
  Metro fields exceed 24:00 (maximum 26:23:21). For example, a Saturday T2
  trip departs Central at 23:25:31 and arrives at Leppington at 24:56:00.
  Preserve service-day seconds; inspect DST transitions separately. The
  current Sydney Trains capture's service calendars end on 26 Nov 2026, before
  the next daylight-saving transition, so it cannot alone validate the later
  spring/fall cases.
- Treat the current production captures as the initial data-size baseline.
  The backend owns provider ingestion, calendar/exception resolution, timezone
  conversion, snapshot validation, and compact-index generation. Keep the
  compressed source feeds and last-known-good snapshot on persistent backend
  storage; build and validate the active index before an atomic swap. Publish
  only a compact normalized client package, never raw GTFS tables or provider
  DTOs. The frontend performs interactive stop/time/mode filtering and timetable
  search over that package in a Web Worker, and caches it in IndexedDB.
- Advertise travel-date coverage from Sydney yesterday through today+6
  inclusive (eight dates total), subject to each feed's actual validity, but
  fetch/cache one travel-date package at a time. The date partition must include earlier service-date trips
  whose resolved stop events fall on the selected travel date: captured stop
  times reach 31:35:06 after service midnight, so querying only that calendar
  date's service IDs would miss early-morning arrivals. Report actual covered
  dates in the manifest and return a clear unavailable-date state outside the
  range.
- Expand service calendars and stop times on the backend into dated schedule
  events for the package. Include service date and timezone provenance, and
  publish comparable absolute event instants so the browser does not have to
  reinterpret GTFS service-day seconds. Define the Sydney DST gap/overlap
  behavior once in the API contract; reject nonexistent local times and
  require an explicit offset for the repeated fall-back time.
- For the 1 GB droplet, do not store one Mongo document per stop-time row: the
  captured production schedules contain about 1.65 million stop-time rows
  across Sydney Trains, Metro, ReplacementBus, and PCBC. Published offerings
  remain Sydney Trains and Metro; their initial measured `ALL` packages for
  29 Oct through 5 Nov 2026 ranged from about 0.12 MB to 1.11 MB gzip before
  the reviewed supporting legs were added. The target is at most 2 MB
  compressed per date package. Re-measure the updated packages. Store each immutable
  compressed package as one document in a timetable-specific Mongo collection;
  publish the new manifest pointer only after the full candidate set validates.
  Keep the previous snapshot and bound retention to fit the roughly 300 MB
  available storage. Measure final package size, browser transfer/decode, phone
  memory, worker query latency, and peak backend build heap. If builds exceed
  the droplet's memory, move feed parsing/building to a controlled separate
  process before increasing the droplet size.
- Keep realtime alerts/trip updates as a separate, independently refreshed
  overlay with its own retrieved-at timestamp. Do not bake short-lived realtime
  values into the static schedule snapshot. Saved trips are capped at 20 and
  contain only query inputs in a versioned `localStorage` collection. No
  permission prompt is normally needed. Handle blocked/cleared storage; do not
  store secrets or sensitive data there.
- Make the frontend the single production timetable-route search implementation if it
  owns routing from the package. Avoid maintaining a second backend route
  solver with subtly different transfer and DST behavior; the backend can
  still expose stop search and snapshot/alert data APIs.
- Train routes remain the first priority. School-bus records present in public
  captures can be evaluated later as a mode, with coverage and licensing
  checked before user-facing inclusion.

## Next hand checks / captures

No additional TSN mapping file is needed from the user: the provider
documentation defines TSN as the stable network stop/group number, and the
captured feeds can be matched using TSN-like `stop_id`/`stop_code` values. I
will continue the feed-to-TSN reconciliation from the local captures.

The local reference-table capture was updated on 14 September 2026 to include
Planned Contingency Busing and identifies the dedicated schedule, trip-update,
vehicle-position, and alert endpoints. The API status page lists the
Replacement Bus schedule and bus trip-update endpoints as available at the
time checked;
availability does not prove the response contains trips for a particular
date. The 28 Oct 2026 `ReplacementBus` and `PCBC` schedule ZIPs are now
captured under [`docs/apis/v1-gtfs/schedules/buses/`](../../../docs/apis/v1-gtfs/schedules/buses/).
Capture one v1 `/gtfs/realtime/buses` response to resolve the 08:06 versus
08:07 TripView/static departure and validate trip/stop update joins. The
official [EMS-80106 alert](https://transportnsw.info/alerts/details/ems-80106)
confirms buses replace trains between Leppington and Glenfield Sunday 1 Nov,
08:00 to 14:00. The bus can be offered as a replacement-purpose transit leg
in that service window. Keep alert impact and any explicit substitution
relationship in the realtime/disruption overlay rather than mutating the static
trip. Associate the bus service with the disrupted rail segment only when
alert targets/time ranges or a reviewed mapping establish that relationship.
When displaying the route, keep scheduled and realtime times distinct and
expose ordered stop calls on expansion. Vehicle positions can wait until map
work.

The first proposed hand-check was invalid: I selected a trip whose service
calendar was Saturday-only (14 Nov), despite the calendar row's date range.
After applying the weekday flags, the current capture has Thursday 29 Oct
Central Platform 19 trips at 23:24:30 and 23:54:30 that reach Ashfield at
23:41:30 and 24:11:30. These round to the times reported from TripView. The
GTFS route short name on the matching entries is `T3`. The 28 Oct Swagger
fetch returned the same bundle filename and `Last-Modified` as the earlier
capture (27 Oct 14:01:22 UTC), so the snapshot did not change. The saved
[departure-monitor response](../../../docs/apis/v1-tp/response.json) independently
labels the 23:24 departure T3 and includes `RealtimeTripId` matching the GTFS
trip ID. The GTFS time is 23:24:30 and the API reports 23:24, consistent with
minute-level display rounding. That response covers only through 23:40, so it
does not establish the line or trip for the reported 23:54 departure.

TripView's date limit also makes a far-future DST comparison impractical. My
recommended contract behavior is to reject a nonexistent spring-forward local
time as invalid, and require an explicit Sydney UTC offset only for the rare
repeated fall-back time. That lets the API distinguish the two real instants
without changing ordinary date/time searches. We can verify the behavior with
timezone-rule fixtures when implementing the contract. NSW Government lists
the transition dates at
https://www.nsw.gov.au/about-nsw/daylight-saving.
