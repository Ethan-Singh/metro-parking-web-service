# Timetable domain

This domain owns TfNSW schedule ingestion and normalization, versioned static
snapshots, realtime overlays, and the data package used by local timetable
search. Route search runs in the frontend against a published package; the
backend does not maintain a second journey solver.

| Concern | Model or contract |
| --- | --- |
| Normalized schedule, journey, fare, and realtime concepts | [domain.puml](domain.puml) |
| Captured-feed evidence and domain decisions | [model-spec.md](model-spec.md) |
| Proposed service-owned API contract status | [domain-api.md](domain-api.md) |
| Provider API and capture inventory | [provider-apis.md](provider-apis.md) |
| Provider Swagger and reference documentation | [provider docs](../../../docs/provider-docs/public-transport/) |
| Captured provider responses | [API captures](../../../docs/apis/) |

Provider feed IDs remain scoped by feed and provider namespace. The normalized
domain and future service API must preserve enough validated data and provenance
for the client to explain journeys, realtime changes, and replacement service.
