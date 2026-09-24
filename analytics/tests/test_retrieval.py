from uuid import UUID

from fastapi.testclient import TestClient

from app.main import app
from app.retrieval.answerer import answer_question
from app.retrieval.corpus import load_corpus
from app.schemas import RetrievalRequest

client = TestClient(app)


def test_corpus_ids_are_stable_and_note_content_is_clearly_fictional() -> None:
    first = load_corpus()
    second = load_corpus()

    assert [(item.source_id, item.excerpt) for item in first] == [
        (item.source_id, item.excerpt) for item in second
    ]
    assert all("#chunk-" in item.source_id for item in first)
    assert any("fictional" in item.title.lower() for item in first)


def test_question_returns_ranked_evidence_and_repeatable_citations() -> None:
    request = RetrievalRequest(question="What does the synthetic score threshold mean?")

    first = answer_question(request, load_corpus())
    second = answer_question(request, load_corpus())

    assert first == second
    assert not first.insufficientEvidence
    assert first.citations
    assert all(citation.sourceId.startswith("demo-note-") for citation in first.citations)
    assert "not an engineering limit" in first.answer
    assert (
        first.disclaimer == "Synthetic demonstration only. Not engineering or maintenance advice."
    )


def test_cycle_analysis_answer_uses_supplied_synthetic_context_and_note_citation() -> None:
    request = RetrievalRequest(
        question="Why was this cycle flagged?",
        cycleId=UUID("00000000-0000-4000-8000-000000000101"),
        cycleContext={
            "cycleCode": "CYC-000101",
            "latestAnalysis": {
                "score": 0.8,
                "threshold": 0.65,
                "isFlagged": True,
                "explanation": {
                    "features": [
                        {"featureName": "vibration_rms", "contribution": 0.8},
                        {"featureName": "temperature_c", "contribution": 0.1},
                    ]
                },
            },
        },
    )

    response = answer_question(request, load_corpus())

    assert not response.insufficientEvidence
    assert "CYC-000101" in response.answer
    assert "score 0.800 at threshold 0.650" in response.answer
    assert "vibration_rms" in response.answer
    assert response.citations


def test_specific_cycle_question_without_analysis_is_insufficient() -> None:
    request = RetrievalRequest(
        question="Why was this cycle flagged?",
        cycleId=UUID("00000000-0000-4000-8000-000000000101"),
        cycleContext={"cycleCode": "CYC-000101", "features": {}},
    )

    response = answer_question(request, load_corpus())

    assert response.insufficientEvidence
    assert response.citations == []
    assert response.answer == "I could not find a stored synthetic analysis result for that cycle."


def test_unsupported_question_does_not_invent_an_answer() -> None:
    response = answer_question(
        RetrievalRequest(question="What is the exact repair torque for a carbon brake actuator?"),
        load_corpus(),
    )

    assert response.insufficientEvidence
    assert response.citations == []
    assert response.answer == "I could not find support for that in the demo sources."


def test_http_retrieval_route_and_health_report_local_corpus() -> None:
    health = client.get("/health")
    answer = client.post(
        "/v1/answer", json={"question": "What does the synthetic review note say about vibration?"}
    )

    assert health.status_code == 200
    assert health.json()["dependencies"]["retrieval"] == "UP"
    assert answer.status_code == 200
    assert answer.json()["citations"]
    assert (
        answer.json()["disclaimer"]
        == "Synthetic demonstration only. Not engineering or maintenance advice."
    )


def test_http_retrieval_route_validates_question_length() -> None:
    response = client.post("/v1/answer", json={"question": "x" * 501})

    assert response.status_code == 422
    assert response.json()["code"] == "VALIDATION_ERROR"
