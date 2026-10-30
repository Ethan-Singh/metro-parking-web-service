# TfNSW GTFS-Realtime protobuf schemas

TfNSW GTFS-Realtime endpoints return Protocol Buffers binary data. The service
decodes each response with the matching GTFS-Realtime base schema and TfNSW
extension definitions, then maps those messages into the normalized realtime
domain. Do not expose generated protobuf messages as the application API
contract.

## Pinned schema sources

- [`gtfs-realtime.proto`](gtfs-realtime.proto) is the official GTFS-Realtime
  base schema from `google/transit` commit
  `3c9e7b904b5035349622f03e11851e25c16d1d99` (Apache-2.0).
- Sydney Trains trip-update and vehicle-position feeds use the captured
  [`gtfs-realtime_1007_extension.proto`](gtfs-realtime_1007_extension.proto)
  schema. Its decoder copy is
  [`src/main/proto/sydney/gtfs-realtime-sydney.proto`](../../../src/main/proto/sydney/gtfs-realtime-sydney.proto).
- Metro captures are inspected for extension wire numbers. The supplied
  [`tfnsw-realtime (002).proto`](tfnsw-realtime%20(002).proto) describes the
  1006/1999 variant, whose decoder copy is
  [`src/main/proto/metro/tfnsw-realtime-metro.proto`](../../../src/main/proto/metro/tfnsw-realtime-metro.proto).
  The captured Metro vehicle-position payload instead contains extension
  field 1007; this is decoded with the compatible pinned schema copy at
  [`src/main/proto/sydney/gtfs-realtime-sydney.proto`](../../../src/main/proto/sydney/gtfs-realtime-sydney.proto).

The decoder copies isolate Java/protobuf package names to avoid collisions;
their message fields and wire numbers are kept aligned with the source files.
The base GTFS schema is compiled separately from the self-contained Sydney
schema so their duplicate standard message names do not collide. Maven pins
`protoc` and the Java protobuf runtime to 3.25.9 and generates decoder classes
during compilation.

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
  1999. The current Metro vehicle-position capture instead uses field 1007
  for these extensions; the service selects a decoder based on observed wire
  fields.

Sydney Trains uses extension field 1007. Metro's decoder detects the observed
extension fields and selects the matching 1007 or 1006/1999 variant. Each
normalized source retains its selected schema key and provider feed version.

These schemas apply to binary GTFS-Realtime feeds such as alerts, trip updates,
and vehicle positions. Static GTFS schedule ZIPs are CSV tables and are parsed
separately by the timetable schedule importer.

The captured Sydney Trains response contains trip updates, delay and
uncertainty values, stop-level occupancy, vehicle attributes, and carriage
consists. Metro captures contain trip updates and vehicle positions. The
normalized model preserves useful optional values without exposing protobuf
message names in the service API. Captured binaries live under
[`docs/apis/v2-gtfs/realtime`](../../apis/v2-gtfs/realtime/) and
[`docs/apis/v2-gtfs/vehiclepos`](../../apis/v2-gtfs/vehiclepos/).
