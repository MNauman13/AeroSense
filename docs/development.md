# Development conventions

## Scope boundary

All example data, measurements, limits, rig descriptions, and reference notes are fictional and must be labelled synthetic. Never add Airbus, operational aircraft, or proprietary maintenance data. Nothing in this prototype is engineering or maintenance advice.

## Repository layout

- `backend/`: Java 21 / Spring Boot API and PostgreSQL access.
- `analytics/`: Python analytics and deterministic retrieval service.
- `frontend/`: React and TypeScript dashboard; it calls only the Java API.
- `data/`: generator instructions and ignored local generated output.
- `docs/`: architecture, contracts, API examples, and decisions.

## Formatting and linting

- Java: Maven with Spotless using Google Java Format.
- Python: Ruff for formatting and linting; pytest for tests.
- TypeScript: Prettier and ESLint; the frontend type-check is a separate check.

The Java service uses Java 21 with Spring Boot 3.5.16. In `backend/`, run `mvn spotless:check test`; use `mvn spotless:apply` to format Java sources. In `analytics/`, run `uv sync --group dev`, `uv run ruff check app tests`, `uv run ruff format --check app tests`, and `uv run pytest`. Start the Python service from `analytics/` with `uv run uvicorn app.main:app --host 0.0.0.0 --port 8000`. Frontend commands will be added when the client is scaffolded. Do not claim a check passes until its command has been run.

## Change workflow

Implement one README phase at a time, preserve existing work, run the phase's relevant checks, and keep the project runnable at each practical milestone. Record only architecture choices with meaningful trade-offs. Do not add authentication, streaming, Kubernetes, external model credentials, or cloud deployment to the local MVP.
