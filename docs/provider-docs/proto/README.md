# TfNSW GTFS-Realtime protobuf schemas

TfNSW GTFS-Realtime endpoints return Protocol Buffers binary data. Realtime
ingestion must decode each response with the matching GTFS-Realtime base
schema and TfNSW extension definitions, then map the decoded messages into our
normalized realtime domain. Do not treat the binary response as JSON or expose
generated protobuf messages as the application API contract.

The schemas captured in this folder are provider-specific additions and
variants:

- [`gtfs-realtime_1007_extension.proto`](gtfs-realtime_1007_extension.proto)
  is the captured Sydney Trains schema variant. It adds GTFS-Realtime fields
  and TfNSW vehicle/carriage metadata, including wheelchair capacity, vehicle
  model, occupancy, and consist details.
- [`tfnsw-gtfs-realtime-carriage.proto`](tfnsw-gtfs-realtime-carriage.proto)
  describes a carriage extension on `VehiclePosition` field 1007.
- [`tfnsw-realtime (002).proto`](tfnsw-realtime%20(002).proto) describes
  the alternate TfNSW schema variant: repeated carriage descriptors on
  `VehiclePosition` field 1006 and a vehicle descriptor extension on field
  1999. The captured Metro response also contains vehicle descriptors.

Before implementing a decoder, confirm which schema version applies to each
specific realtime endpoint and retain the provider's original message version
and feed provenance. These files are not interchangeable: extension field
numbers and message definitions differ between versions. Keep imported base
GTFS-Realtime definitions alongside the exact extension version required by
the selected feed, and generate decoder classes from those pinned sources.

These schemas apply to binary GTFS-Realtime feeds such as alerts, trip updates,
and vehicle positions. Static GTFS schedule ZIPs are CSV tables and are parsed
separately by the timetable schedule importer.

The captured Sydney Trains response contains trip updates, delay and
uncertainty values, stop-level occupancy, vehicle attributes, and carriage
consists. The Metro capture contains trip updates and vehicle positions. The
normalized model preserves these useful optional values without exposing
protobuf message names in the service API. Confirm the exact schema variant
for every endpoint before generating production decoder classes; the captured
schema files are not interchangeable, and the base `gtfs-realtime.proto` must
also be pinned with the selected variant.
