"""Feature matrix construction shared by scoring and synthetic evaluation."""

from __future__ import annotations

import pandas as pd

from app.schemas import CycleFeatures, FeatureName

FEATURE_NAMES: tuple[FeatureName, ...] = (
    "extension_time_ms",
    "pressure_kpa",
    "vibration_rms",
    "temperature_c",
    "cycle_duration_ms",
)


def feature_matrix(cycles: list[CycleFeatures]) -> pd.DataFrame:
    """Return only the five inference features, indexed by cycle ID."""
    return pd.DataFrame(
        [cycle.features.model_dump() for cycle in cycles],
        index=[str(cycle.cycleId) for cycle in cycles],
        columns=FEATURE_NAMES,
        dtype="float64",
    )
