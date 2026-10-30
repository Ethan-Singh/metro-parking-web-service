# Timetable domain

This domain owns TfNSW schedule ingestion and normalization, versioned static
snapshots, realtime overlays, and the data package used by local timetable
search. Route search runs in the frontend against a published package; the
backend does not maintain a second journey solver.

| Concern | Model or contract |
| --- | --- |
| Cross-domain summary | [domain.puml](domain.puml) |
| Stops, networks, trips, calendars, and transfers | [schedule-domain.puml](schedule-domain.puml) |
| Saved plans and computed route options | [route-planning.puml](route-planning.puml) |
| Alerts, trip updates, and disruption matching | [realtime-domain.puml](realtime-domain.puml) |
| Opal fare estimates | [opal-fare-domain.puml](opal-fare-domain.puml) |
| Provider evidence and domain decisions | [model-spec.md](model-spec.md) |
| Implemented API and frontend handoff | [domain-api.md](domain-api.md) |
| Provider API and capture inventory | [provider-apis.md](provider-apis.md) |
| Provider Swagger and reference documentation | [provider docs](../../../docs/provider-docs/public-transport/) |
| Captured provider responses | [API captures](../../../docs/apis/) |

Provider feed IDs remain scoped by feed and provider namespace. The normalized
domain and future service API must preserve enough validated data and provenance
for the client to explain journeys, realtime changes, and replacement service.
