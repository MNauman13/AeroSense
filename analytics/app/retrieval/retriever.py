"""Small deterministic BM25-style lexical ranker."""

from __future__ import annotations

import math
import re
from collections import Counter

from app.retrieval.corpus import Passage

_TOKEN_PATTERN = re.compile(r"[a-z0-9]+")
_STOP_WORDS = {
    "a",
    "about",
    "an",
    "and",
    "are",
    "does",
    "for",
    "from",
    "how",
    "i",
    "is",
    "it",
    "me",
    "of",
    "on",
    "or",
    "please",
    "say",
    "show",
    "tell",
    "that",
    "the",
    "this",
    "to",
    "was",
    "what",
    "when",
    "where",
    "which",
    "who",
    "why",
    "with",
}


def retrieve(question: str, passages: list[Passage], limit: int = 3) -> list[Passage]:
    """Rank passages by query term coverage and frequency; ties use stable source IDs."""
    terms = [
        token for token in _TOKEN_PATTERN.findall(question.lower()) if token not in _STOP_WORDS
    ]
    query_terms = set(terms)
    if not query_terms:
        return []

    tokenized = [_TOKEN_PATTERN.findall(passage.excerpt.lower()) for passage in passages]
    frequencies = [Counter(tokens) for tokens in tokenized]
    document_frequency = {
        term: sum(term in counts for counts in frequencies) for term in query_terms
    }
    average_length = sum(map(len, tokenized)) / max(len(tokenized), 1)
    ranked: list[tuple[float, Passage]] = []
    minimum_coverage = max(1, math.ceil(len(query_terms) * 0.3))
    for passage, tokens, counts in zip(passages, tokenized, frequencies, strict=True):
        coverage = sum(counts[term] > 0 for term in query_terms)
        if coverage < minimum_coverage:
            continue
        score = 0.0
        length_norm = len(tokens) / average_length if average_length else 0.0
        for term in query_terms:
            term_frequency = counts[term]
            if term_frequency == 0:
                continue
            inverse_document_frequency = math.log(
                1
                + (len(passages) - document_frequency[term] + 0.5)
                / (document_frequency[term] + 0.5)
            )
            score += inverse_document_frequency * (
                term_frequency * 2.2 / (term_frequency + 1.2 * (0.25 + 0.75 * length_norm))
            )
        if score > 0:
            ranked.append((score, passage))
    ranked.sort(key=lambda result: (-result[0], result[1].source_id))
    return [passage for _, passage in ranked[:limit]]
