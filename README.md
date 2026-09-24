# AeroSense: Engineering Test Data Intelligence

AeroSense is a portfolio prototype for exploring **synthetic aircraft-system test data**. Every measurement, limit, rig description, and reference note is fictional and generated for demonstration. This project is not an aircraft digital twin, a validated analytics tool, engineering guidance, or a maintenance system. It does not use Airbus or real aircraft data.

## Project status

The application is being implemented in small phases. See the [development guide](docs/development.md), [architecture](docs/architecture.md), [data dictionary](docs/data-dictionary.md), and [API examples](docs/api-examples.md) for current contracts and setup status. Local run commands will be documented and verified as the runnable services are added.

## Planned local workflow

The target stack is Java 21 / Spring Boot, PostgreSQL, Python / FastAPI, and React / TypeScript, orchestrated with Docker Compose. The core application will work without external LLM credentials. All displayed data and analysis will be marked synthetic.

## Safety and scope

Scores, thresholds, explanations, and notes are arbitrary synthetic demonstration output. They are not validated engineering limits, maintenance instructions, or safety advice. Synthetic evaluation metrics apply only to the generated dataset and do not generalize to aircraft systems.
