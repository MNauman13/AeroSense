"""Pydantic wire models shared with docs/openapi.yaml and frontend TypeScript types."""

from __future__ import annotations

from typing import Literal, Self
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator

FeatureName = Literal[
    "extension_time_ms",
    "pressure_kpa",
    "vibration_rms",
    "temperature_c",
    "cycle_duration_ms",
]


class ContractModel(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)


class FeatureVector(ContractModel):
    extension_time_ms: float = Field(allow_inf_nan=False)
    pressure_kpa: float = Field(allow_inf_nan=False)
    vibration_rms: float = Field(allow_inf_nan=False)
    temperature_c: float = Field(allow_inf_nan=False)
    cycle_duration_ms: float = Field(allow_inf_nan=False)


class CycleFeatures(ContractModel):
    cycleId: UUID
    features: FeatureVector


class ScoreRequest(ContractModel):
    threshold: float = Field(default=0.65, ge=0.0, le=1.0)
    cycles: list[CycleFeatures] = Field(min_length=1)

    @model_validator(mode="after")
    def unique_cycle_ids(self) -> Self:
        ids = [cycle.cycleId for cycle in self.cycles]
        if len(ids) != len(set(ids)):
            raise ValueError("cycleId values must be unique within a request")
        return self


class FeatureContribution(ContractModel):
    featureName: FeatureName
    observedValue: float
    baselineMedian: float
    robustZScore: float
    contribution: float = Field(ge=0.0, le=1.0)


class Explanation(ContractModel):
    method: Literal["absolute_robust_z"] = "absolute_robust_z"
    features: list[FeatureContribution]


class ScoredCycle(ContractModel):
    cycleId: UUID
    score: float = Field(ge=0.0, le=1.0)
    isFlagged: bool
    explanation: Explanation


class ScoreResponse(ContractModel):
    modelName: Literal["robust-zscore"] = "robust-zscore"
    modelVersion: str = "1"
    threshold: float = Field(ge=0.0, le=1.0)
    scoreDirection: Literal["higher_is_more_anomalous"] = "higher_is_more_anomalous"
    results: list[ScoredCycle]


class EvaluationCycle(ContractModel):
    cycleId: UUID
    features: FeatureVector
    syntheticLabel: bool


class EvaluationRequest(ContractModel):
    threshold: float = Field(default=0.65, ge=0.0, le=1.0)
    cycles: list[EvaluationCycle] = Field(min_length=1)

    @model_validator(mode="after")
    def unique_cycle_ids(self) -> Self:
        ids = [cycle.cycleId for cycle in self.cycles]
        if len(ids) != len(set(ids)):
            raise ValueError("cycleId values must be unique within a request")
        return self


class EvaluationResponse(ContractModel):
    modelName: Literal["robust-zscore"] = "robust-zscore"
    modelVersion: str = "1"
    totalCycles: int = Field(ge=0)
    truePositives: int = Field(ge=0)
    falsePositives: int = Field(ge=0)
    trueNegatives: int = Field(ge=0)
    falseNegatives: int = Field(ge=0)
    precision: float = Field(ge=0.0, le=1.0)
    recall: float = Field(ge=0.0, le=1.0)
    f1: float = Field(ge=0.0, le=1.0)
    disclaimer: str


class QuestionRequest(ContractModel):
    question: str = Field(min_length=1, max_length=500)
    cycleId: UUID | None = None


class Citation(ContractModel):
    sourceId: str
    title: str
    excerpt: str


class AnswerResponse(ContractModel):
    answer: str
    insufficientEvidence: bool
    citations: list[Citation]
    disclaimer: str


class RetrievalRequest(QuestionRequest):
    cycleContext: dict[str, object] | None = None


class ApiError(ContractModel):
    code: str
    message: str
    requestId: str
    fieldErrors: dict[str, list[str]] | None = None


class HealthResponse(ContractModel):
    status: Literal["UP", "DEGRADED", "DOWN"]
    dependencies: dict[str, Literal["UP", "DOWN", "UNKNOWN"]] = Field(default_factory=dict)
