# Proposed daily parking metrics API

This contract restores useful 7 AM and 8 AM expected-free estimates and provides per-day occupancy milestones. It is implemented by the backend and included in `docs/apis/metro-parking-api-v1.yaml`. The frontend should consume this endpoint instead of deriving commuter metrics from chart samples.

## Endpoint

```http
GET /api/v1/parking/{slug}/metrics/daily?from=YYYY-MM-DD&to=YYYY-MM-DD
```

`from` and `to` are inclusive Sydney calendar dates. Use the same configured history window as the history endpoint and return the effective clamped `from` and `to`. The metrics endpoint has a stricter maximum of three dates: if the requested range is longer, retain the most recent three dates ending at the effective `to`. Return one object per date in that effective range, ordered by date ascending. Keep dates with no observations and return `null` for unsupported metrics so gaps remain visible.

The frontend should request the most recent three Sydney dates for the facility page and the most recent Sydney date for facility-card Quick view. The metric request is independent of the chart's selected period and resolution, so changing chart controls does not remove or recalculate this card.

## Response shape

```json
{
  "from": "2026-10-25",
  "to": "2026-10-27",
  "timeZone": "Australia/Sydney",
  "days": [
    {
      "date": "2026-10-25",
      "expectedFreeAt7am": 179,
      "expectedFreeAt8am": 136,
      "crossed50AtMs": 1792964400000,
      "crossed90AtMs": null,
      "fullAtMs": null
    },
    {
      "date": "2026-10-26",
      "expectedFreeAt7am": 163,
      "expectedFreeAt8am": 121,
      "crossed50AtMs": 1793051400000,
      "crossed90AtMs": 1793062200000,
      "fullAtMs": 1793065800000
    },
    {
      "date": "2026-10-27",
      "expectedFreeAt7am": null,
      "expectedFreeAt8am": null,
      "crossed50AtMs": null,
      "crossed90AtMs": null,
      "fullAtMs": null
    }
  ]
}
```

Metric fields are always present and may be `null` when the day's observations cannot support the calculation. Milestone times are source event timestamps in Unix epoch milliseconds. `fullAtMs` is the first sample at or above capacity, including exactly 100%; preserve upstream occupancy values above capacity without clamping.

## Calculation rules

- Use each stored sample's source event timestamp converted to Sydney local time and available spaces (`max(0, capacity - occupancy)`).
- For expected free at 7 AM or 8 AM, select that date's closest sample within 20 minutes of the target time, inclusive. If two samples are equally close, select the earlier event. Return `null` when no sample is within that window. These are historical point estimates, not guaranteed forecasts.
- `crossed50AtMs` is the first sample for that date with occupancy rate at or above 50%.
- `crossed90AtMs` is the first sample for that date with occupancy rate at or above 90%.
- `fullAtMs` is the first sample for that date with occupancy rate at or above 100%.
- Occupancy rate is `occupancy / capacity` when capacity is positive. Do not clamp occupancy to capacity; a source value above capacity remains above 100%.
- Milestone fields are `null` if no sample reaches the threshold or the day's samples have no positive capacity.
- Compute each date independently. A later date that does not reach a milestone must return `null`, even if an earlier date did.
- Return null metrics for requested dates with missing observations.

This endpoint keeps the daily commuter card stable while chart history independently uses the selected resolution and period.
