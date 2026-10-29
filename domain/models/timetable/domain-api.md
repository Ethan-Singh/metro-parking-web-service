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

Represent each service method as an independently versioned timetable
offering in the manifest. Publish its lifecycle, feed revisions, date coverage,
minimum search facets, and currently supported facets separately, so a provider
change can affect one offering without implicitly changing the others. The
minimum facets are listed in [model-spec.md](model-spec.md); supported facets
may grow when provider feeds and reviewed mappings supply reliable data.

Saved plans are client-owned query presets, capped at 20; they are not part of
the timetable API payload. Store the versioned collection in browser
`localStorage`: it normally requires no permission prompt and is not sent with
HTTP requests automatically. Handle storage being blocked or cleared. Plans
are readable by same-origin JavaScript, so keep them free of secrets and
sensitive data. A cookie would not improve that protection if the UI must read
it; `SameSite=Strict` does not block same-origin script access, and matching
cookies are sent with requests. `localStorage` is preferred here to avoid
attaching these presets to HTTP requests, not as an XSS defense.

Include optional stop locality data as a typed `LOCALITY` or `SUBURB`, with its
source/provenance. Do not assume every timetable stop has a suburb: the captured
GTFS `stop_desc` fields are empty, while the Trip Planner Stop Finder schema
supports locality/suburb parent locations. The client should display the
locality only when the published package contains a verified mapping.

Do not settle endpoint paths or response schemas in this note. Those belong in
the reviewed OpenAPI contract once package and UI requirements are agreed.
Saved plans, user naming, and locally computed route options belong to the
client unless account-based synchronization is deliberately added later.
