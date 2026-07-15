# ADR 0001: Build Stratus Lite Before Full Stratus

## Status

Accepted

## Context

The full Project Stratus plan includes multiple microservices, Kafka, several datastores, Kubernetes, observability infrastructure, and optimization tooling. Stratus Lite keeps the same control-plane domain while reducing infrastructure scope for local development.

## Decision

Build Stratus Lite first as a local-first modular monolith with a React dashboard, PostgreSQL, Docker Compose, placement logic, incident simulation, and tests.

## Consequences

- The project can be run and verified locally.
- The placement engine remains deep enough to show meaningful scheduling behavior.
- The architecture can still evolve toward microservices later.
- The README can clearly separate completed scope from future roadmap.
