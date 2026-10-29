# Car park domain

This domain owns the service's parking facility catalog, occupancy ingestion,
history storage, daily metrics, and public read API.

| Concern | Model or contract |
| --- | --- |
| Normalized domain and storage | [domain.puml](domain.puml) |
| TfNSW provider operations and payload boundary | [provider-api.puml](provider-api.puml) |
| This service's public API shape | [domain-api.puml](domain-api.puml) |
| Scheduled live sync and historical backfill | [backfill-flow.puml](backfill-flow.puml) |
| Implementation and persistence decisions | [implementation-spec.md](implementation-spec.md) |
| Daily commuter metrics calculation contract | [daily-metrics-contract.md](daily-metrics-contract.md) |
| Exact public paths, schemas, errors, and examples | [metro-parking-api-v1.yaml](../../../docs/apis/metro-parking-api-v1.yaml) |
| Provider Swagger and field documentation | [car-park-api](../../../docs/provider-docs/car-park-api/) |
| Captured provider responses | [v1-carpark](../../../docs/apis/v1-carpark/) |

The provider model and public API model are separate on purpose: provider
records are mapped into the parking domain before persistence or API response
mapping. The OpenAPI document is authoritative for the client contract.
