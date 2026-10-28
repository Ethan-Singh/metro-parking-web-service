# Metro Parking Web Service: persistent project context

Read this before making changes. It captures durable domain and architecture
decisions so a new session does not need to reconstruct them from chat.

## Product and system

This repository is the Java backend for the Metro Parking mobile-first
dashboard in the separate `metro-parking-app` repository. The backend collects
NSW Transport data, stores compact parking history in MongoDB, and serves the
frontend's versioned REST API. The service does not render the UI or own its
design system.

| Area | Current design |
| --- | --- |
| Runtime | Java 25, Spring Boot 4, Maven wrapper |
| API | Spring MVC, `/api/v1/parking`, OpenAPI 3 via springdoc |
| Storage | Spring Data MongoDB; one `ParkingDay` document per facility and Sydney date |
| Resilience | Resilience4j circuit breaker/retry for upstream calls; Bucket4j for inbound rate limiting |
| Cache | Caffeine for completed historical API ranges; current-day ranges are not cached |
| External provider | NSW Car Park API; the car park catalog, live facility snapshot, and history are separate provider operations |

## Read these sources first

| Source | Use |
| --- | --- |
| `docs/apis/metro-parking-api-v1.yaml` | Public endpoint paths, request/response schemas, and examples |
| `domain/models/` | PlantUML context, domain, provider, and backfill-flow models |
| `domain/models/implementation-spec.md` | Current architectural decisions and implementation plan |
| `domain/models/timetable-model-spec.md` | Draft normalized timetable and routing model based on captured provider feeds |
| `domain/contracts/` | Focused behavior contracts, especially `daily-parking-metrics-v1.md` |
| `docs/apis/v1-carpark/` | Saved examples of the upstream catalog, live, and history payloads |
| `docs/apis/v1-gtfs/`, `docs/apis/v1-publictransport/`, `docs/apis/v2-gtfs/` | Captured TfNSW schedule and realtime feed responses; retain feed provenance and IDs |
| `docs/provider-docs/` | Provider Swagger and official field semantics, including the Car Park API v2.3 PDF |
| `README.md`, `DEPLOYMENT.md` | Developer setup and deployed configuration |

When code and docs disagree, verify the current implementation and public
contract, then fix stale docs in the same change when they describe the
affected behavior. The original `docs/architecture/rfc-0001-metro_parking_web_service.md`
is background material. Do not treat it as newer than the domain models,
implementation spec, or current API contract.

## Code map

| Package | Responsibility |
| --- | --- |
| `parking.server` | Upstream `RestClient`, provider DTOs/mapping, resilience configuration |
| `parking.client` | Facility catalog, scheduled live/history sync, Mongo documents/repositories, backfill and retention |
| `parking.analytics` | Versioned controllers, query services, response DTOs/mappers, validation, API caching and rate limiting |
| `parking.domain` | Small domain-level value/normalization helpers shared across features |
| `timetable.opal.domain` | Opal fare profiles, versioned fare rules, normalized fare-table types, and timetable fare-estimate rules |

Keep upstream DTOs, Mongo documents, and public response DTOs separate. Do not
return Mongo document shapes or full upstream payloads from the API. Avoid
building generic provider abstractions for future trip-planner or toll support
until those integrations are actively implemented.

## Provider ingestion and scheduling

- The facility catalog is fetched from the provider's `/carpark` catalog
  operation, filtered by `ParkingPolicy`, then published as one immutable
  catalog snapshot containing both ID/name and slug/ID maps. A successful
  refresh makes newly listed facilities available immediately. Slug lookup
  must not scan `ParkingDay` or depend on a history document existing.
- Live snapshots use `/carpark?facility={id}`. Historical backfill uses
  `/carpark/history?facility={id}&eventdate={date}`. These are different
  provider calls with different purposes; the live endpoint should not wait
  for yesterday's history/backfill.
- `ParkingSyncScheduler` owns catalog refresh cadence and dispatches work to
  `ParkingBackfillService`. The service rotates between live facility
  snapshots and backfill. Read the current scheduler/config before reasoning
  about request frequency: local uncommitted scheduler edits are common, and
  profile/environment settings may override repository defaults.
- The provider account limits previously supplied for this project are 60,000
  requests/day and 5 requests/second. Count retries, catalog refreshes, live
  calls, and backfill calls when changing scheduling. Do not infer total
  upstream usage only from scheduler ticks: Resilience4j retries can issue
  additional requests.
- Successful history responses, including empty responses, complete the
  requested facility/date backfill state. Failed requests use `RETRY_WAIT`
  with backoff. A per-document processing lease lets another worker resume
  expired work.

## Data and time invariants

- A `ParkingDay` is identified by `(facilityId, sydneyDate)` and stores
  `sydneyDate` as a date-only `YYYY-MM-DD` value.
- `MessageDate` is an offset-free Sydney local date/time. Interpret it in
  `Australia/Sydney`, convert it to UTC `Instant` for MongoDB, and return
  timestamps to clients as Unix epoch milliseconds. Do not use the upstream
  `time` field; the provider documentation says to use `MessageDate`.
