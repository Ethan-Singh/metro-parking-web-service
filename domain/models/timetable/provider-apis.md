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
| Sydney Trains / Metro alerts | [Alerts v2 Swagger](../../../docs/provider-docs/public-transport/realtime/alerts-v2/v2-gtfs-alerts-swaggerprod_1.yaml) | [`docs/apis/v2-gtfs/alerts-all`](../../../docs/apis/v2-gtfs/alerts-all/) |
| Sydney Trains / Metro trip updates | [Trip Updates v2 Swagger](../../../docs/provider-docs/public-transport/realtime/trip-updates-v2/getrealtime_v2_03.yml) | [`docs/apis/v2-gtfs/realtime`](../../../docs/apis/v2-gtfs/realtime/) |
| Bus trip updates | [Realtime Trip Update dataset](https://opendata.transport.nsw.gov.au/data/dataset/public-transport-realtime-trip-update); `GET /v1/gtfs/realtime/buses` | Capture pending; compare provider namespace IDs with ReplacementBus and PCBC before joining. |
| Opal fare data and rules | [Fare files](../../../docs/provider-docs/Opal%20Fares/) | Workbook and rules PDF are captured. |
| Transport Routes | [TfNSW dataset](https://opendata.transport.nsw.gov.au/data/dataset/transport-routes) | Candidate route/operator metadata; inspect its response and ID mapping before using it in the public model. |
| Complete GTFS | [TfNSW dataset](https://opendata.transport.nsw.gov.au/data/dataset/timetables-complete-gtfs) | Coverage research only; do not merge its records into the initial Realtime schedule namespaces. |

The legacy Complete GTFS capture is for coverage research only. It must not be
merged into the Realtime-compatible Sydney Trains or Metro schedules.
