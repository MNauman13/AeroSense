# Synthetic data dictionary and shared wire contract

All values, ranges, equipment descriptions, and notes below are fictional software-demo choices. They do not describe an aircraft, a test rig, or engineering limits. Timestamps are RFC 3339 / ISO 8601 instants in UTC, serialized with `Z`. UUIDs use the standard hyphenated form.

## Feature set

Each generated cycle has exactly one summary measurement for each feature. Feature keys are fixed, snake_case, finite JSON numbers; values and units are required and never null. Units are display labels only.

| Feature key | Unit | Arbitrary synthetic normal center and spread |
|---|---:|---:|
| `extension_time_ms` | `ms` | Mean 180, standard deviation 6 |
| `pressure_kpa` | `kPa` | Mean 1000, standard deviation 18 |
| `vibration_rms` | `g_rms` | Mean 0.30, standard deviation 0.035 |
| `temperature_c` | `degC` | Mean 24, standard deviation 2 |
| `cycle_duration_ms` | `ms` | Mean 2800, standard deviation 90 |

These values only make the generated demo varied and legible. They are not limits, recommended operating ranges, or calibrated aircraft data.

## Records

- **Rig**: `id` UUID, unique synthetic `rigCode`, fictional `description`, and `syntheticNotice`.
- **Cycle**: `id` UUID, stable `cycleCode`, `rigId`, `recordedAt` UTC instant, and fictional `cycleType`. Public cycle DTOs do not expose the generator's `syntheticLabel`. `isFlagged` is `null` when no completed analysis exists; otherwise it reflects the latest analysis.
- **Measurement**: `featureName`, finite numeric `value`, `unit`, and `measuredAt`. In this MVP, one summary per feature is measured at the cycle's `recordedAt`; no within-cycle sample series is implied.
- **Feature vector**: `features` object containing all five feature keys above. Inference rejects missing, extra, null, non-numeric, NaN, and infinite values; it does not impute.
- **Ground truth**: `syntheticLabel` is stored on generated cycles for evaluation only. It is accepted only by the isolated evaluation request and must never be sent to `/v1/score` or used as an inference feature.
- **Citation**: stable `sourceId`, `title`, and a verbatim `excerpt` from a clearly fictional local note.

## Analysis semantics

The baseline is `robust-zscore`, version `1`. For each feature in a scoring cohort, calculate its median and median absolute deviation (MAD); the robust z-score is `(value - median) / (1.4826 * MAD)`. If MAD is zero, use a 1e-9 denominator to keep outputs finite. A feature contribution is `min(abs(robustZScore) / 8, 1)`. The cycle score is the maximum contribution across its features, in `[0, 1]`; a higher score means more unusual relative to that synthetic cohort. The default threshold is `0.65`; a cycle is flagged when `score >= threshold`. A caller may configure a threshold in `[0, 1]`.

Explanations list feature name, observed value, cohort median, signed robust z-score, and non-negative contribution, sorted by contribution descending. These are numerical associations used by the scoring method, not causal explanations. Scores and thresholds are not validated engineering limits.

## API conventions

- Public Java API routes use `/api/v1`; Python service routes use `/v1` (health uses `/health`).
- List pagination is zero-based: `page` defaults to 0; `size` defaults to 20 and must be 1–100. Responses have `items`, `page`, `size`, `totalElements`.
- Until a completed analysis is stored, cycle `isFlagged` is `null` and a `flagged` filter returns an empty page. Ground-truth labels never determine that filter.
- Date filters are optional UTC instants; `from` must be less than or equal to `to`. Missing optional filters are omitted, never represented by an empty string.
- JSON required fields are non-null. The only defined nullable response field is cycle `isFlagged` before the first analysis; optional request fields may be omitted. Invalid or missing required feature values are validation errors; there is no silent default or imputation.
- Errors have `code`, safe human-readable `message`, `requestId`, and optional `fieldErrors` (field name to list of messages). No stack trace or credential is returned.
- `POST /api/v1/assistant/questions` limits questions to 500 characters. The deterministic retrieval path cites only retrieved fictional text and returns `insufficientEvidence: true` with an explicit not-found answer when support is absent.
- All assistant responses include the disclaimer: `Synthetic demonstration only. Not engineering or maintenance advice.`

## Contract examples

Full request and response examples are in [API examples](api-examples.md). The same wire names and value semantics are represented by Java records in `backend/`, Pydantic models in `analytics/app/schemas.py`, TypeScript types in `frontend/src/types/api.ts`, and [OpenAPI](openapi.yaml).
