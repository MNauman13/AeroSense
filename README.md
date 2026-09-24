# AeroSense: Engineering Test Data Intelligence

AeroSense is a local full-stack portfolio prototype for exploring **synthetic aircraft-system test data**. The dashboard supports rig and time filters, measurement trends, cycle details, saved anomaly scores, and answers grounded in fictional reference notes.

> **Synthetic demonstration only.** All data, measurements, notes, scores, and thresholds are fictional. They are not Airbus data, validated engineering limits, maintenance instructions, or safety advice. The project does not use or imply access to real aircraft data.

## Run locally

Prerequisite: Docker Desktop or another working Docker Engine with Docker Compose.

From the repository root:

    docker compose up --build -d

The first start builds the Java, Python, and frontend images. Compose waits for PostgreSQL and analytics health checks, generates a reproducible local fixture dataset, starts the API (which applies Flyway migrations), and then starts the dashboard.

Open http://localhost:5173 and select **Load synthetic demo data**, or seed with PowerShell:

    Invoke-RestMethod -Method Post http://localhost:8080/api/v1/demo-data/seed

Use **Run synthetic analysis** to score the stored cycles, then review cycle results and ask a question about the fictional notes. No external model credentials are needed.

Run the end-to-end smoke flow from PowerShell:

    .\scripts\e2e-smoke.ps1

Stop the services with docker compose down. To clear both local volumes (database and generated fixture files), use docker compose down --volumes.

The default ports are 5173 (dashboard), 8080 (Java API), 8000 (Python analytics/retrieval), and 5432 (PostgreSQL). Override ports and local demo credentials in a .env file based on .env.example. Defaults are only for local development.

## Services and design

- **Java 21 / Spring Boot:** public API, validation, persistence, Flyway migrations, and analysis orchestration.
- **PostgreSQL:** synthetic rigs, cycles, measurements, analysis runs, and results.
- **Python 3.12 / FastAPI:** robust z-score baseline, synthetic-only evaluation, and deterministic lexical retrieval over local notes.
- **React / TypeScript:** dashboard served by Nginx; the browser calls only the Java API.

The robust z-score baseline compares measurements within the selected synthetic cohort. Scores and feature contributions are software output and do not identify real equipment conditions. Evaluation precision, recall, and F1 describe only the generated fixture data.

See the [development guide](docs/development.md), [short demo script](docs/demo-script.md), [architecture](docs/architecture.md), [data dictionary](docs/data-dictionary.md), [API examples](docs/api-examples.md), and [OpenAPI contract](docs/openapi.yaml).

## Checks

Run Java checks from backend/:

    mvn spotless:check test

Run Python checks from analytics/:

    uv sync --group dev
    uv run ruff check app tests
    uv run ruff format --check app tests
    uv run pytest

Run frontend checks from frontend/:

    npm ci
    npm run format:check
    npm run typecheck
    npm test
    npm run build

Check Compose syntax without starting services with docker compose config --quiet. The live container startup and end-to-end smoke flow require a working Docker Engine.

## Scope

The local MVP uses a fixed-seed synthetic dataset and a small set of clearly fictional Markdown notes. The assistant returns retrieved evidence with citations or states that evidence is insufficient. No LLM is required. Cloud deployment, authentication, streaming, and operational aircraft data are outside the project scope.
