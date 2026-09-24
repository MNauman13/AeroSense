"""AeroSense synthetic analytics service; no external model credentials are used."""

from __future__ import annotations

from collections import defaultdict
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.detector import score_cycles
from app.evaluation import evaluate_cycles
from app.schemas import (
    ApiError,
    EvaluationRequest,
    EvaluationResponse,
    HealthResponse,
    ScoreRequest,
    ScoreResponse,
)

app = FastAPI(
    title="AeroSense Analytics API",
    version="1.0.0",
    description=(
        "Synthetic-only scoring and evaluation. Scores are not engineering limits, "
        "maintenance guidance, or safety advice."
    ),
)


@app.exception_handler(RequestValidationError)
async def request_validation_error(_request: Request, exception: RequestValidationError):
    field_errors: dict[str, list[str]] = defaultdict(list)
    for error in exception.errors():
        location = ".".join(str(part) for part in error.get("loc", ())[1:]) or "request"
        field_errors[location].append(str(error.get("msg", "Invalid value.")))

    response = ApiError(
        code="VALIDATION_ERROR",
        message="Request validation failed.",
        requestId=str(uuid4()),
        fieldErrors=dict(field_errors),
    )
    return JSONResponse(status_code=422, content=response.model_dump(mode="json", by_alias=True))


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(status="UP")


@app.post("/v1/score", response_model=ScoreResponse)
def score(request: ScoreRequest) -> ScoreResponse:
    return score_cycles(request.cycles, threshold=request.threshold)


@app.post("/v1/evaluate", response_model=EvaluationResponse)
def evaluate(request: EvaluationRequest) -> EvaluationResponse:
    return evaluate_cycles(request)
