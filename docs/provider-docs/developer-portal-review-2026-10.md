# TfNSW developer portal review - 30 October 2026

Reviewed the current User Guide, Documentation, Resources, API Basics, and
Troubleshooting pages. These are provider guidance; the captured production
responses and current service contracts remain the evidence for implementation
details.

## Findings relevant to this service

- **Authentication:** The User Guide and API Basics specify an API key in the
  `Authorization` header as `apikey <token>`. Keep it on the backend and out of
  URLs, frontend code, and logs. The portal now describes creating API tokens
  through the signed-in profile.
- **API transport and limits:** API Basics describes HTTPS/HTTP endpoints and
  response content types that vary by endpoint. Successful responses may be
  JSON or binary, so clients must follow the endpoint's media type. Its stated
  Bronze limits remain 60,000 requests per day and five requests per second.
  The documented API contract is HTTP, not gRPC.
- **Static and realtime feeds:** Documentation distinguishes Complete GTFS
  from the `Timetables - For Realtime` bundle. The latter is the static source
  intended to join with realtime feeds for supported operators. The docs list
  Trip Update, Vehicle Position, and Alerts as separate realtime APIs and
  identify Replacement Vehicles and changed stopping patterns in trip updates.
  Retain our separate feed namespaces and validate joins instead of mixing
  Complete GTFS records into the initial rail snapshot.
- **Update cadence:** Troubleshooting says static timetables update daily
  overnight and realtime data updates every 10-15 seconds, recommending a
  15-second polling interval. Our current one-minute poll is a deliberate
  quota-conscious choice: six feeds x 1 request/minute = 8,640 requests/day
  before retries. It trades freshness for lower call volume and should be
  revisited against measured quota and user needs.
- **Realtime fallback:** Troubleshooting explicitly recommends displaying
  scheduled information when realtime data is inaccurate or unavailable and
  clearly distinguishing realtime from scheduled values. This matches the
  timetable package plus independent overlay design.
- **Schema guidance:** The current Documentation page identifies the TfNSW
  1007 extension and separate NLR/Metro and CBD/South East Light Rail proto
  files. The local proto edit to `gtfs-realtime_1007_extension.proto` only
  removed leading whitespace; no wire field or message change was found. The
  captured Metro trip-update feed uses the existing 1006/1999 decoder variant,
  while the captured Metro vehicle-position feed uses 1007; keep both variants
  pinned and selected from observed wire fields.
- **Other possible validation source:** Documentation lists TfNSW's Stop
  Finder, Trip Planner, Departure, Service Alert, and Coordinate Request APIs.
  They may provide comparison examples, but do not replace our snapshot-backed
  client routing design or make a journey request call upstream.

## Logos, colours, and pictograms

The Resources page links the **Transport Mode Symbols and Pictograms** dataset.
It includes mode symbols, pictograms, operator marks, Opal imagery, parking
symbols, and other assets in ZIP, PDF, and image formats. The portal labels
the dataset Creative Commons Non-Commercial (Any) and requires sign-in to
download its files, so no asset binaries were copied into this repository.

TfNSW's usage guidance says not to alter brand elements, to use the Transport
logo for data attribution only, and not to imply endorsement. Mode symbols
should identify only stops/stations/wharves owned and operated by Transport;
pictograms should be small and secondary. Confirm the dataset licence is
compatible with the intended product use before distributing those assets.

## Official pages

- [User Guide](https://opendata.transport.nsw.gov.au/developers/userguide)
- [Documentation](https://opendata.transport.nsw.gov.au/developers/documentation)
- [Resources](https://opendata.transport.nsw.gov.au/developers/resources)
- [API Basics](https://opendata.transport.nsw.gov.au/developers/api-basics)
- [Troubleshooting](https://opendata.transport.nsw.gov.au/developers/troubleshooting)
- [Transport Mode Symbols and Pictograms dataset](https://opendata.transport.nsw.gov.au/dataset/transport-mode-symbols-and-pictograms)
