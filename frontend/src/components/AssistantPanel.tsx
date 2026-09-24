import { useState, type FormEvent } from "react";
import { api, apiErrorMessage } from "../api/client";
import type { AnswerResponse, UUID } from "../types/api";

interface AssistantPanelProps {
  cycleId: UUID | null;
  cycleCode: string | null;
}

export function AssistantPanel({ cycleId, cycleCode }: AssistantPanelProps) {
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState<AnswerResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submitQuestion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setAnswer(null);
    try {
      setAnswer(
        await api.askQuestion({
          question: question.trim(),
          ...(cycleId ? { cycleId } : {}),
        }),
      );
    } catch (requestError) {
      setError(apiErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }

  return (
    <section
      aria-labelledby="assistant-heading"
      className="panel assistant-panel"
    >
      <div className="panel-heading">
        <div>
          <span className="eyebrow">FICTIONAL REFERENCE NOTES</span>
          <h2 id="assistant-heading">Ask about the included notes</h2>
        </div>
        <span className="local-badge">
          <span aria-hidden="true">●</span> Uses demo notes only
        </span>
      </div>
      <p className="assistant-intro">
        This panel searches the fictional notes included with the demo. If a
        cycle is selected, its saved score is also sent as context. Answers cite
        the note excerpts they use; if a note does not support your question,
        the app says so.
      </p>
      <p className="assistant-example">
        Try: “What should I compare in the synthetic example?”
      </p>
      <form className="question-form" onSubmit={submitQuestion}>
        <label className="sr-only" htmlFor="assistant-question">
          Your question
        </label>
        <textarea
          aria-label="Your question"
          id="assistant-question"
          maxLength={500}
          onChange={(event) => setQuestion(event.target.value)}
          placeholder={
            cycleCode
              ? `Ask about ${cycleCode} or the demo notes…`
              : "Ask about the demo notes…"
          }
          required
          rows={3}
          value={question}
        />
        <div className="question-actions">
          <span>
            {question.length}/500 characters
            {cycleCode ? ` · Context: ${cycleCode}` : " · No cycle selected"}
          </span>
          <button
            className="button button-secondary"
            disabled={loading || !question.trim()}
            type="submit"
          >
            {loading ? "Searching notes…" : "Search notes"}
            {!loading && <span aria-hidden="true">↗</span>}
          </button>
        </div>
      </form>

      {error && (
        <div className="inline-error" role="alert">
          {error}
        </div>
      )}
      {answer && (
        <div className="answer-card" aria-live="polite">
          <div className="answer-status">
            <span
              className={
                answer.insufficientEvidence
                  ? "answer-dot answer-dot-muted"
                  : "answer-dot"
              }
            />
            {answer.insufficientEvidence
              ? "Not enough support in notes"
              : "Supported by notes"}
          </div>
          <p>{answer.answer}</p>
          {answer.citations.length > 0 && (
            <div className="citation-list">
              <span className="citation-label">NOTES USED FOR THIS ANSWER</span>
              {answer.citations.map((citation) => (
                <blockquote className="citation" key={citation.sourceId}>
                  <strong>{citation.title}</strong>
                  <span>{citation.excerpt}</span>
                  <small>{citation.sourceId}</small>
                </blockquote>
              ))}
            </div>
          )}
          <small className="answer-disclaimer">{answer.disclaimer}</small>
        </div>
      )}
    </section>
  );
}
