import { FormEvent, ReactNode, useCallback, useEffect, useMemo, useState } from "react";
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Database,
  Loader2,
  MoveRight,
  RefreshCw,
  Server,
  ShieldAlert,
  Zap
} from "lucide-react";
import {
  api,
  Cell,
  ControlPlaneEvent,
  CreateWorkloadPayload,
  Incident,
  Placement,
  PlacementStrategy,
  RebalanceRecommendation,
  ServiceTier,
  Workload
} from "./api";

const defaultWorkload: CreateWorkloadPayload = {
  tenantId: "tenant-alpha",
  region: "us-east",
  tier: "STANDARD",
  demand: {
    cpuCores: 2,
    memoryGb: 4,
    storageGb: 50,
    iops: 1000
  }
};

export function App() {
  const [cells, setCells] = useState<Cell[]>([]);
  const [workloads, setWorkloads] = useState<Workload[]>([]);
  const [placements, setPlacements] = useState<Placement[]>([]);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [events, setEvents] = useState<ControlPlaneEvent[]>([]);
  const [recommendations, setRecommendations] = useState<RebalanceRecommendation[]>([]);
  const [form, setForm] = useState<CreateWorkloadPayload>(defaultWorkload);
  const [strategy, setStrategy] = useState<PlacementStrategy>("LEAST_ALLOCATED");
  const [selectedWorkloadId, setSelectedWorkloadId] = useState("");
  const [selectedCellId, setSelectedCellId] = useState("cell-use1-a");
  const [isLoading, setIsLoading] = useState(true);
  const [isMutating, setIsMutating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadDashboard = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [nextCells, nextWorkloads, nextPlacements, nextIncidents, nextEvents, nextRecommendations] = await Promise.all([
        api.cells(),
        api.workloads(),
        api.placements(),
        api.incidents(),
        api.events(),
        api.recommendations()
      ]);

      setCells(nextCells);
      setWorkloads(nextWorkloads);
      setPlacements(nextPlacements);
      setIncidents(nextIncidents);
      setEvents(nextEvents);
      setRecommendations(nextRecommendations);
      if (!selectedCellId && nextCells.length > 0) {
        setSelectedCellId(nextCells[0].id);
      }
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : "Could not load dashboard data");
    } finally {
      setIsLoading(false);
    }
  }, [selectedCellId]);

  useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  const requestedWorkloads = useMemo(
    () => workloads.filter((workload) => workload.state === "REQUESTED"),
    [workloads]
  );

  const latestPlacement = placements.at(-1);
  const criticalIncidents = incidents.filter((incident) => incident.severity === "CRITICAL").length;
  const activeCells = cells.filter((cell) => cell.status === "ACTIVE").length;
  const activeWorkloads = workloads.filter((workload) => ["PLACED", "RUNNING"].includes(workload.state)).length;

  async function mutate(action: () => Promise<unknown>, successMessage: string) {
    setIsMutating(true);
    setError(null);
    setNotice(null);
    try {
      await action();
      setNotice(successMessage);
      await loadDashboard();
    } catch (nextError) {
      setError(nextError instanceof Error ? nextError.message : "Action failed");
    } finally {
      setIsMutating(false);
    }
  }

  async function handleCreateWorkload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await mutate(async () => {
      const workload = await api.createWorkload(form);
      setSelectedWorkloadId(workload.id);
    }, "Workload request created");
  }

  async function handlePlaceWorkload() {
    if (!selectedWorkloadId) {
      setError("Choose a requested workload before placing it");
      return;
    }
    await mutate(() => api.placeWorkload(selectedWorkloadId, strategy), "Placement decision created");
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Stratus Lite Control Plane</p>
          <h1>Capacity and workload placement dashboard</h1>
        </div>
        <button className="icon-button" onClick={() => void loadDashboard()} disabled={isLoading} title="Refresh dashboard">
          <RefreshCw size={18} />
          Refresh
        </button>
      </header>

      {error && (
        <div className="banner banner-error" role="alert">
          <ShieldAlert size={18} />
          {error}
        </div>
      )}
      {notice && (
        <div className="banner banner-success">
          <CheckCircle2 size={18} />
          {notice}
        </div>
      )}

      {isLoading ? (
        <section className="loading-state">
          <Loader2 className="spin" size={28} />
          Loading control plane state
        </section>
      ) : (
        <>
          <section className="metric-grid" aria-label="Control plane summary">
            <Metric icon={<Server size={20} />} label="Active cells" value={`${activeCells}/${cells.length}`} />
            <Metric icon={<Database size={20} />} label="Active workloads" value={activeWorkloads.toString()} />
            <Metric icon={<AlertTriangle size={20} />} label="Open incidents" value={incidents.length.toString()} />
            <Metric icon={<Activity size={20} />} label="Audit events" value={events.length.toString()} />
          </section>

          <section className="workspace-grid">
            <section className="panel fleet-panel">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Fleet</p>
                  <h2>Cell utilization</h2>
                </div>
                <span className={criticalIncidents > 0 ? "status-pill danger" : "status-pill ok"}>
                  {criticalIncidents > 0 ? `${criticalIncidents} critical` : "healthy"}
                </span>
              </div>
              <div className="cell-grid">
                {cells.map((cell) => (
                  <button
                    key={cell.id}
                    className={`cell-tile ${cell.status.toLowerCase()} ${selectedCellId === cell.id ? "selected" : ""}`}
                    onClick={() => setSelectedCellId(cell.id)}
                  >
                    <span className="cell-name">{cell.id}</span>
                    <span>{cell.region} · {cell.tier}</span>
                    <span className="progress-track">
                      <span style={{ width: `${Math.min(cell.utilizationPercent, 100)}%` }} />
                    </span>
                    <strong>{cell.utilizationPercent.toFixed(1)}%</strong>
                  </button>
                ))}
              </div>
            </section>

            <section className="panel actions-panel">
              <div className="section-heading">
                <div>
                  <p className="eyebrow">Actions</p>
                  <h2>Operate the fleet</h2>
                </div>
              </div>
              <form className="workload-form" onSubmit={(event) => void handleCreateWorkload(event)}>
                <label>
                  Tenant
                  <input value={form.tenantId} onChange={(event) => setForm({ ...form, tenantId: event.target.value })} />
                </label>
                <label>
                  Region
                  <select value={form.region} onChange={(event) => setForm({ ...form, region: event.target.value })}>
                    <option value="us-east">us-east</option>
                    <option value="us-west">us-west</option>
                  </select>
                </label>
                <label>
                  Tier
                  <select value={form.tier} onChange={(event) => setForm({ ...form, tier: event.target.value as ServiceTier })}>
                    <option value="BASIC">BASIC</option>
                    <option value="STANDARD">STANDARD</option>
                    <option value="PREMIUM">PREMIUM</option>
                  </select>
                </label>
                <div className="resource-row">
                  <NumberField label="CPU" value={form.demand.cpuCores} onChange={(value) => setForm({ ...form, demand: { ...form.demand, cpuCores: value } })} />
                  <NumberField label="Memory" value={form.demand.memoryGb} onChange={(value) => setForm({ ...form, demand: { ...form.demand, memoryGb: value } })} />
                  <NumberField label="Storage" value={form.demand.storageGb} onChange={(value) => setForm({ ...form, demand: { ...form.demand, storageGb: value } })} />
                  <NumberField label="IOPS" value={form.demand.iops} onChange={(value) => setForm({ ...form, demand: { ...form.demand, iops: value } })} />
                </div>
                <button className="primary-button" disabled={isMutating}>
                  <Database size={18} />
                  Create workload
                </button>
              </form>

              <div className="operator-actions">
                <label>
                  Requested workload
                  <select value={selectedWorkloadId} onChange={(event) => setSelectedWorkloadId(event.target.value)}>
                    <option value="">Select workload</option>
                    {requestedWorkloads.map((workload) => (
                      <option key={workload.id} value={workload.id}>{workload.id}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Strategy
                  <select value={strategy} onChange={(event) => setStrategy(event.target.value as PlacementStrategy)}>
                    <option value="BEST_FIT">BEST_FIT</option>
                    <option value="LEAST_ALLOCATED">LEAST_ALLOCATED</option>
                    <option value="BALANCED">BALANCED</option>
                  </select>
                </label>
                <button className="secondary-button" onClick={() => void handlePlaceWorkload()} disabled={isMutating}>
                  <MoveRight size={18} />
                  Place workload
                </button>
                <button className="secondary-button" onClick={() => void mutate(() => api.simulateLoadSpike(selectedCellId), "Load spike simulated")} disabled={isMutating || !selectedCellId}>
                  <Zap size={18} />
                  Spike selected cell
                </button>
                <button className="danger-button" onClick={() => void mutate(() => api.simulateCellFailure(selectedCellId), "Cell failure simulated")} disabled={isMutating || !selectedCellId}>
                  <AlertTriangle size={18} />
                  Fail selected cell
                </button>
              </div>
            </section>
          </section>

          <section className="workspace-grid lower-grid">
            <DataPanel title="Workloads" eyebrow="Lifecycle">
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>Tenant</th>
                      <th>State</th>
                      <th>Cell</th>
                    </tr>
                  </thead>
                  <tbody>
                    {workloads.map((workload) => (
                      <tr key={workload.id}>
                        <td>{shortId(workload.id)}</td>
                        <td>{workload.tenantId}</td>
                        <td><span className={`state-badge ${workload.state.toLowerCase()}`}>{workload.state}</span></td>
                        <td>{workload.assignedCellId ?? "unplaced"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </DataPanel>

            <DataPanel title="Latest placement" eyebrow="Filter Score Bind">
              {latestPlacement ? (
                <div className="placement-detail">
                  <p>{latestPlacement.explanation}</p>
                  <div className="candidate-list">
                    {latestPlacement.candidates.map((candidate) => (
                      <div key={candidate.cellId} className="candidate-row">
                        <span>{candidate.cellId}</span>
                        <strong>{candidate.score.toFixed(2)}</strong>
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <p className="empty-copy">No placement decisions yet.</p>
              )}
            </DataPanel>

            <DataPanel title="Incidents" eyebrow="Reliability">
              <div className="stack-list">
                {incidents.length === 0 ? (
                  <p className="empty-copy">No incidents recorded.</p>
                ) : incidents.map((incident) => (
                  <div key={incident.id} className={`incident-item ${incident.severity.toLowerCase()}`}>
                    <Activity size={17} />
                    <div>
                      <strong>{incident.type}</strong>
                      <span>{incident.message}</span>
                    </div>
                  </div>
                ))}
              </div>
            </DataPanel>

            <DataPanel title="Audit events" eyebrow="Timeline">
              <div className="stack-list">
                {events.length === 0 ? (
                  <p className="empty-copy">No events recorded.</p>
                ) : events.map((event) => (
                  <div key={event.id} className={`event-item ${event.severity.toLowerCase()}`}>
                    <Activity size={17} />
                    <div>
                      <strong>{event.type}</strong>
                      <span>{event.message}</span>
                    </div>
                  </div>
                ))}
              </div>
            </DataPanel>

            <DataPanel title="Rebalance" eyebrow="Recommendations">
              <div className="stack-list">
                {recommendations.length === 0 ? (
                  <p className="empty-copy">No moves recommended.</p>
                ) : recommendations.map((recommendation) => (
                  <div key={`${recommendation.workloadId}-${recommendation.targetCellId}`} className="move-item">
                    <span className="priority">{recommendation.priority}</span>
                    <div>
                      <strong>{shortId(recommendation.workloadId)}</strong>
                      <span>{recommendation.sourceCellId} → {recommendation.targetCellId}</span>
                    </div>
                    <button
                      className="mini-button"
                      onClick={() => void mutate(
                        () => api.executeRebalance(recommendation),
                        "Rebalance migration executed"
                      )}
                      disabled={isMutating}
                    >
                      <MoveRight size={15} />
                      Execute
                    </button>
                  </div>
                ))}
              </div>
            </DataPanel>
          </section>
        </>
      )}
    </main>
  );
}

function Metric({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <article className="metric-card">
      {icon}
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function DataPanel({ eyebrow, title, children }: { eyebrow: string; title: string; children: ReactNode }) {
  return (
    <section className="panel data-panel">
      <div className="section-heading">
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h2>{title}</h2>
        </div>
      </div>
      {children}
    </section>
  );
}

function NumberField({ label, value, onChange }: { label: string; value: number; onChange: (value: number) => void }) {
  return (
    <label>
      {label}
      <input type="number" min={0} value={value} onChange={(event) => onChange(Number(event.target.value))} />
    </label>
  );
}

function shortId(id: string) {
  return id.length > 12 ? `${id.slice(0, 10)}…` : id;
}
