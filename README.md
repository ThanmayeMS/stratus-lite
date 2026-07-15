# Stratus Lite

Stratus Lite is a local-first cloud workload placement control plane.

It simulates how a cloud platform accepts workload requests, chooses the best cell for each workload, reserves capacity, tracks lifecycle state, detects overload, recommends rebalancing actions, and explains each operational decision.

The project runs entirely on a laptop with free local tooling. No external cloud account or paid API is required.

## Technical Highlights

- Backend system design with Java and Spring Boot
- React + TypeScript dashboard development
- Relational data modeling with PostgreSQL
- Capacity-aware scheduling algorithms
- Workload lifecycle orchestration
- Incident simulation and rebalancing
- Monitor-only autonomous reconciler
- Operational metrics endpoint
- Auditability and operational risk insights
- Test automation and CI
- Docker Compose based local development

## V1 Scope

The first version is intentionally focused:

- One Spring Boot backend with clean internal modules
- One React + TypeScript dashboard
- PostgreSQL for durable state
- Docker Compose for one-command local startup
- Placement engine using `Filter -> Score -> Bind`
- Workload lifecycle: `REQUESTED -> RUNNING -> DEGRADED -> MIGRATING -> RUNNING`
- Incident simulation for overloaded or failed cells
- Rebalance recommendations and executable migrations
- Monitor-only reconciler that continuously checks for pending recovery actions
- Operational metrics for placement, rejection, migration, incident, and audit activity
- Capacity risk insight and control-plane audit timeline
- Unit and integration tests
- Architecture notes, testing guide, release notes, demo script, and smoke test

## Current Status

- V1 backend implemented with Spring Boot.
- Core fleet, workload, placement, incident, audit, and insight domain model implemented.
- Placement engine supports `BEST_FIT`, `LEAST_ALLOCATED`, and `BALANCED` strategies.
- REST APIs expose seeded cells, workload creation, workload placement, and placement history.
- React dashboard shows fleet health, workload creation, placement outcomes, incidents, and rebalance recommendations.
- JDBC repositories persist fleet, workload, placement, and incident state.
- Audit events capture workload, placement, simulation, and rebalance actions for a control-plane timeline.
- Rebalance execution history records migration status and supports capacity-safe rollback.
- Capacity insights compute a live risk score from utilization, incidents, degraded workloads, and recommended moves.
- The reconciler exposes current control-loop status and can be run on demand from the dashboard.
- Operational metrics expose placement decisions, rejected candidates, migrations, incidents, and recent audit events.
- Docker Compose runs PostgreSQL, the Spring Boot backend, and the production dashboard together.
- Frontend and backend test suites run locally and in GitHub Actions.
- Unit and integration tests cover placement scoring, filtering, API flow, and no-capacity failure behavior.

## V1 Proof Commands

Run the full automated verification suite:

```bash
make test
```

Run the end-to-end smoke test after the backend is live:

```bash
make smoke
```

Project proof docs:

- [Architecture notes](docs/architecture.md)
- [Demo script](docs/demo-script.md)
- [Testing guide](docs/testing.md)
- [V1 release notes](docs/v1-release.md)

## Core Domain

Stratus Lite manages a simulated fleet:

- Tenant: customer or team requesting workloads
- Workload: database-like resource request with CPU, memory, storage, IOPS, tier, and region
- Cell: simulated host node with finite capacity
- Placement decision: selected cell plus scored alternatives and explanation
- Incident: overloaded cell, failed cell, or SLO/capacity violation
- Rebalance plan: suggested workload migrations to reduce risk

## Placement Engine

The placement engine follows a scheduler-style pipeline:

1. Filter cells that cannot host the workload.
2. Score feasible cells using configurable strategies.
3. Bind the workload to the best cell and reserve capacity.

Initial scoring strategies:

- Best fit: pack workloads tightly to improve utilization.
- Least allocated: spread workloads to preserve headroom.
- Balanced: prefer cells where CPU, memory, storage, and IOPS remain balanced.

Each placement stores an explanation so the dashboard can show why a cell was chosen.

## Stack

- Java 21
- Spring Boot
- PostgreSQL
- H2 for lightweight local/test runs
- React 19
- TypeScript
- Vite
- pnpm
- Docker Compose
- JUnit 5
- React Testing Library
- GitHub Actions

## Local Java Setup

