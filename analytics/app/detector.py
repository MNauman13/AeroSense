"""Transparent cohort-relative robust z-score baseline."""

from __future__ import annotations

import math
from collections.abc import Sequence

import numpy as np

from app.features import FEATURE_NAMES, feature_matrix
from app.schemas import (
    CycleFeatures,
    Explanation,
    FeatureContribution,
    ScoredCycle,
    ScoreResponse,
)

MODEL_NAME = "robust-zscore"
MODEL_VERSION = "1"
SCORE_DIRECTION = "higher_is_more_anomalous"
EXPLANATION_METHOD = "absolute_robust_z"
ROBUST_SCALE_FACTOR = 1.4826
ZERO_MAD_DENOMINATOR = 1e-9
ROBUST_Z_SATURATION = 8.0
_MAX_FLOAT = np.finfo(np.float64).max


def _safe_difference(value: float, center: float) -> float:
    difference = value - center
    if math.isfinite(difference):
        return difference
    return math.copysign(_MAX_FLOAT, value)


def _safe_median(values: Sequence[float]) -> float:
    ordered = np.sort(np.asarray(values, dtype=np.float64))
    middle = len(ordered) // 2
    if len(ordered) % 2:
        return float(ordered[middle])
    # Halving before adding avoids overflow when the two central values are large.
    return float(ordered[middle - 1] * 0.5 + ordered[middle] * 0.5)


def _robust_stats(values: np.ndarray) -> tuple[float, float]:
    center = _safe_median(values)
    deviations = [_safe_difference(float(value), center) for value in values]
    absolute_deviations = [min(abs(value), _MAX_FLOAT) for value in deviations]
    mad = _safe_median(absolute_deviations)
    if mad == 0.0:
        return center, ZERO_MAD_DENOMINATOR
    if mad > _MAX_FLOAT / ROBUST_SCALE_FACTOR:
        return center, _MAX_FLOAT
    return center, ROBUST_SCALE_FACTOR * mad


def score_cycles(cycles: list[CycleFeatures], threshold: float = 0.65) -> ScoreResponse:
    """Score one or more cycles; labels are not part of this function or request type."""
    matrix = feature_matrix(cycles)
    centers_and_scales = {
        name: _robust_stats(matrix[name].to_numpy(dtype=np.float64)) for name in FEATURE_NAMES
    }
    results: list[ScoredCycle] = []

    for cycle in cycles:
        values = cycle.features.model_dump()
        contributions: list[FeatureContribution] = []
        for name in FEATURE_NAMES:
            center, scale = centers_and_scales[name]
            delta = _safe_difference(values[name], center)
            robust_z = delta / scale
            if not math.isfinite(robust_z):
                robust_z = math.copysign(_MAX_FLOAT, delta)
            contribution = min(abs(robust_z) / ROBUST_Z_SATURATION, 1.0)
            contributions.append(
                FeatureContribution(
                    featureName=name,
                    observedValue=values[name],
                    baselineMedian=center,
                    robustZScore=robust_z,
                    contribution=contribution,
                )
            )

        contributions.sort(key=lambda item: (-item.contribution, item.featureName))
        score = max(item.contribution for item in contributions)
        results.append(
            ScoredCycle(
                cycleId=cycle.cycleId,
                score=score,
                isFlagged=score >= threshold,
                explanation=Explanation(method=EXPLANATION_METHOD, features=contributions),
            )
        )

    return ScoreResponse(
        modelName=MODEL_NAME,
        modelVersion=MODEL_VERSION,
        threshold=threshold,
        scoreDirection=SCORE_DIRECTION,
        results=results,
    )
