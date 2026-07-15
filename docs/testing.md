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
- Audit events.
- Capacity risk insight.

## Frontend

```bash
pnpm --dir frontend test
pnpm --dir frontend build
```

Coverage includes:

- Dashboard rendering.
- Placement action.
- Rebalance execution action.

## Smoke Test

Run this after the backend is live:

```bash
./scripts/smoke-test.sh
```

The smoke test creates and places a workload, simulates a source-cell failure, executes the recommended migration, checks capacity insight, and verifies audit events.

For repeatability against Docker Compose:

```bash
docker compose down -v
docker compose up --build
./scripts/smoke-test.sh
```
