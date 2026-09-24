from __future__ import annotations

from uuid import UUID, uuid4

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.detector import score_cycles
from app.evaluation import evaluate_cycles
from app.generator import FEATURES, generate_dataset
from app.main import app
from app.schemas import (
    CycleFeatures,
    EvaluationCycle,
    EvaluationRequest,
    FeatureVector,
    ScoreRequest,
)


def feature_vector(**overrides: float) -> FeatureVector:
    values = {
        "extension_time_ms": 180.0,
        "pressure_kpa": 1000.0,
        "vibration_rms": 0.30,
        "temperature_c": 24.0,
        "cycle_duration_ms": 2800.0,
    }
    values.update(overrides)
    return FeatureVector(**values)


def cycle(index: int, features: FeatureVector) -> CycleFeatures:
    return CycleFeatures(cycleId=UUID(int=index + 1), features=features)


def test_scoring_is_deterministic_bounded_and_explains_every_feature() -> None:
    cycles = [cycle(i, feature_vector()) for i in range(8)]
    cycles.append(cycle(8, feature_vector(vibration_rms=1.2)))

    first = score_cycles(cycles, threshold=0.65)
    second = score_cycles(cycles, threshold=0.65)

    assert first == second
    assert first.modelName == "robust-zscore"
    assert first.modelVersion == "1"
    assert first.scoreDirection == "higher_is_more_anomalous"
    assert len(first.results) == len(cycles)
    assert first.results[-1].score == 1.0
    assert first.results[-1].isFlagged is True
    assert all(result.isFlagged == (result.score >= first.threshold) for result in first.results)
    assert all(0.0 <= result.score <= 1.0 for result in first.results)
    explanation = first.results[-1].explanation.features
    assert len(explanation) == len(FEATURES)
    assert [item.contribution for item in explanation] == sorted(
        (item.contribution for item in explanation), reverse=True
    )
    assert max(item.contribution for item in explanation) == first.results[-1].score


def test_zero_mad_uses_finite_fallback_and_threshold_comparison_is_inclusive() -> None:
    cycles = [cycle(i, feature_vector()) for i in range(4)]
    cycles.append(cycle(4, feature_vector(extension_time_ms=500.0)))

    threshold_at_score = score_cycles(cycles, threshold=1.0)
    all_at_zero = score_cycles(cycles, threshold=0.0)

    assert threshold_at_score.results[-1].score == 1.0
    assert threshold_at_score.results[-1].isFlagged is True
    assert all(result.isFlagged for result in all_at_zero.results)
    assert all(
        item.robustZScore == item.robustZScore and abs(item.robustZScore) != float("inf")
        for item in threshold_at_score.results[-1].explanation.features
    )


def test_evaluation_metrics_match_confusion_counts_and_labels_are_not_inputs() -> None:
    request = EvaluationRequest(
        cycles=[
            EvaluationCycle(
                cycleId=UUID(int=i + 1), features=feature_vector(), syntheticLabel=False
            )
            for i in range(4)
        ]
        + [
            EvaluationCycle(
                cycleId=UUID(int=5),
                features=feature_vector(extension_time_ms=500.0),
                syntheticLabel=True,
            )
        ]
    )

    result = evaluate_cycles(request)

    assert result.totalCycles == 5
    assert result.truePositives == 1
    assert result.falsePositives == 0
    assert result.trueNegatives == 4
    assert result.falseNegatives == 0
    assert result.precision == result.recall == result.f1 == 1.0
    assert "Synthetic evaluation only" in result.disclaimer


def test_zero_denominator_metrics_are_zero() -> None:
    request = EvaluationRequest(
        cycles=[
            EvaluationCycle(
                cycleId=UUID(int=i + 1), features=feature_vector(), syntheticLabel=False
            )
            for i in range(4)
        ]
    )

    result = evaluate_cycles(request)

    assert result.truePositives == result.falsePositives == 0
    assert result.precision == result.recall == result.f1 == 0.0


def test_generator_fixture_evaluation_is_repeatable_and_confusion_counts_add_up() -> None:
    dataset = generate_dataset(rig_count=2, cycle_count=180, seed=37)
    rows = dataset.cycles.to_dict(orient="records")
    request = EvaluationRequest(
        cycles=[
            EvaluationCycle(
                cycleId=row["id"],
                features=FeatureVector(**{name: row[name] for name in FEATURES}),
                syntheticLabel=bool(row["syntheticLabel"]),
            )
            for row in rows
        ]
    )

    first = evaluate_cycles(request)
    second = evaluate_cycles(request)

    assert first == second
    assert (
        first.truePositives + first.falsePositives + first.trueNegatives + first.falseNegatives
        == first.totalCycles
    )
    assert 0.0 <= first.precision <= 1.0
    assert 0.0 <= first.recall <= 1.0
    assert 0.0 <= first.f1 <= 1.0


def test_invalid_missing_nonfinite_and_duplicate_features_are_rejected() -> None:
    values = feature_vector().model_dump()
    missing_feature = dict(values)
    missing_feature.pop("temperature_c")
    with pytest.raises(ValidationError):
        FeatureVector.model_validate(missing_feature)

    with pytest.raises(ValidationError):
        FeatureVector.model_validate({**values, "pressure_kpa": float("inf")})

    duplicate = cycle(0, feature_vector())
    with pytest.raises(ValidationError, match="unique"):
        ScoreRequest(cycles=[duplicate, duplicate])


def test_fastapi_health_score_evaluate_openapi_and_common_validation_error() -> None:
    client = TestClient(app)
    values = feature_vector().model_dump()
    cycle_id = str(uuid4())
    body = {"threshold": 0.65, "cycles": [{"cycleId": cycle_id, "features": values}]}

    health = client.get("/health")
    assert health.status_code == 200
    assert health.json() == {"status": "UP", "dependencies": {}}

    scored = client.post("/v1/score", json=body)
    assert scored.status_code == 200
    assert scored.json()["modelName"] == "robust-zscore"
    assert scored.json()["results"][0]["cycleId"] == cycle_id

    invalid = dict(values)
    invalid.pop("temperature_c")
    response = client.post(
        "/v1/score", json={"cycles": [{"cycleId": cycle_id, "features": invalid}]}
    )
    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_ERROR"
    assert response.json()["message"] == "Request validation failed."
    assert response.json()["requestId"]
    assert "fieldErrors" in response.json()
    assert "detail" not in response.json()

    evaluation = client.post(
        "/v1/evaluate",
        json={"cycles": [{"cycleId": cycle_id, "features": values, "syntheticLabel": False}]},
    )
    assert evaluation.status_code == 200
    assert evaluation.json()["totalCycles"] == 1
    assert "precision" in evaluation.json()
    spec = client.get("/openapi.json").json()
    assert "/v1/score" in spec["paths"]
    assert "/v1/evaluate" in spec["paths"]
