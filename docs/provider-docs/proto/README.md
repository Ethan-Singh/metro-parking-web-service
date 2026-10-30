# TfNSW GTFS-Realtime protobuf schemas

TfNSW GTFS-Realtime endpoints return Protocol Buffers binary data. Realtime
ingestion must decode each response with the matching GTFS-Realtime base
schema and TfNSW extension definitions, then map the decoded messages into our
normalized realtime domain. Do not treat the binary response as JSON or expose
generated protobuf messages as the application API contract.

The schemas captured in this folder are provider-specific additions and
variants:

- [`gtfs-realtime_1007_extension.proto`](gtfs-realtime_1007_extension.proto)
  includes TfNSW extensions for service updates, vehicle descriptors,
  carriage/occupancy information, and alert fields.
- [`tfnsw-gtfs-realtime-carriage.proto`](tfnsw-gtfs-realtime-carriage.proto)
  describes a carriage extension for train vehicle positions.
- [`tfnsw-realtime (002).proto`](tfnsw-realtime%20(002).proto) describes
  TfNSW vehicle and carriage extensions used by the captured Metro and light
  rail schema material.

Before implementing a decoder, confirm which schema version applies to each
specific realtime endpoint and retain the provider's original message version
and feed provenance. These files are not interchangeable: extension field
numbers and message definitions differ between versions. Keep imported base
GTFS-Realtime definitions alongside the exact extension version required by
the selected feed, and generate decoder classes from those pinned sources.

These schemas apply to binary GTFS-Realtime feeds such as alerts, trip updates,
and vehicle positions. Static GTFS schedule ZIPs are CSV tables and are parsed
separately by the timetable schedule importer.