- `eventdate` selects an upstream history request; it does **not** assign a
  returned record to a Mongo date. Persist each sample under the Sydney date
  derived from that record's own `MessageDate`, even if it falls before or
  after the requested `eventdate`.
- Keep at most the newest source sample in each ten-minute Sydney bucket.
  Five-minute persistence was explicitly dropped. A Sydney calendar day may
  contain 138, 144, or 150 ten-minute buckets across daylight-saving changes;
  samples are UTC instants while the day key and bucket boundaries are
  Sydney-based.
- Preserve provider occupancy and capacity values, including occupancy above
  capacity. Presentation may floor available spaces at zero; never clamp the
  stored source occupancy to capacity.
- Keep backfill state on the day document (`PROCESSING`, `RETRY_WAIT`,
  `COMPLETE`) with retry/lease/completion data as implemented. Do not restore
  the removed singleton `ParkingBackfillCursorDocument` or create a queue
  document. Candidate selection can use the short-lived in-memory metadata
  plan; Mongo's atomic day lease remains the cross-instance coordination.
- The service intentionally does not support the former schema. A fresh DB
  is acceptable when the user chooses to reset it; never drop a database as
  an incidental test or implementation step.

## Public API summary

The OpenAPI YAML is authoritative for exact schemas. Current endpoint intent:

| Endpoint | Behavior |
| --- | --- |
| `GET /api/v1/parking` | Latest populated stored sample for each allowed facility |
| `GET /api/v1/parking/{slug}/live` | Latest populated stored sample for one catalog facility |
| `GET /api/v1/parking/{slug}/history?from=&to=&resolution=` | Compact chart series; point tuple is `[timeMs, occupancyRate, available]` |
| `GET /api/v1/parking/{slug}/metrics/daily?from=&to=` | Daily commuter metrics for at most three effective Sydney dates |

- History dates are inclusive Sydney dates; supported resolutions are
  `AUTO`, `TEN_MINUTE`, `ONE_HOUR`, `SIX_HOUR`, `TWELVE_HOUR`, and
  `TWENTY_FOUR_HOUR`. The service bounds history ranges/point counts as
  described by the OpenAPI contract and policy.
- Daily metrics always return a row for each effective date. Unsupported or
  missing observations yield `null`; the detailed calculation rules are in
  `domain/contracts/daily-parking-metrics-v1.md`.
- Ranges including today bypass history caching and use `Cache-Control:
  no-store`; completed historical ranges may be cached. Preserve this split
  when changing query caching.
- The frontend formats epoch milliseconds in `Australia/Sydney`; API event
  timestamps must not be serialized as long timestamp strings.

## Mongo query and performance decisions

- The unique compound index `{ facilityId: 1, sydneyDate: 1 }` supports
  facility/date range reads and the reverse latest-day scan. Latest populated
  days for the facility list use one aggregation, not one query per facility;
  sort the small result by facility name in the application.
- Backfill selection must avoid an `_id` lookup for every facility/date on
  every scheduler slot. Load only the necessary metadata into the brief
  in-memory plan, refresh it periodically/catalog-window changes, and claim
  one candidate atomically on its `ParkingDay`.
- Do not add indexes speculatively. Reproduce the real query with
  `explain("executionStats")`, inspect keys/docs examined and latency, and
  record why an index is needed. The measured baseline from 2026-10-27 was:
  latest aggregation examined 301 keys/docs and grouped 41 facilities in
  about 2 ms; retention scanned 306 documents in about 0 ms. These are a
  point-in-time baseline, not a performance guarantee.
- An observed `/api/v1/parking` curl/Lighthouse server wait was about 1.2 s
  despite the aggregation's ~2 ms explain. If investigating that latency,
  instrument total application request duration and database stages before
  assuming Mongo is responsible; proxy/runtime/hosting overhead may dominate.

## Style, design boundary, and commits

- Follow existing Java naming, package structure, nullability, logging, and
  formatting. Use Spotless instead of hand-adjusting formatter output.
- There is no frontend design system in this repository. Public field names,
  null semantics, timestamp units, and endpoint behavior must follow the
  OpenAPI/domain contracts. Mobile layout, React/MUI components, and chart
  behavior belong in `metro-parking-app`.
- Use conventional commits with a short imperative summary, e.g.
  `perf: reduce parking list query count`, `fix: resolve slugs from catalog`,
  `docs: document Mongo query plan`.
- Do not commit or push unless the user asks. When asked to commit, stage only
  files related to the requested logical change; preserve local edits,
  untracked dumps/reports, and scheduler overrides.

## Validation and operational safety

- Java validation: `./mvnw spotless:apply` and `./mvnw test` (PowerShell:
  `./mvnw.cmd spotless:apply` / `./mvnw.cmd test`) when requested or needed.
- Run `gitleaks detect -v` for secret checks when changing configuration,
  credentials, dependencies, or when the user asks. Never paste detected
  secret values into chat or reports.
- Mongo integration tests require `localhost:27017`, create a unique test DB,
  and drop only that generated test DB. Never point them at production or the
  developer's ordinary local database.
- Runtime configuration comes from `application.yaml`, profile YAML, and
  environment variables. Do not print or inspect local credential files;
  preserve secrets and use placeholders in examples.
- Report checks that ran and call out any test requiring MongoDB or another
  local service.
