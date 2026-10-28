# Metro Parking Web Service

![Java 25](https://img.shields.io/badge/Java_25-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot 4](https://img.shields.io/badge/Spring_Boot_4-6DB33F?style=flat&logo=springboot&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-47A248?style=flat&logo=mongodb&logoColor=white)
![Resilience4j](https://img.shields.io/badge/Resilience4j-000000?style=flat&logo=java&logoColor=white)
![Bucket4j](https://img.shields.io/badge/Bucket4j-0052CC?style=flat&logo=java&logoColor=white)
![JUnit 5](https://img.shields.io/badge/JUnit_5-C21325?style=flat&logo=junit5&logoColor=white)
![OpenAPI 3](https://img.shields.io/badge/OpenAPI_3-6BA539?style=flat&logo=swagger&logoColor=white)

A Spring Boot service that collects, stores, and serves current and historical parking occupancy from the NSW Car Park API. The provider's daily `/history` endpoint supplies both recent samples for live charts and historical data.

> **Frontend:** [metro-parking-app](https://github.com/Ethan-Singh/metro-parking-app) — React and TypeScript dashboard consuming this service.

## Architecture models

The PlantUML sources are maintained in [`domain/models`](domain/models/README.md). Use the model index for rendering instructions and model scope.

| Model | Description |
| --- | --- |
| [System context](domain/models/system-context.puml) | Service boundaries and external systems |
| [Parking domain](domain/models/parking-domain.puml) | Parking data, storage structure, and API concepts |
| [Backfill flow](domain/models/backfill-flow.puml) | Scheduled history retrieval and backfill lifecycle |
| [Car Park API](domain/models/carpark-api.puml) | Upstream provider integration |
| [Timetable routing domain](domain/models/timetable-domain.puml) | Draft normalized schedule and journey model from captured TfNSW feeds |
| [Timetable model spec](domain/models/timetable-model-spec.md) | Captured feed observations, proposed concepts, and open modeling questions |
| [Toll domain](domain/models/toll-domain.puml) | Separate road toll calculation model |
| [Implementation spec](domain/models/implementation-spec.md) | Current implementation decisions |

## Data collection and storage

1. Every six hours, the scheduler refreshes the facility-name lookup from the provider's `/carpark` endpoint and applies configured facility exclusions.
2. A single scheduled worker makes at most one provider request per slot. It requests `/history?facility=...&eventdate=...` for either today's rotating facility or the newest eligible historical date. Four of every five history slots target today; the fifth targets backfill.
3. Provider `MessageDate` values are interpreted in `Australia/Sydney`, converted to UTC, and stored as `Instant` values.
4. MongoDB groups samples by facility and Sydney calendar date. Each `ParkingDay` stores at most one newest sample per ten-minute bucket (up to 150 samples) and its backfill state.
5. History responses use epoch milliseconds and compact tuples: `[timeMs, occupancyRate, available]`.

The `parkingDay` collection uses a fresh schema. A unique index covers `(facilityId, sydneyDate)`. Backfill state lives on the daily document (`PROCESSING`, `RETRY_WAIT`, or `COMPLETE`), including successful responses with no samples.

### Provider request budget

The worker uses a 2.2-second fixed delay and makes at most one request per slot. A single running instance can make at most about 39,273 calls per day, below the stated Bronze limits of 60,000 per day and 5 per second. Failed history requests are retried in later slots. Facility lookup refreshes also use a worker slot.

## REST API

Base path: `/api/v1/parking`.

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/` | Latest stored sample for each allowed facility |
| `GET` | `/{slug}/live` | Latest stored sample for one facility |
| `GET` | `/{slug}/history` | Compact history for a date range |
| `GET` | `/{slug}/metrics/daily` | Daily commuter metrics for up to three dates |

History requests require `from` and `to` in `YYYY-MM-DD` format. Optional `resolution` accepts `AUTO`, `TEN_MINUTE`, `ONE_HOUR`, `SIX_HOUR`, `TWELVE_HOUR`, or `TWENTY_FOUR_HOUR`. `AUTO` selects a resolution based on the range. Requests are clamped to the configured inclusive Sydney-date window and capped at 31 dates and 200 points.

Requests that include today bypass the history cache and return `Cache-Control: no-store`. Completed historical ranges can be cached for one hour. The frontend should format epoch milliseconds in `Australia/Sydney` for local display.

Daily metrics accept inclusive `from` and `to` Sydney dates, return one result for each effective date including missing-data dates, and limit the range to the most recent three dates. They provide expected available spaces near 7 am and 8 am plus daily 50%, 90%, and full-capacity milestone timestamps. A day that lacks a qualifying sample returns `null` for that metric. The endpoint shares the daily collection's `(facilityId, sydneyDate)` index and reads at most three day documents.

Example response:

```json
{
  "from": "2026-10-26",
  "to": "2026-10-26",
  "resolution": "TEN_MINUTE",
  "points": [[1792964400000, 0.42, 58]]
}
```

The full contract is available as [OpenAPI YAML](docs/apis/metro-parking-api-v1.yaml). Daily metric calculations are specified in [daily-parking-metrics-v1.md](domain/contracts/daily-parking-metrics-v1.md). Swagger UI and the generated JSON endpoint are described below.

## Configuration

Set `MONGODB_URI`, `PARKING_BASE_URL`, `PARKING_API`, and `PARKING_APIKEY`. `ALLOWED_ORIGINS` controls browser CORS. Policy defaults are in [`application.yaml`](src/main/resources/application.yaml):

```yaml
external-server:
  parking:
    policy:
      disabled-facilities: [1, 2, 3, 4, 5]
      backfill-window: 31 # inclusive Sydney calendar dates
```

The per-IP token bucket is configured under `external-server.parking.security.rate-limit`. The scheduler is suppressed in the `test` profile.

## Run locally

```bash
./mvnw spring-boot:run
```

MongoDB must be reachable at the configured URI.

### Swagger UI

Enable the API spec and Swagger UI when starting locally:

```powershell
$env:API_DOCS = "true"
$env:SWAGGER_UI = "true"
./mvnw spring-boot:run
```

Open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html). The generated JSON spec is at [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) when enabled.

## Tests

```bash
./mvnw test
```

The MongoDB integration test connects to `localhost:27017`, writes to a unique temporary database, and drops only that database after the test. Other test-profile contexts use `metro-parking-test`.
