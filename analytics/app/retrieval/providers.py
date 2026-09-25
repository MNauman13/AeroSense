"""Optional OpenAI-compatible phrasing with a deterministic local fallback."""

from __future__ import annotations

import json
import logging
import os
from collections.abc import Mapping
from typing import Any

import httpx

from app.retrieval.answerer import AnswerProvider, DeterministicAnswerProvider
from app.retrieval.corpus import Passage

logger = logging.getLogger(__name__)


class OpenAICompatibleAnswerProvider:
    """Ask an optional chat-completions endpoint to phrase retrieved evidence."""

    def __init__(
        self,
        api_key: str,
        model: str,
        base_url: str = "https://api.openai.com/v1",
        timeout_seconds: float = 8.0,
        transport: httpx.BaseTransport | None = None,
    ) -> None:
        self.api_key = api_key
        self.model = model
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds
        self.transport = transport

    def answer(
        self,
        question: str,
        passages: list[Passage],
        cycle_context: dict[str, Any] | None,
    ) -> str:
        system_message = (
            "Answer the narrow question using only the supplied retrieved fictional notes and "
            "synthetic cycle context. Do not add facts, real-world operating limits, diagnoses, "
            "maintenance steps, or safety advice. If evidence does not support a detail, say so. "
            "When referring to a note, cite only its exact source ID from the supplied list. "
            "All measurements and analysis values are synthetic software demonstration output."
        )
        user_context = {
            "question": question,
            "retrievedNotes": [
                {
                    "sourceId": passage.source_id,
                    "title": passage.title,
                    "excerpt": passage.excerpt,
                }
                for passage in passages
            ],
            "syntheticCycleContext": cycle_context,
        }
        payload = {
            "model": self.model,
            "temperature": 0,
            "max_tokens": 220,
            "messages": [
                {"role": "system", "content": system_message},
                {
                    "role": "user",
                    "content": json.dumps(user_context, ensure_ascii=False),
                },
            ],
        }
        timeout = httpx.Timeout(
            self.timeout_seconds,
            connect=min(2.0, self.timeout_seconds),
        )
        with httpx.Client(timeout=timeout, transport=self.transport) as client:
            response = client.post(
                f"{self.base_url}/chat/completions",
                headers={"Authorization": f"Bearer {self.api_key}"},
                json=payload,
            )
            response.raise_for_status()

        content = response.json()["choices"][0]["message"]["content"]
        if not isinstance(content, str) or not content.strip():
            raise ValueError("The answer provider returned no text.")
        return content.strip()


class FallbackAnswerProvider:
    """Keep answers available when the optional provider is misconfigured or down."""

    def __init__(self, primary: AnswerProvider, fallback: AnswerProvider) -> None:
        self.primary = primary
        self.fallback = fallback

    def answer(
        self,
        question: str,
        passages: list[Passage],
        cycle_context: dict[str, Any] | None,
    ) -> str:
        try:
            answer = self.primary.answer(question, passages, cycle_context)
            if not isinstance(answer, str) or not answer.strip():
                raise ValueError("The answer provider returned no text.")
            return answer.strip()
        except Exception as exception:
            logger.warning(
                "Optional answer provider failed; deterministic local fallback used (%s).",
                type(exception).__name__,
            )
            return self.fallback.answer(question, passages, cycle_context)


def create_answer_provider(
    environment: Mapping[str, str] | None = None,
) -> AnswerProvider:
    """Build a provider from environment configuration without requiring credentials."""
    values = os.environ if environment is None else environment
    provider_name = values.get("AEROSENSE_ANSWER_PROVIDER", "deterministic").strip().lower()
    deterministic = DeterministicAnswerProvider()
    if provider_name == "deterministic":
        return deterministic
    if provider_name != "openai-compatible":
        raise ValueError("AEROSENSE_ANSWER_PROVIDER must be deterministic or openai-compatible.")

    api_key = values.get("AEROSENSE_LLM_API_KEY", "").strip()
    model = values.get("AEROSENSE_LLM_MODEL", "").strip()
    if not api_key or not model:
        logger.warning(
            "Optional answer provider is selected but lacks a model or API key; "
            "deterministic local answers remain enabled."
        )
        return deterministic

    try:
        timeout_seconds = float(values.get("AEROSENSE_LLM_TIMEOUT_SECONDS", "8"))
        if not 0 < timeout_seconds <= 60:
            raise ValueError
    except ValueError:
        logger.warning(
            "Optional answer provider timeout is invalid; "
            "deterministic local answers remain enabled."
        )
        return deterministic

    return FallbackAnswerProvider(
        primary=OpenAICompatibleAnswerProvider(
            api_key=api_key,
            model=model,
            base_url=values.get("AEROSENSE_LLM_BASE_URL", "https://api.openai.com/v1"),
            timeout_seconds=timeout_seconds,
        ),
        fallback=deterministic,
    )
