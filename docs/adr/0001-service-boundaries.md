# ADR 0001: Keep application workflow in Java and analytics in Python

- Status: Accepted
- Date: 2026-09-24

## Context

The prototype needs a persistent public API and a small analytics/retrieval component, and the requested stack explicitly assigns those responsibilities to Java and Python. Keeping the browser away from both the database and Python services also gives the app one stable public contract.

## Decision

Spring Boot owns public API requests, validation, persistence, and orchestration. Python owns versioned scoring, synthetic evaluation, and lexical retrieval. Deterministic answer phrasing is the local default; an optional provider can phrase retrieved evidence and falls back locally on error. For the local MVP, analytics and retrieval can run as separate modules in one FastAPI process.

## Consequences

The Java API remains the only browser-facing service, and the shared JSON contract must be kept in sync across Java DTOs, Pydantic schemas, OpenAPI, and TypeScript. A single Python process is the smallest local deployment; modules remain independently testable without introducing extra services or frameworks.
