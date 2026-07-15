# Testing Guide

## Full Verification

```bash
make test
```

This runs:

- Backend unit and integration tests.
- Frontend React tests.
- Frontend TypeScript and production build checks.

## Backend

```bash
./scripts/use-java-21.sh mvn -Dmaven.repo.local=.m2/repository -f backend/pom.xml test
```

Coverage includes:

- Placement scoring and filtering.
- Workload creation and placement API flow.
- No-capacity conflict behavior.
- Load spike incidents and rebalance recommendations.
- Cell failure degradation and restore recommendation.
- Executable rebalance migration.
- Migration history and capacity-safe rollback.
- Audit events.
- Capacity risk insight.
- Monitor-only reconciler status and manual sweep.
- Operational metrics for placement, migration, incident, and audit activity.

## Frontend

```bash
pnpm --dir frontend test
pnpm --dir frontend build
```

Coverage includes:

- Dashboard rendering.
- Placement action.
- Rebalance execution action.
- Rebalance rollback action.
- Reconciler check action.

## Smoke Test

Run this after the backend is live:

```bash
./scripts/smoke-test.sh
```

The smoke test targets `http://localhost:8081` by default. It resets demo state, creates and places a workload, simulates a source-cell failure, verifies the reconciler reports `ACTION_REQUIRED`, executes the recommended migration, checks capacity insight and operational metrics, and verifies audit events.

For repeatability against Docker Compose:

```bash
docker compose down -v
docker compose up --build
BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh
```
