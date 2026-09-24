# Development guide

## Scope boundary

All example data, measurements, limits, rig descriptions, and reference notes are fictional and must be labelled synthetic. Never add Airbus, operational aircraft, or proprietary maintenance data. Nothing in this prototype is engineering or maintenance advice.

## Repository layout

- backend/: Java 21 / Spring Boot API and PostgreSQL access.
- analytics/: Python analytics and deterministic retrieval service.
- frontend/: React and TypeScript dashboard; it calls only the Java API.
- data/: generator instructions and ignored local generated output.
- docs/: architecture, contracts, API examples, and decisions.

## Run the local Compose environment

From the repository root, start the services with:

    docker compose up --build -d

Compose starts PostgreSQL, the analytics and retrieval service, the Java API, and the React dashboard served by Nginx. PostgreSQL and analytics health checks gate backend startup. The synthetic fixture generator must complete before the Java API starts. Spring Boot applies the Flyway schema migration during startup, and the dashboard waits for the API health check.

Open http://localhost:5173. Load the generated dataset by choosing Load synthetic demo data, or run this from PowerShell:

    Invoke-RestMethod -Method Post http://localhost:8080/api/v1/demo-data/seed

The one-shot synthetic-fixtures service generates 3 synthetic rigs and 1,000 cycles with the fixed seed 20260101. Generation output and database state use local Docker volumes. The data can be regenerated deterministically by removing volumes and starting Compose again.

Run the end-to-end seed → analysis → browse → question flow from PowerShell:

    .\scripts\e2e-smoke.ps1

Stop services with docker compose down. Remove database and generated fixture files with docker compose down --volumes. Compose port and local demo credentials can be changed in a local .env based on .env.example. The defaults are for local development only.

## Local browser client

To run the client outside Compose, change to frontend/, install the locked dependencies with npm ci, and start Vite with npm run dev. It serves http://localhost:5173 and proxies /api requests to the Java API at http://localhost:8080. Set VITE_API_PROXY_TARGET if the API uses another address.

The dashboard provides rig, UTC date, and anomaly-status filters; cycle and analysis summaries; measurement trends; paginated cycles; cycle detail and score contributions; and a cited assistant panel. All assistant answers are deterministic and use the fictional Markdown notes in analytics/reference_notes. No model key is required.

## Checks

Java checks from backend/:

    mvn spotless:check test

Use mvn spotless:apply to format Java sources.

Python checks from analytics/:

    uv sync --group dev
    uv run ruff check app tests
    uv run ruff format --check app tests
    uv run pytest

Start the Python service directly from analytics/ with uv run uvicorn app.main:app --host 0.0.0.0 --port 8000.

Frontend checks from frontend/:

    npm run format:check
    npm run typecheck
    npm test
    npm run build

Check Compose syntax without starting containers with docker compose config --quiet. Container builds, migrations, and the live end-to-end smoke test require a working Docker Engine.

## Implementation choices

- Java owns public API validation, database access, and persisted analysis runs. Python owns the scoring calculation and local note retrieval.
- The baseline uses cohort-relative robust z-scores with deterministic numerical contributions. This is a transparent software demonstration, not a validated method.
- The retrieval service uses lexical ranking over local fictional notes. It returns cited source passages or an insufficient-evidence response.
- PostgreSQL migrations run through Spring Boot Flyway startup. The optional tools profile can run Flyway manually.
- All evaluation labels are used only by the evaluation request and are omitted from cycle browsing, inference, and assistant context.

## Limitations

The scores, threshold, explanations, sample values, and notes are synthetic demonstration output. Evaluation metrics apply only to the generated fixture dataset. They do not generalize to aircraft equipment and provide no operating limits, diagnosis, inspection steps, or maintenance advice.

Do not add authentication, streaming, Kubernetes, a required external LLM, or cloud deployment to the local MVP.
