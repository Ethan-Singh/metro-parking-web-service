# Timetable provider APIs and captures

This is an index to provider-owned schemas and captured responses. Schedule
ZIPs and realtime messages are separate source namespaces; preserve that
provenance through normalization.

| Provider data | Documentation / operation | Local capture |
| --- | --- | --- |
| Sydney Trains schedule | [For Realtime schedule API](https://opendata.transport.nsw.gov.au/dataset/public-transport-timetables-realtime); `GET /v1/gtfs/schedule/sydneytrains` | [`docs/apis/v1-gtfs/schedules`](../../../docs/apis/v1-gtfs/schedules/) |
| Sydney Metro schedule | [For Realtime v2 Swagger](../../../docs/provider-docs/public-transport/timetables/realtime-v2/getschedule-v2_1.0.json); `GET /v2/gtfs/schedule/metro` | [`docs/apis/v2-gtfs/schedules/metro`](../../../docs/apis/v2-gtfs/schedules/metro/) |
| ReplacementBus schedule | [TfNSW endpoint notice](https://opendataforum.transport.nsw.gov.au/t/new-endpoint-for-replacement-bus-gtfs-schedules/2441); `GET /v1/gtfs/schedule/buses/ReplacementBus` | [`docs/apis/v1-gtfs/schedules/buses/replacementbus`](../../../docs/apis/v1-gtfs/schedules/buses/replacementbus/) |
| Planned Contingency Bus (PCBC) | [TfNSW endpoint notice](https://opendataforum.transport.nsw.gov.au/t/new-schedule-endpoint-for-planned-contingency-bus-services/10471); `GET /v1/gtfs/schedule/buses/PCBC` | [`docs/apis/v1-gtfs/schedules/buses/pcbc`](../../../docs/apis/v1-gtfs/schedules/buses/pcbc/) |
| Sydney Trains / Metro alerts | [Alerts v2 Swagger](../../../docs/provider-docs/public-transport/realtime/alerts-v2/v2-gtfs-alerts-swaggerprod_1.yaml); `/v2/gtfs/alerts/sydneytrains?format=json` and `/v2/gtfs/alerts/metro?format=json` | [`docs/apis/v2-gtfs/alerts-all`](../../../docs/apis/v2-gtfs/alerts-all/) |
| Sydney Trains / Metro trip updates | [Trip Updates v2 Swagger](../../../docs/provider-docs/public-transport/realtime/trip-updates-v2/getrealtime_v2_03.yml); `GET /v2/gtfs/realtime/{sydneytrains,metro}` | [`docs/apis/v2-gtfs/realtime`](../../../docs/apis/v2-gtfs/realtime/) contains binary captures and debug text. |
| Sydney Trains / Metro vehicle positions | [Vehicle Positions v2 Swagger](../../../docs/provider-docs/public-transport/realtime/vehicle-positions-v2/getvehicleposition_v2_03.yaml); `GET /v2/gtfs/vehiclepos/{sydneytrains,metro}` | [`docs/apis/v2-gtfs/vehiclepos`](../../../docs/apis/v2-gtfs/vehiclepos/) contains binary captures; polling uses GET once per configured round. |
| Stop Finder and locality metadata | [Trip Planner Swagger](../../../docs/provider-docs/public-transport/trip-planner/tripplanner_v1_swag_efa11_20251002.yml); `GET /stop_finder` accepts a stop/global ID with `type_sf=stop` | The saved [Trip Planner response](../../../docs/apis/v1-tp/response.json) has Central's parent locality (`Sydney`); it is not a standalone Stop Finder response. Capture one Stop Finder response before treating its stop-ID lookup and parent hierarchy as validated for timetable enrichment. This API is metadata/search support only, not the journey-routing engine. |
| Bus trip updates | [Realtime Trip Update dataset](https://opendata.transport.nsw.gov.au/data/dataset/public-transport-realtime-trip-update); `GET /v1/gtfs/realtime/buses` | Capture pending; compare provider namespace IDs with ReplacementBus and PCBC before joining. |
| Opal fare data and rules | [Fare files](../../../docs/provider-docs/Opal%20Fares/) | Workbook and rules PDF are captured. |
| Transport Routes | [TfNSW dataset](https://opendata.transport.nsw.gov.au/data/dataset/transport-routes) | Candidate route/operator metadata; inspect its response and ID mapping before using it in the public model. |
| Complete GTFS | [TfNSW dataset](https://opendata.transport.nsw.gov.au/data/dataset/timetables-complete-gtfs) | Coverage research only; do not merge its records into the initial Realtime schedule namespaces. |

The legacy Complete GTFS capture is for coverage research only. It must not be
merged into the Realtime-compatible Sydney Trains or Metro schedules.

## Supporting schedule use in Sydney Trains packages

The snapshot builder fetches the dedicated ReplacementBus and PCBC endpoints
as supporting source feeds. They do not create manifest offerings. Only these
reviewed routes are admitted to the Sydney Trains package:

| Supporting feed route | Purpose / reviewed segment | Window (Sydney time) | Reviewed stop links |
| --- | --- | --- | --- |
| ReplacementBus `7063_9T2` (`9T2`) | `REPLACEMENT`, Leppington–Glenfield | 08:00–14:00 | Leppington, Edmondson Park, Glenfield bus stops to Sydney Trains station IDs |
| PCBC `700015_11T2` (`11T2`) | `PLANNED_CONTINGENCY`, Olympic Park–Ashfield | 02:00–10:00 | Olympic Park, Flemington, Homebush, Strathfield, Burwood, Croydon, Ashfield bus stops to Sydney Trains station IDs |

The builder limits each supporting source to these route IDs and retains only
trips with a call in the corresponding reviewed window. Routes, trips, and
stops retain their source feed namespace. Each trip includes `servicePurpose`
and `serviceContext`, which identifies the affected rail segment, station
endpoints, evidence URL, and window. Reviewed stop mappings are emitted as
interchange links with provenance. Neither source appears as a standalone
user-facing offering, and no general bus schedule is inferred from these
feeds.

The captured ReplacementBus schedule confirms the 1 Nov 2026 `9T2` run from
Leppington Station Stand A at 08:07 to Glenfield Station Railway Pde at 08:36,
via Edmondson Park. This is schedule evidence; an alert's active-period
association remains a separate realtime concern.

## Search behavior evidence and limits

The Trip Planner Swagger defines `stop_finder` with `type_sf=any` for general
location search and `type_sf=stop` plus a stop/global ID for stop lookup. Its
location schema supports `stop`, `platform`, `locality`, and `suburb` results,
long and disassembled names, parent locations, and transport-mode metadata.
That is provider-schema evidence for stop/locality distinctions and mode-aware
search. The saved Central departure-monitor response confirms the parent
locality `Sydney` for one station; it does not validate suburb text search or
every stop's hierarchy. A dedicated Stop Finder response is still needed to
confirm those runtime cases before depending on them for enrichment.

GTFS `routes.txt` and `trips.txt`/`stop_times.txt` provide route names and
route-to-stop service relationships. The 28 Oct Complete GTFS capture contains
route types `4`, `106`, `204`, `205`, `401`, `700`, `712`, `714`, and `900`.
Route descriptions identify `700` as Sydney buses, `712` as school buses,
`714` as temporary buses, `900` as light rail, `106` as regional trains, and
`205` as temporary coaches. Type `204` is described as the Regional Trains and
Coaches Network and includes route records; do not treat that raw value alone
as a precise mode classification. This supports route filtering when records
are interpreted with their feed, agency, and route metadata, but Complete GTFS
remains research-only and does not confirm current authoritative coverage,
operator-specific service boundaries, locality availability, or licensing for
each future offering. The current dedicated For-Realtime captures confirm
Sydney Trains, Metro, ReplacementBus, and PCBC structures only. Ferry, regional
train/coach, general regional bus, light rail, and school-bus offerings need
their selected authoritative schedule captures and validation before
activation.

Vehicle-position API status currently lists the bus and NSW Trains feeds as
available, while the v2 Swagger specifies Sydney Trains, Metro, and Inner West
Light Rail. These are promising sources for live map positions, not proof that
every replacement-bus trip has a matching position entity. Keep stop locations
from the static timetable snapshot; realtime vehicle feeds update vehicles,
not the underlying stop catalogue. A stop departures board should use trip
updates/departure data rather than polling vehicle positions as if they were
stop updates. Validate each feed's update cadence and call quota before setting
backend polling intervals. Never let frontend requests cause provider polls.

The initial realtime scheduler polls six Sydney Trains and Metro sources once
per configured round (one minute by default): two alert operations with
`format=json`, two trip-update operations, and two vehicle-position operations.
This is 8,640 provider GETs per day before retries. Alerts use the documented
GTFS-Realtime JSON mapping and are decoded with Jackson. Binary feeds are
decoded with the endpoint-pinned schemas. Their records preserve feed keys and
are joined conservatively to the active published snapshot. Only a unique
feed-scoped trip ID with agreeing supplied route and service date is marked
`MATCHED`; ambiguous and unmatched entities remain visible. Stop calls join
only by exact provider stop ID and sequence within a matched trip.

The Complete GTFS capture dated 30 Oct 2026 remains a comparison fixture, not
an input to these namespaces. It contains a Metro realtime trip ID such as
`0275-001-117-012:1000`, but names its route `3-M1-sj2-1`; the realtime record
names that route `SMNW_M1`. The captured Sydney Trains realtime ID
`9--J.1324.166.64.B.8.92001629` is not present in that Complete GTFS capture.
These observations reinforce the feed-scoped join rule: do not bridge the
route mismatch or merge Complete GTFS records into the For-Realtime snapshots.
