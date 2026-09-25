# API examples (synthetic demonstration only)

These examples define wire names shared by the Java API, Python services, and TypeScript client. Every identifier, reading, score, note, and citation excerpt shown is fictional.

## Seed response

`POST /api/v1/demo-data/seed` loads the generated local JSON fixtures once. Repeated calls return the existing counts without creating duplicate records.

```json
{
  "seeded": true,
  "rigCount": 3,
  "cycleCount": 1000,
  "syntheticNotice": "Synthetic demonstration only. Not engineering or maintenance advice."
}
```

## Rig and cycle

```json
{
  "id": "00000000-0000-4000-8000-000000000001",
  "rigCode": "RIG-SYN-01",
  "description": "Fictional landing-gear demonstration rig; synthetic data only.",
  "syntheticNotice": "Synthetic demonstration rig. Not real aircraft equipment."
}
```

```json
{
  "id": "00000000-0000-4000-8000-000000000101",
  "cycleCode": "CYC-000001",
  "rigId": "00000000-0000-4000-8000-000000000001",
  "recordedAt": "2026-01-01T00:00:00Z",
  "cycleType": "synthetic-extension-check",
  "isFlagged": null,
  "measurements": [
    {"featureName": "extension_time_ms", "value": 181.2, "unit": "ms", "measuredAt": "2026-01-01T00:00:00Z"},
    {"featureName": "pressure_kpa", "value": 1002.4, "unit": "kPa", "measuredAt": "2026-01-01T00:00:00Z"},
    {"featureName": "vibration_rms", "value": 0.29, "unit": "g_rms", "measuredAt": "2026-01-01T00:00:00Z"},
    {"featureName": "temperature_c", "value": 24.3, "unit": "degC", "measuredAt": "2026-01-01T00:00:00Z"},
    {"featureName": "cycle_duration_ms", "value": 2794.0, "unit": "ms", "measuredAt": "2026-01-01T00:00:00Z"}
  ]
}
```

The public cycle response intentionally omits the private evaluation-only `syntheticLabel`.

## Analytics scoring

`POST /v1/score` accepts a cohort of fully populated feature vectors. Labels are not accepted here.

```json
{
  "threshold": 0.65,
  "cycles": [{
    "cycleId": "00000000-0000-4000-8000-000000000101",
    "features": {
      "extension_time_ms": 181.2,
      "pressure_kpa": 1002.4,
      "vibration_rms": 0.29,
      "temperature_c": 24.3,
      "cycle_duration_ms": 2794.0
    }
  }]
}
```

```json
{
  "modelName": "robust-zscore",
  "modelVersion": "1",
  "threshold": 0.65,
  "scoreDirection": "higher_is_more_anomalous",
  "results": [{
    "cycleId": "00000000-0000-4000-8000-000000000101",
    "score": 0.39,
    "isFlagged": false,
    "explanation": {
      "method": "absolute_robust_z",
      "features": [{
        "featureName": "extension_time_ms",
        "observedValue": 181.2,
        "baselineMedian": 180.0,
        "robustZScore": 3.1,
        "contribution": 0.39
      }]
    }
  }]
}
```

Scores, threshold, and contributions are synthetic cohort-relative software output. The values above illustrate the response shape, not a validated limit.

## Evaluation-only labels

`POST /v1/evaluate` takes the same feature vectors plus `syntheticLabel` for offline scoring of generator output. That label is not an inference input.

```json
{
  "threshold": 0.65,
  "cycles": [{
    "cycleId": "00000000-0000-4000-8000-000000000101",
    "features": {
      "extension_time_ms": 181.2,
      "pressure_kpa": 1002.4,
      "vibration_rms": 0.29,
      "temperature_c": 24.3,
      "cycle_duration_ms": 2794.0
    },
    "syntheticLabel": false
  }]
}
```

Evaluation returns `totalCycles`, `truePositives`, `falsePositives`, `trueNegatives`, `falseNegatives`, `precision`, `recall`, and `f1`. Zero-denominator metrics are `0.0`; these numbers describe only the generated synthetic dataset.

## Stored analysis run

`POST /api/v1/analysis-runs` runs the baseline over locally stored synthetic cycles. A rig and UTC time range can narrow the cohort; `threshold` defaults to `0.65`.

```json
{
  "modelName": "robust-zscore",
  "configuration": {"threshold": 0.65}
}
```

The response includes the run ID, status, selected cycle count, model version, and threshold. A successful run stores each result and its explanation. Use `GET /api/v1/analysis-runs/{runId}/results` to page through those results, `GET /api/v1/analysis-runs/{runId}/evaluation` for synthetic-only metrics, or `GET /api/v1/cycles/{cycleId}/analysis/latest` for the latest result on one cycle. Ground-truth labels are sent to Python only for evaluation.

`GET /api/v1/metrics/summary` reports dataset counts and descriptive statistics for the synthetic measurement summaries. These values are not engineering limits or safety advice.

## Filtered downloads

The dashboard's CSV and report links preserve the selected rig, inclusive UTC date range, and latest demo-comparison status. They export all matching runs across every results page. CSV rows contain generated measurements and the saved demo status; they omit the private `syntheticLabel` used only to evaluate the generator.

```text
GET /api/v1/cycles/export?rigId=00000000-0000-4000-8000-000000000001&from=2026-01-01T00:00:00Z&to=2026-01-31T23:59:59Z&flagged=true
GET /api/v1/cycles/report?rigId=00000000-0000-4000-8000-000000000001&from=2026-01-01T00:00:00Z&to=2026-01-31T23:59:59Z&flagged=true
```

The first endpoint returns a UTF-8 CSV attachment with readings and units. The second returns a short UTF-8 text attachment with included-run totals, comparison-status counts, and average/minimum/maximum readings. Both state that the data is synthetic and not advice; the summary is descriptive, not a set of equipment limits.

## Pagination and errors

```json
{"items": [], "page": 0, "size": 20, "totalElements": 0}
```

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed.",
  "requestId": "req-synthetic-example",
  "fieldErrors": {"size": ["must be between 1 and 100"]}
}
```

## Grounded assistant

```json
{
  "question": "What does the fictional review note say about this demo cycle?",
  "cycleId": "00000000-0000-4000-8000-000000000101"
}
```

```json
{
  "answer": "The fictional demo notes say: “For this synthetic example, compare the displayed vibration summary with the other generated cycles and read the score explanation beside the measurement table.”",
  "insufficientEvidence": false,
  "citations": [{
    "sourceId": "demo-note-02#chunk-01",
    "title": "Fictional demonstration review note",
    "excerpt": "For this synthetic example, compare the displayed vibration summary with the other generated cycles and read the score explanation beside the measurement table."
  }],
  "disclaimer": "Synthetic demonstration only. Not engineering or maintenance advice."
}
```

When no passage supports the question, the response uses `insufficientEvidence: true`, an explicit not-found answer, and an empty citations list. The retrieval service never creates note content or engineering advice.

GET /api/v1/metrics/trend?featureName=vibration_rms&limit=200 returns the latest matching synthetic measurements in chronological order. Optional rigId, from, and to filters are shared with the cycle list and summary endpoints. The inclusive from and to values are UTC timestamps.

~~~json
{
  "featureName": "vibration_rms",
  "unit": "g_rms",
  "points": [
    {
      "cycleId": "00000000-0000-4000-8000-000000000101",
      "cycleCode": "CYC-000001",
      "recordedAt": "2026-01-01T00:00:00Z",
      "value": 0.29
    }
  ],
  "disclaimer": "Synthetic measurement values only. Not engineering limits or safety advice."
}
~~~
