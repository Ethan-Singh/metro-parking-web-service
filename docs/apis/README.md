# API contracts and provider captures

This directory contains the service's public API contract and captured provider
requests/responses. Provider product specifications and technical guides are
indexed in [`../provider-docs/`](../provider-docs/).

| Path | Contents |
| --- | --- |
| [`metro-parking-api-v1.yaml`](metro-parking-api-v1.yaml) | Canonical public API contract for this service |
| [`v1-carpark/`](v1-carpark/) | Car Park API catalog, live, and history response samples |
| [`v1-gtfs/`](v1-gtfs/) | Sydney Trains schedule feed captures |
| [`v1-publictransport/`](v1-publictransport/) | Complete static GTFS feed capture |
| [`v1-tp/`](v1-tp/) | Trip Planner departure-monitor response used for a read-only timetable comparison |
| [`v2-gtfs/`](v2-gtfs/) | Metro schedule, GTFS-Realtime alert, and Sydney Trains/Metro trip-update captures |
| [`Reference Tables for TfNSW GTFS feeds/`](Reference%20Tables%20for%20TfNSW%20GTFS%20feeds/) | Agency and service-reference data used to interpret/reconcile feeds |

The folders group provider captures by API version/resource. Keep provider IDs,
feed names, capture dates, response headers, and request paths with sample
data. Do not assume identifiers in Complete GTFS map directly to realtime
feeds; use the TfNSW reference tables and feed-specific documentation.
