# Domain model index

The service has two first-class domains. Each domain keeps its normalized model,
provider integrations, and service-owned API contract together while linking to
the authoritative captures and OpenAPI documents under `docs/`.

## Domains

- [Car park](carpark/README.md): implemented parking catalog, live occupancy,
  history, daily metrics, and backfill.
- [Timetable](timetable/README.md): schedule ingestion, normalized snapshots,
  realtime overlays, and client-side route search; its domain and service API
  are being designed.

## Shared and future models

- [System context](system-context.puml): service-level integrations and
  storage boundaries.
- [Future toll capability](future/toll-domain.puml): deferred road-route
  integration, separate from the two active domains.

Provider API schemas and captured responses live in `docs/provider-docs/` and
`docs/apis/`. These model folders link to them; they do not copy provider DTOs
into domain contracts.
