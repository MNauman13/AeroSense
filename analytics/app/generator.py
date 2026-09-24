"""Deterministic generator for clearly fictional AeroSense demonstration data."""

from __future__ import annotations

import json
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

DEFAULT_SEED = 20260101
DEFAULT_START = "2026-01-01T00:00:00Z"
DEFAULT_END = "2026-02-01T00:00:00Z"
SYNTHETIC_NOTICE = "Synthetic demonstration rig. Not real aircraft equipment."
RIG_DESCRIPTION = "Fictional landing-gear demonstration rig; synthetic data only."
SYNTHETIC_CYCLE_TYPE = "synthetic-extension-check"
_NAMESPACE = uuid.UUID("a74d70e9-ae46-48f4-a12a-f8766bb6b23b")

FEATURES: dict[str, tuple[float, float, str]] = {
    "extension_time_ms": (180.0, 6.0, "ms"),
    "pressure_kpa": (1000.0, 18.0, "kPa"),
    "vibration_rms": (0.30, 0.035, "g_rms"),
    "temperature_c": (24.0, 2.0, "degC"),
    "cycle_duration_ms": (2800.0, 90.0, "ms"),
}
FEATURE_NAMES = tuple(FEATURES)
RIG_COLUMNS = ("id", "rigCode", "description", "syntheticNotice")
CYCLE_COLUMNS = (
    "id",
    "cycleCode",
    "rigId",
    "recordedAt",
    "cycleType",
    "syntheticLabel",
    *FEATURE_NAMES,
)


@dataclass(frozen=True)
class SyntheticDataset:
    """Tabular generated rigs and cycles, with labels kept separate from features."""

    rigs: pd.DataFrame
    cycles: pd.DataFrame

    def cycle_records(self) -> list[dict[str, Any]]:
        """Return JSON-ready cycles; ground truth is separate from the feature vector."""
        records: list[dict[str, Any]] = []
        for row in self.cycles.to_dict(orient="records"):
            features = {name: float(row[name]) for name in FEATURE_NAMES}
            records.append(
                {
                    "id": row["id"],
                    "cycleCode": row["cycleCode"],
                    "rigId": row["rigId"],
                    "recordedAt": row["recordedAt"],
                    "cycleType": row["cycleType"],
                    "syntheticLabel": bool(row["syntheticLabel"]),
                    "features": features,
                }
            )
        return records


def _utc_timestamp(value: str, name: str) -> pd.Timestamp:
    try:
        timestamp = pd.Timestamp(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{name} must be an ISO 8601 timestamp with a UTC offset") from exc
    if timestamp.tzinfo is None:
        raise ValueError(f"{name} must include a UTC offset")
    return timestamp.tz_convert("UTC")


def generate_dataset(
    *,
    rig_count: int = 3,
    cycle_count: int = 1000,
    start: str = DEFAULT_START,
    end: str = DEFAULT_END,
    seed: int = DEFAULT_SEED,
) -> SyntheticDataset:
    """Generate stable IDs and five arbitrary synthetic features with injected labels."""
    if rig_count < 1:
        raise ValueError("rig_count must be at least 1")
    if cycle_count < 1:
        raise ValueError("cycle_count must be at least 1")

    start_at = _utc_timestamp(start, "start")
    end_at = _utc_timestamp(end, "end")
    if start_at >= end_at:
        raise ValueError("start must be earlier than end")

    rng = np.random.default_rng(seed)
    rig_rows = [
        {
            "id": str(uuid.uuid5(_NAMESPACE, f"rig:{index + 1:03d}")),
            "rigCode": f"RIG-SYN-{index + 1:02d}",
            "description": RIG_DESCRIPTION,
            "syntheticNotice": SYNTHETIC_NOTICE,
        }
        for index in range(rig_count)
    ]

    count = cycle_count
    progress = np.linspace(-0.25, 0.25, count)
    values: dict[str, np.ndarray] = {}
    for name, (mean, standard_deviation, _unit) in FEATURES.items():
        # A small background trend is deliberately distinct from the injected drift pattern.
        values[name] = mean + standard_deviation * (rng.normal(0.0, 1.0, count) + progress * 0.2)

    anomaly_count = max(1, int(round(count * 0.08)))
    anomaly_indices = rng.choice(count, size=anomaly_count, replace=False)
    rng.shuffle(anomaly_indices)
    pattern_groups = np.array_split(anomaly_indices, 3)

    # Isolated single-feature spikes.
    for index in pattern_groups[0]:
        values["vibration_rms"][index] += 8.0 * FEATURES["vibration_rms"][1]

    # A small labelled subset gradually shifts in temperature over its own sequence.
    drift_indices = np.sort(pattern_groups[1])
    if drift_indices.size:
        drift = np.linspace(2.5, 7.0, drift_indices.size)
        values["temperature_c"][drift_indices] += drift * FEATURES["temperature_c"][1]

    # A correlated shift across three synthetic features.
    for index in pattern_groups[2]:
        values["extension_time_ms"][index] += 5.0 * FEATURES["extension_time_ms"][1]
        values["pressure_kpa"][index] += 6.0 * FEATURES["pressure_kpa"][1]
        values["temperature_c"][index] += 4.0 * FEATURES["temperature_c"][1]

    labels = np.zeros(count, dtype=bool)
    labels[anomaly_indices] = True
    timestamps = pd.date_range(start_at, end_at, periods=count, inclusive="both")
    rig_ids = [row["id"] for row in rig_rows]

    cycle_rows: list[dict[str, Any]] = []
    for index, timestamp in enumerate(timestamps):
        cycle = {
            "id": str(uuid.uuid5(_NAMESPACE, f"cycle:{index + 1:06d}")),
            "cycleCode": f"CYC-{index + 1:06d}",
            "rigId": rig_ids[index % rig_count],
            "recordedAt": timestamp.isoformat(timespec="milliseconds").replace("+00:00", "Z"),
            "cycleType": SYNTHETIC_CYCLE_TYPE,
            "syntheticLabel": bool(labels[index]),
        }
        cycle.update({name: round(float(values[name][index]), 6) for name in FEATURE_NAMES})
        cycle_rows.append(cycle)

    return SyntheticDataset(
        rigs=pd.DataFrame(rig_rows, columns=RIG_COLUMNS),
        cycles=pd.DataFrame(cycle_rows, columns=CYCLE_COLUMNS),
    )


def write_dataset(dataset: SyntheticDataset, output_dir: Path) -> None:
    """Overwrite the four named fixture files; do not remove other directory contents."""
    output_dir.mkdir(parents=True, exist_ok=True)
    dataset.rigs.to_csv(output_dir / "rigs.csv", index=False)
    dataset.cycles.to_csv(output_dir / "cycles.csv", index=False)
    (output_dir / "rigs.json").write_text(
        json.dumps(dataset.rigs.to_dict(orient="records"), indent=2) + "\n",
        encoding="utf-8",
    )
    (output_dir / "cycles.json").write_text(
        json.dumps(dataset.cycle_records(), indent=2, allow_nan=False) + "\n",
        encoding="utf-8",
    )


def default_output_dir() -> Path:
    return Path(__file__).resolve().parents[2] / "data" / "generated"
