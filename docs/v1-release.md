# V1 Release Notes

## Completed Scope

- Modular Spring Boot backend.
- React and TypeScript dashboard.
- PostgreSQL persistence with H2 local/test fallback.
- Docker Compose stack for PostgreSQL, backend, and frontend.
- Capacity-aware placement engine with explainable scoring.
- Workload lifecycle: request, place, degrade, restore, and run.
- Incident simulation for load spikes and cell failures.
- Rebalance recommendations and executable migrations.
- Persisted rebalance execution history with rollback for active migrations.
- Capacity risk insight endpoint and dashboard panel.
- Persisted audit timeline.
- Backend and frontend automated tests.
- GitHub Actions CI.
- Demo script and smoke-test script.

## Known V1 Limits

- No authentication or multi-user authorization.
- No Kubernetes deployment manifests.
- No Prometheus/Grafana stack yet.
- No distributed locking because V1 runs as a modular monolith.
- No real cloud provider integration; all fleet state is simulated locally.

## Suggested V2 Roadmap

- Add authenticated tenants and role-based operations.
- Add Prometheus metrics and Grafana dashboard.
- Add OpenTelemetry traces around placement and migration flows.
- Add batch placement and bin-packing comparisons.
- Add Kubernetes manifests for local kind/minikube deployment.
