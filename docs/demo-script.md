# Demo Script

This demo path shows the project as a local cloud control plane rather than a CRUD app.

## Setup

Start the full stack:

```bash
docker compose up --build
```

Open the dashboard:

```text
http://localhost:5173
```

For repeatable demos, reset persisted state first:

```bash
docker compose down -v
docker compose up --build
```

## Walkthrough

1. Show the fleet tiles and explain that each cell has finite CPU, memory, storage, and IOPS capacity.
2. Create a workload request for `tenant-alpha` in `us-east` with `STANDARD` tier.
3. Place the workload using `LEAST_ALLOCATED`.
4. Open the latest placement panel and explain `Filter -> Score -> Bind`.
5. Select the placed cell and trigger a cell failure.
6. Show the incident panel, degraded workload state, and capacity risk panel.
7. Show the autonomous loop card reporting `ACTION_REQUIRED`.
8. Run a reconciler check and explain that it is monitor-only, so it detects work but does not move workloads without approval.
9. Show the rebalance recommendation from the failed source cell to a healthy target cell.
10. Execute the migration.
11. Show the workload moving to `RUNNING`, the source/target cell capacity changes, and recommendations clearing.
12. Show the migration history entry with `ACTIVE` status.
13. Show operational metrics for placement decisions, rejected candidates, migrations, incidents, and audit events.
14. For a load-spike demo where the source cell remains healthy, roll back the migration and show capacity moving back.
15. Show the audit timeline proving the end-to-end control-plane sequence.

## Command-Line Smoke Test

After the backend is running:

```bash
./scripts/smoke-test.sh
```

Against a custom backend URL:

```bash
BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh
```
