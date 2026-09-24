"""Offline metrics against the generator's synthetic-only ground-truth labels."""

from __future__ import annotations

from app.detector import score_cycles
from app.schemas import (
    CycleFeatures,
    EvaluationRequest,
    EvaluationResponse,
)

EVALUATION_DISCLAIMER = (
    "Synthetic evaluation only. Metrics do not generalize to real aircraft systems."
)


def evaluate_cycles(request: EvaluationRequest) -> EvaluationResponse:
    """Calculate precision/recall/F1 without sending labels into inference scoring."""
    inference_cycles = [
        CycleFeatures(cycleId=cycle.cycleId, features=cycle.features) for cycle in request.cycles
    ]
    scored = score_cycles(inference_cycles, threshold=request.threshold)
    labels_by_id = {cycle.cycleId: cycle.syntheticLabel for cycle in request.cycles}

    true_positives = false_positives = true_negatives = false_negatives = 0
    for result in scored.results:
        actual = labels_by_id[result.cycleId]
        predicted = result.isFlagged
        if predicted and actual:
            true_positives += 1
        elif predicted:
            false_positives += 1
        elif actual:
            false_negatives += 1
        else:
            true_negatives += 1

    precision = _ratio(true_positives, true_positives + false_positives)
    recall = _ratio(true_positives, true_positives + false_negatives)
    f1 = _ratio(2 * precision * recall, precision + recall)
    return EvaluationResponse(
        modelName=scored.modelName,
        modelVersion=scored.modelVersion,
        totalCycles=len(request.cycles),
        truePositives=true_positives,
        falsePositives=false_positives,
        trueNegatives=true_negatives,
        falseNegatives=false_negatives,
        precision=precision,
        recall=recall,
        f1=f1,
        disclaimer=EVALUATION_DISCLAIMER,
    )


def _ratio(numerator: int | float, denominator: int | float) -> float:
    return float(numerator / denominator) if denominator else 0.0
