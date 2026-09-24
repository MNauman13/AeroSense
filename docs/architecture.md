# Architecture

## System boundary

```mermaid
flowchart LR
  User[Analyst] --> Web[React / TypeScript]
  Web --> API[Java / Spring Boot API]
  API --> DB[(PostgreSQL)]
  API --> Analytics[Python / FastAPI]
  API --> Retrieval[Python deterministic retrieval]
  Retrieval --> Notes[Fictional Markdown notes]
```

The React client calls only the Java API. Java owns public HTTP contracts, workflow, and persistence. Python accepts versioned JSON for scoring, evaluation, and retrieval, and does not own the relational database. The same Python service can host analytics and retrieval for this small local demo while keeping their modules separate. PostgreSQL stores generated cycles and persisted analysis results.

## Data and safety

All data and notes are generated or written as fictional demonstration material and labelled synthetic. Ground-truth anomaly labels exist only to evaluate the generator's injected patterns; they are not model inputs and are not returned in ordinary cycle browsing. A higher anomaly score means more unusual relative to this synthetic scoring cohort. Neither scores nor thresholds represent engineering limits.

## Request paths

- Browse: browser → Java API → PostgreSQL.
- Analyze: Java API → Python scoring → PostgreSQL; results include method, version, threshold, and feature contributions.
- Ask: browser → Java API → Python lexical retrieval; answers cite retrieved fictional note excerpts or state that the sources do not support an answer.

Analysis runs execute synchronously over at most 10,000 selected stored cycles. The Java API saves the requested threshold and run status, sends feature vectors without ground-truth labels to Python, then stores scores and numerical explanations. If analytics is unavailable, the run is stored as `FAILED` with a safe summary and no result rows. Synthetic labels are loaded only for the run's separate evaluation request.

See [ADR 0001](adr/0001-service-boundaries.md) for the Java/Python boundary and [the data dictionary](data-dictionary.md) for wire contracts.
