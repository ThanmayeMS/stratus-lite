# ADR 0001: Build Stratus Lite Before Full Stratus

## Status

Accepted

## Context

The full Project Stratus plan includes multiple microservices, Kafka, several datastores, Kubernetes, observability infrastructure, and optimization tooling. That is a strong long-term portfolio project, but it is too large for a 3-day build.

## Decision

Build Stratus Lite first as a local-first modular monolith with a React dashboard, PostgreSQL, Docker Compose, placement logic, incident simulation, and tests.

## Consequences

- The project can be finished and demoed quickly.
- The placement engine remains deep enough for interviews.
- The architecture can still evolve toward microservices later.
- The README can clearly separate completed MVP scope from future roadmap.

