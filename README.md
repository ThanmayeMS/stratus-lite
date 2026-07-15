# Stratus Lite

Stratus Lite is a local-first cloud workload placement control plane built as a resume-grade SDE portfolio project.

It simulates how a cloud platform accepts workload requests, chooses the best cell/node for each workload, reserves capacity, tracks lifecycle state, detects overload, and recommends rebalancing actions.

## Why This Project

This project is designed to demonstrate practical SDE skills without requiring paid cloud infrastructure:

- Backend system design with Java and Spring Boot
- React + TypeScript dashboard development
- Relational data modeling with PostgreSQL
- Capacity-aware scheduling algorithms
- Workload lifecycle orchestration
- Incident simulation and rebalancing
- Local observability and metrics
- Test automation and CI
- Docker Compose based local development

## MVP Scope

The 3-day target is intentionally focused:

- One Spring Boot backend with clean internal modules
- One React + TypeScript dashboard
- PostgreSQL for durable state
- Optional Redis for caching/locks
- Docker Compose for one-command local startup
- Placement engine using `Filter -> Score -> Bind`
- Workload lifecycle: `REQUESTED -> PLACED -> RUNNING -> MIGRATING`
- Incident simulation for overloaded or failed cells
- Rebalance recommendations
- Unit and integration tests
- README screenshots, architecture notes, and demo script

## Current Status

- Backend project scaffolded with Spring Boot.
- Core fleet, workload, and placement domain model implemented.
- Placement engine supports `BEST_FIT`, `LEAST_ALLOCATED`, and `BALANCED` strategies.
- REST APIs expose seeded cells, workload creation, workload placement, and placement history.
- React dashboard shows fleet health, workload creation, placement outcomes, incidents, and rebalance recommendations.
- JDBC repositories persist fleet, workload, placement, and incident state.
- Audit events capture workload, placement, simulation, and rebalance actions for a control-plane timeline.
- Capacity insights compute a live risk score from utilization, incidents, degraded workloads, and recommended moves.
- Docker Compose runs PostgreSQL, the Spring Boot backend, and the production dashboard together.
- Frontend and backend test suites run locally and in GitHub Actions.
- Unit and integration tests cover placement scoring, filtering, API flow, and no-capacity failure behavior.

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

The Vite dev server proxies `/api` to `http://localhost:8080`, so start the backend first for live data.

Verify frontend changes:

```bash
pnpm --dir frontend test
pnpm --dir frontend build
```

## Three-Day Build Plan

### Day 1: Backend Foundation

- Create Spring Boot project structure.
- Model tenants, workloads, cells, placement decisions, and incidents.
- Add PostgreSQL schema and seed data.
- Implement placement engine with unit tests.
- Add REST APIs for creating workloads, placing workloads, listing fleet state, and simulating load.

### Day 2: Dashboard And Reliability

- Create React dashboard.
- Build fleet capacity view, workload table, placement explanation panel, and incident timeline.
- Add lifecycle state transitions.
- Add overload/failure simulation.
- Add rebalance recommendation logic.

### Day 3: Polish And Proof

- Add integration tests.
- Add Docker Compose.
- Add GitHub Actions CI.
- Add screenshots and a demo script.
- Write final resume bullets and architecture notes.

## Demo Script

1. Start the local stack.
2. Show seeded cells and capacity.
3. Submit a workload request.
4. Show placement score breakdown.
5. Simulate a load spike or cell failure.
6. Show incident creation.
7. Generate a rebalance recommendation.
8. Execute the migration and show workload/capacity state moving to the target cell.
9. Show the audit timeline for the full control-plane sequence.
10. Show the capacity risk score changing as incidents and migrations occur.

## Resume Bullets

- Built a local cloud workload placement control plane using Java, Spring Boot, React, PostgreSQL, and Docker Compose.
- Implemented a capacity-aware `Filter -> Score -> Bind` scheduler with explainable scoring strategies across CPU, memory, storage, and IOPS.
- Added workload lifecycle orchestration, overload simulation, incident detection, and executable rebalance migrations.
- Persisted an audit timeline for workload, placement, simulation, and migration actions.
- Built a capacity insight endpoint that scores fleet risk from utilization, incidents, degraded workloads, and migration pressure.
- Wrote automated tests for placement correctness, API behavior, and failure scenarios, with GitHub Actions CI.
