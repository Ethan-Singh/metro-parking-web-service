# Timetable domain API

## Status

There is no established timetable OpenAPI contract yet. The model in
[domain.puml](domain.puml) describes normalized domain data; it is not a
provider schema or a finalized HTTP contract. Define and review the versioned
OpenAPI document before integrating a feed-backed frontend.

## Service boundary

The backend owns provider credentials, controlled feed refresh, validation,
normalization, and atomic publication of a last-known-good snapshot. The client
downloads the optimized date-specific package and performs stop/time/mode
search locally. Realtime alerts and trip updates are a separately refreshed
overlay; fare data and rules have their own revision lifecycle.

The API contract should define the package manifest and date coverage, stable
client stop identifiers, normalized routes/trips/stop calls, realtime overlay,
fare package, cache/version behavior, Sydney timezone and DST policy, payload
limits, stale/unavailable states, and errors. It should retain useful optional
normalized metadata and provenance so the UI can choose what to display, while
never exposing raw GTFS tables or provider DTOs as the app contract.

Do not settle endpoint paths or response schemas in this note. Those belong in
the reviewed OpenAPI contract once package and UI requirements are agreed.