This repo expects Java 21 for backend development.

```bash
./scripts/use-java-21.sh mvn -Dmaven.repo.local=.m2/repository -f backend/pom.xml test
```

By default, the backend uses an in-memory H2 database for fast local development and automated tests.
For direct local development it listens on `http://localhost:8081` to avoid common `8080` port conflicts.

## One-Command Docker Demo

Run the full local stack with persistent PostgreSQL:

```bash
docker compose up --build
```

Then open:

- Dashboard: `http://localhost:5173`
- Backend API: `http://localhost:8080/api/cells`
- PostgreSQL: `localhost:5432`, database `stratus_lite`, user `stratus`, password `stratus`

Stop the stack:

```bash
docker compose down
```

Reset persisted demo data:

```bash
docker compose down -v
```

## Backend API

Run the backend:

```bash
./scripts/use-java-21.sh mvn -Dmaven.repo.local=.m2/repository -f backend/pom.xml spring-boot:run
```

Then verify it is serving Stratus data:

```bash
curl http://localhost:8081/api/cells
```

Core endpoints:

- `GET /api/cells` - list seeded cells with capacity and utilization.
- `GET /api/workloads` - list workload requests and lifecycle state.
- `POST /api/workloads` - create a workload request.
- `POST /api/workloads/{workloadId}/place?strategy=BEST_FIT` - place a workload using a strategy.
- `GET /api/placements` - list placement decisions and candidate score explanations.
- `POST /api/simulations/load-spike` - apply synthetic load to a cell and create an incident if it crosses the overload threshold.
- `POST /api/simulations/cell-failure` - mark a cell down and degrade assigned workloads.
- `GET /api/incidents` - list simulated operational incidents.
- `GET /api/insights/capacity` - summarize fleet health, risk score, utilization pressure, and migration pressure.
- `GET /api/events?limit=25` - list recent control-plane audit events.
- `GET /api/rebalance/recommendations` - recommend workload moves away from overloaded or down cells.
- `POST /api/rebalance/executions` - execute a recommended migration and move capacity between cells.
- `GET /api/rebalance/executions` - list migration execution history and rollback status.
- `POST /api/rebalance/executions/{executionId}/rollback` - roll back an active migration if the source cell is healthy and has capacity.
- `GET /api/reconciler/status` - show the latest monitor-only control-loop decision.
- `POST /api/reconciler/run` - run one reconciler sweep and record the decision in the audit timeline.
- `GET /api/metrics/operations` - expose operational metrics for placements, rejected candidates, migrations, incidents, and audit events.

Example workload request:

```json
{
  "tenantId": "tenant-alpha",
  "region": "us-east",
  "tier": "STANDARD",
  "demand": {
    "cpuCores": 2,
    "memoryGb": 4,
    "storageGb": 50,
    "iops": 1000
  }
}
```

## Frontend Dashboard

Install frontend dependencies:

```bash
pnpm --dir frontend install
```

Run the dashboard:

```bash
pnpm --dir frontend dev
```

In dev mode the dashboard calls `http://localhost:8081/api` directly, so start the backend first for live data.
If you intentionally run the backend on a different port, start Vite with `VITE_STRATUS_API_BASE_URL=http://localhost:<port>/api`.

The dashboard includes:

- A beginner-friendly quick start and guided failure drill.
- Fleet, workload, placement, incident, rebalance, migration, and risk panels.
- A monitor-only reconciler card that explains the current control-loop decision.
- Operational metrics cards for placement, rejection, migration, and audit activity.

Verify frontend changes:

```bash
pnpm --dir frontend test
pnpm --dir frontend build
```

## Demo Flow

1. Reset the demo state.
2. Create and place a workload.
3. Simulate a cell failure or load spike.
4. Watch the reconciler report `ACTION_REQUIRED`.
5. Review the rebalance recommendation and explanation.
6. Execute the migration.
7. Verify migration history, metrics, audit events, and capacity risk.

## Demo Script

Use the dedicated V1 walkthrough in [docs/demo-script.md](docs/demo-script.md).

## Design Notes

- The reconciler is monitor-only by design. It detects drift and recommends action, while migration execution remains explicit and auditable.
- Placement decisions are explainable. The API returns accepted and rejected candidate cells with policy summaries.
- The app favors local repeatability over external integrations, which keeps the demo free and easy to run.
