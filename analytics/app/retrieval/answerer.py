"""Answer only from passages retrieved from the fictional reference corpus."""

from __future__ import annotations

import logging
import re
from typing import Any, Protocol

from app.retrieval.corpus import Passage
from app.retrieval.retriever import retrieve
from app.schemas import AnswerResponse, Citation, RetrievalRequest

DISCLAIMER = "Synthetic demonstration only. Not engineering or maintenance advice."
INSUFFICIENT_ANSWER = "I could not find support for that in the demo sources."
_ANALYSIS_TERMS = {"anomalous", "anomaly", "flag", "flagged", "score", "scoring"}
logger = logging.getLogger(__name__)


class AnswerProvider(Protocol):
    def answer(
        self,
        question: str,
        passages: list[Passage],
        cycle_context: dict[str, Any] | None,
    ) -> str: ...


class DeterministicAnswerProvider:
    """Phrase retrieved text and stored synthetic analysis without a model call."""

    def answer(
        self,
        question: str,
        passages: list[Passage],
        cycle_context: dict[str, Any] | None,
    ) -> str:
        cycle_answer = _cycle_analysis_answer(question, cycle_context)
        evidence = " ".join(f"“{passage.excerpt}”" for passage in passages)
        if cycle_answer:
            return f"{cycle_answer} The fictional demo notes say: {evidence}"
        return f"The fictional demo notes say: {evidence}"


def answer_question(
    request: RetrievalRequest,
    passages: list[Passage],
    provider: AnswerProvider | None = None,
) -> AnswerResponse:
    matches = retrieve(request.question, passages)
    if not matches:
        return _insufficient()

    cycle_answer = _cycle_analysis_answer(request.question, request.cycleContext)
    if (
        cycle_answer is None
        and request.cycleId is not None
        and _asks_about_analysis(request.question)
    ):
        return AnswerResponse(
            answer="I could not find a stored synthetic analysis result for that cycle.",
            insufficientEvidence=True,
            citations=[],
            disclaimer=DISCLAIMER,
        )

    answer_provider = provider or DeterministicAnswerProvider()
    try:
        answer = answer_provider.answer(request.question, matches, request.cycleContext)
        if not isinstance(answer, str) or not answer.strip():
            raise ValueError("The answer provider returned an empty response.")
        answer = answer.strip()
    except Exception as exception:
        logger.warning(
            "Answer provider failed; using deterministic local fallback (%s).",
            type(exception).__name__,
        )
        answer = DeterministicAnswerProvider().answer(
            request.question, matches, request.cycleContext
        )
    citations = [
        Citation(sourceId=passage.source_id, title=passage.title, excerpt=passage.excerpt)
        for passage in matches
    ]
    return AnswerResponse(
        answer=answer,
        insufficientEvidence=False,
        citations=citations,
        disclaimer=DISCLAIMER,
    )


def _asks_about_analysis(question: str) -> bool:
    tokens = set(re.findall(r"[a-z0-9]+", question.lower()))
    return bool(tokens & _ANALYSIS_TERMS)


def _cycle_analysis_answer(question: str, context: dict[str, Any] | None) -> str | None:
    if not context or not _asks_about_analysis(question):
        return None
    analysis = context.get("latestAnalysis")
    if not isinstance(analysis, dict):
        return None
    score = analysis.get("score")
    threshold = analysis.get("threshold")
    flagged = analysis.get("isFlagged")
    cycle_code = context.get("cycleCode", "the requested synthetic cycle")
    if not isinstance(score, (int, float)) or not isinstance(threshold, (int, float)):
        return None
    explanation = analysis.get("explanation")
    features = explanation.get("features", []) if isinstance(explanation, dict) else []
    highest = max(
        (feature for feature in features if isinstance(feature, dict)),
        key=lambda feature: feature.get("contribution", -1),
        default=None,
    )
    detail = (
        f" Its largest stored feature contribution was {highest.get('featureName')} "
        f"({highest.get('contribution'):.3f})."
        if highest and isinstance(highest.get("contribution"), (int, float))
        else ""
    )
    state = "flagged" if flagged is True else "not flagged"
    return (
        f"Synthetic cycle {cycle_code} was {state} with score {score:.3f} "
        f"at threshold {threshold:.3f}.{detail}"
    )


def _insufficient() -> AnswerResponse:
    return AnswerResponse(
        answer=INSUFFICIENT_ANSWER,
        insufficientEvidence=True,
        citations=[],
        disclaimer=DISCLAIMER,
    )
