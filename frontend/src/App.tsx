import { FormEvent, ReactNode, useCallback, useEffect, useMemo, useState } from "react";
import {
  Activity,
  AlertTriangle,
  Gauge,
  CheckCircle2,
  Database,
  History,
  Loader2,
  MoveRight,
  RefreshCw,
  Server,
  ShieldAlert,
  Undo2,
  Zap
} from "lucide-react";
import {
  api,
  Cell,
  CapacityInsight,
  ControlPlaneEvent,
  CreateWorkloadPayload,
  Incident,
  Placement,
  PlacementStrategy,
  RebalanceExecutionRecord,
  RebalanceRecommendation,
  ServiceTier,
  STRATUS_API_BASE_URL,
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

const drillSteps = [
  {
    title: "Reset",
    copy: "Start with the same clean fleet every time."
  },
  {
    title: "Create workload",
    copy: "Create one app request that needs capacity."
  },
  {
    title: "Place",
    copy: "Pick the best cell for that workload."
  },
  {
    title: "Fail cell",
    copy: "Break the cell that is running the workload."
  },
  {
    title: "Rebalance",
    copy: "Move the workload to a safer cell."
  }
];

const storySections = [
  { id: "quickstart-section", step: "01", label: "Start" },
  { id: "overview-section", step: "02", label: "Overview" },
  { id: "operate-section", step: "03", label: "Operate" },
  { id: "rebalance-section", step: "04", label: "Rebalance" },
  { id: "observe-section", step: "05", label: "Observe" }
];

export function App() {
  const [cells, setCells] = useState<Cell[]>([]);
  const [workloads, setWorkloads] = useState<Workload[]>([]);
  const [placements, setPlacements] = useState<Placement[]>([]);
  const [incidents, setIncidents] = useState<Incident[]>([]);
  const [capacityInsight, setCapacityInsight] = useState<CapacityInsight | null>(null);
  const [events, setEvents] = useState<ControlPlaneEvent[]>([]);
  const [recommendations, setRecommendations] = useState<RebalanceRecommendation[]>([]);
  const [executions, setExecutions] = useState<RebalanceExecutionRecord[]>([]);
  const [form, setForm] = useState<CreateWorkloadPayload>(defaultWorkload);
  const [strategy, setStrategy] = useState<PlacementStrategy>("LEAST_ALLOCATED");
  const [selectedWorkloadId, setSelectedWorkloadId] = useState("");
  const [selectedCellId, setSelectedCellId] = useState("cell-use1-a");
  const [isLoading, setIsLoading] = useState(true);
  const [isMutating, setIsMutating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [backendStatus, setBackendStatus] = useState("Checking backend");
  const [lastAction, setLastAction] = useState("No action yet");
  const [lastClick, setLastClick] = useState("No click captured yet");
  const [guidedStepIndex, setGuidedStepIndex] = useState(0);
  const [guidedDrillStatus, setGuidedDrillStatus] = useState("Ready to run a guided failure drill");

  const loadDashboard = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const [
        nextCells,
        nextWorkloads,
        nextPlacements,
        nextIncidents,
        nextCapacityInsight,
        nextEvents,
        nextRecommendations,
        nextExecutions
      ] = await Promise.all([
        api.cells(),
        api.workloads(),
        api.placements(),
        api.incidents(),
        api.capacityInsight(),
        api.events(),
        api.recommendations(),
        api.executions()
      ]);

      setCells(nextCells);
      setWorkloads(nextWorkloads);
      setPlacements(nextPlacements);
      setIncidents(nextIncidents);
      setCapacityInsight(nextCapacityInsight);
      setEvents(nextEvents);
      setRecommendations(nextRecommendations);
      setExecutions(nextExecutions);
      if (!selectedCellId && nextCells.length > 0) {
        setSelectedCellId(nextCells[0].id);
      }
      setSelectedWorkloadId((currentWorkloadId) => {
        const requested = nextWorkloads.filter((workload) => workload.state === "REQUESTED");
        if (currentWorkloadId && requested.some((workload) => workload.id === currentWorkloadId)) {
          return currentWorkloadId;
        }
        return requested.at(-1)?.id ?? "";
      });
      setBackendStatus(`Connected to ${STRATUS_API_BASE_URL}`);
    } catch (nextError) {
      const message = nextError instanceof Error ? nextError.message : "Could not load dashboard data";
      setError(message);
      setBackendStatus(`Backend error: ${message}`);
    } finally {
      setIsLoading(false);
    }
  }, [selectedCellId]);

  useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  useEffect(() => {
    function trackPointerDown(event: PointerEvent) {
      const target = event.target instanceof HTMLElement ? event.target : null;
      const button = target?.closest("button");
      const label = button?.textContent?.trim().replace(/\s+/g, " ") || target?.textContent?.trim().slice(0, 60) || "page";
      setLastClick(`${label} at ${new Date().toLocaleTimeString()}`);
    }

    window.addEventListener("pointerdown", trackPointerDown, true);
    return () => window.removeEventListener("pointerdown", trackPointerDown, true);
  }, []);

  const requestedWorkloads = useMemo(
    () => workloads.filter((workload) => workload.state === "REQUESTED"),
    [workloads]
  );

  const latestPlacement = placements.at(-1);
  const criticalIncidents = incidents.filter((incident) => incident.severity === "CRITICAL").length;
  const activeCells = cells.filter((cell) => cell.status === "ACTIVE").length;
  const activeWorkloads = workloads.filter((workload) => ["PLACED", "RUNNING"].includes(workload.state)).length;
  const activeExecutions = executions.filter((execution) => execution.status === "ACTIVE");
  const hasRecommendedMoves = recommendations.length > 0;
  const hasRollbackCandidates = activeExecutions.length > 0;
  const selectedCell = cells.find((cell) => cell.id === selectedCellId);
  const placedWorkloads = workloads.filter((workload) => workload.assignedCellId);
  const nextStepTitle = hasRecommendedMoves
    ? `${recommendations.length} rebalance ${recommendations.length === 1 ? "move" : "moves"} ready`
    : hasRollbackCandidates
      ? `${activeExecutions.length} active ${activeExecutions.length === 1 ? "migration" : "migrations"}`
      : "Fleet steady";
  const nextStepDetail = hasRecommendedMoves
    ? "Execute a recommended migration to move risk away from the source cell."
    : hasRollbackCandidates
      ? "Rollback is available for active migrations while the source cell remains healthy."
      : "Create workload, spike a cell, or fail a cell to generate operational movement.";
  const beginnerSummary = hasRecommendedMoves
    ? "A workload is sitting on a risky cell. The system has found a safer cell and is waiting for you to move it."
    : hasRollbackCandidates
      ? "A workload was moved. Rollback means moving it back if the new move was not wanted."
      : activeWorkloads > 0
        ? "Workloads are placed and the fleet has no urgent move to make right now."
        : "Start by creating a workload. Think of it as asking the platform to run an app.";

  function scrollToPanel(panelId: string) {
    document.getElementById(panelId)?.scrollIntoView?.({ behavior: "smooth", block: "start" });
  }

  async function mutate(action: () => Promise<unknown>, successMessage: string) {
    setIsMutating(true);
    setError(null);
    setNotice(null);
    setLastAction(`Running: ${successMessage}`);
    try {
      await action();
      setNotice(successMessage);
      setLastAction(successMessage);
      await loadDashboard();
    } catch (nextError) {
      const message = nextError instanceof Error ? nextError.message : "Action failed";
      setError(message);
      setLastAction(`Failed: ${message}`);
    } finally {
      setIsMutating(false);
    }
  }

  async function createWorkload() {
    let createdWorkloadId = "";
    await mutate(async () => {
      const workload = await api.createWorkload(form);
      createdWorkloadId = workload.id;
      setSelectedWorkloadId(workload.id);
    }, "Workload request created");
    return createdWorkloadId;
  }

  async function handleCreateWorkload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await createWorkload();
  }

  async function handlePlaceWorkload() {
    if (!selectedWorkloadId) {
      setError("Choose a requested workload before placing it");
      setLastAction("Place workload blocked: choose a requested workload");
      return;
    }
    await mutate(() => api.placeWorkload(selectedWorkloadId, strategy), "Placement decision created");
  }

  async function handleCreateAndPlaceWorkload() {
    const workloadId = await createWorkload();
    if (!workloadId) {
      return;
    }
    await mutate(() => api.placeWorkload(workloadId, strategy), "Workload created and placed");
  }

  async function handleRunFailureDrill() {
    setIsMutating(true);
    setError(null);
    setNotice(null);
    setGuidedStepIndex(1);
    setGuidedDrillStatus("Step 1 of 5: resetting the demo so the story starts clean.");
    setLastAction("Running guided failure drill");

    try {
      await api.resetDemo();

      setGuidedStepIndex(2);
      setGuidedDrillStatus("Step 2 of 5: creating a workload, like a small app asking for capacity.");
      const workload = await api.createWorkload(defaultWorkload);

      setGuidedStepIndex(3);
      setGuidedDrillStatus("Step 3 of 5: placing the workload on the best available cell.");
      const placement = await api.placeWorkload(workload.id, strategy);

      setGuidedStepIndex(4);
      setGuidedDrillStatus(`Step 4 of 5: failing ${placement.selectedCellId} so the system must react.`);
      await api.simulateCellFailure(placement.selectedCellId);

      setGuidedStepIndex(5);
      setGuidedDrillStatus("Step 5 of 5: executing the recommended rebalance move.");
      const nextRecommendations = await api.recommendations();
      const recommendation =
        nextRecommendations.find((candidate) => candidate.workloadId === workload.id) ?? nextRecommendations[0];
      if (!recommendation) {
        throw new Error("No rebalance recommendation was generated after the failure drill");
      }
      await api.executeRebalance(recommendation);

      setGuidedStepIndex(6);
      setGuidedDrillStatus("Complete: Stratus Lite recovered the workload onto a healthier cell.");
      setNotice("Guided failure drill completed");
      setLastAction("Guided drill completed: workload recovered on a healthier cell");
      await loadDashboard();
    } catch (nextError) {
      const message = nextError instanceof Error ? nextError.message : "Guided failure drill failed";
      setError(message);
      setGuidedDrillStatus(`Stopped: ${message}`);
      setLastAction(`Guided drill failed: ${message}`);
    } finally {
      setIsMutating(false);
    }
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Stratus Lite Control Plane</p>
          <h1>Capacity and workload placement dashboard</h1>
        </div>
        <div className="topbar-actions">
          <button className="icon-button" onClick={() => void loadDashboard()} disabled={isLoading} title="Refresh dashboard">
            <RefreshCw size={18} />
            Refresh
          </button>
          <button
            className="secondary-button"
            type="button"
            onClick={() => {
              setGuidedStepIndex(0);
              setGuidedDrillStatus("Ready to run a guided failure drill");
              void mutate(() => api.resetDemo(), "Demo state reset");
            }}
            disabled={isMutating}
          >
            <Undo2 size={18} />
            Reset demo
          </button>
        </div>
      </header>

      <section className="ops-strip" aria-live="polite">
        <span><strong>Backend</strong>{backendStatus}</span>
        <span><strong>Last click</strong>{lastClick}</span>
        <span><strong>Last action</strong>{lastAction}</span>
      </section>

      <nav className="story-nav" aria-label="Dashboard sections">
        {storySections.map((section) => (
          <button key={section.id} type="button" onClick={() => scrollToPanel(section.id)}>
            <span>{section.step}</span>
            <strong>{section.label}</strong>
          </button>
        ))}
      </nav>

      <section id="quickstart-section" className="quickstart-panel" aria-label="Quick start">
        <div className="quickstart-intro">
          <div>
            <p className="eyebrow">Quick start</p>
            <h2>What this dashboard does</h2>
            <p>
              Stratus Lite is a tiny cloud control plane. It decides where an app should run,
              notices when capacity becomes risky, and moves the app to a safer place.
            </p>
          </div>
          <button type="button" className="primary-button" onClick={() => void handleRunFailureDrill()} disabled={isMutating}>
            <Activity size={18} />
            Run failure drill
          </button>
        </div>

        <div className="concept-grid">
          <ConceptCard title="Cell" value={`${activeCells}/${cells.length} active`}>
            A pool of servers in one region and tier. If a cell goes down, apps on it need help.
          </ConceptCard>
          <ConceptCard title="Workload" value={`${workloads.length} total`}>
            An app or database request that needs CPU, memory, storage, and IOPS.
          </ConceptCard>
          <ConceptCard title="Placement" value={latestPlacement ? latestPlacement.selectedCellId : "none yet"}>
            The scheduler choosing the best cell for a workload.
          </ConceptCard>
          <ConceptCard title="Rebalance" value={`${recommendations.length} ready`}>
            A safe move away from a risky or failed cell.
          </ConceptCard>
        </div>

        <div className="guided-drill">
          <div>
            <p className="eyebrow">Demo path</p>
            <h2>{guidedDrillStatus}</h2>
            <p>{beginnerSummary}</p>
          </div>
          <ol className="drill-steps">
            {drillSteps.map((step, index) => (
              <li
                key={step.title}
                className={
                  guidedStepIndex === index + 1 ? "active" : guidedStepIndex > index + 1 ? "done" : ""
                }
              >
                <span>{index + 1}</span>
                <div>
                  <strong>{step.title}</strong>
                  <p>{step.copy}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>
      </section>

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
          <section id="overview-section" className="story-section" aria-label="Overview">
            <div className="story-heading">
              <span>02</span>
              <div>
                <p className="eyebrow">Overview</p>
                <h2>Read the current fleet state</h2>
              </div>
            </div>
            <section className="metric-grid" aria-label="Control plane summary">
              <Metric icon={<Server size={20} />} label="Active cells" value={`${activeCells}/${cells.length}`} />
              <Metric icon={<Database size={20} />} label="Active workloads" value={activeWorkloads.toString()} />
              <Metric icon={<AlertTriangle size={20} />} label="Open incidents" value={incidents.length.toString()} />
              <Metric icon={<Gauge size={20} />} label="Risk score" value={capacityInsight?.riskScore.toString() ?? "0"} />
            </section>

            <section className={`workflow-cue ${hasRecommendedMoves || hasRollbackCandidates ? "needs-action" : "steady"}`}>
              <div>
                <p className="eyebrow">Operational next step</p>
                <h2>{nextStepTitle}</h2>
                <p>{nextStepDetail}</p>
                <p className="helper-copy">{beginnerSummary}</p>
              </div>
              <div className="workflow-cue-actions">
                <button
                  type="button"
                  className={hasRecommendedMoves ? "primary-button" : "secondary-button"}
                  onClick={() => scrollToPanel("rebalance-panel")}
                >
                  <MoveRight size={18} />
                  Rebalance
                </button>
                <button
                  type="button"
                  className={hasRollbackCandidates ? "primary-button" : "secondary-button"}
                  onClick={() => scrollToPanel("migrations-panel")}
                >
                  <Undo2 size={18} />
                  Migrations
                </button>
              </div>
            </section>
          </section>

          <section id="operate-section" className="story-section" aria-label="Operate">
            <div className="story-heading">
              <span>03</span>
              <div>
                <p className="eyebrow">Operate</p>
                <h2>Create workloads and test failures</h2>
              </div>
            </div>
            <div className="workspace-grid">
              <section className="panel fleet-panel">
                <div className="section-heading">
                  <div>
                  <p className="eyebrow">Fleet</p>
                  <h2>Cell utilization</h2>
                  <p className="panel-help">
                    Each card is a server pool. Select one to test a load spike or failure.
                    {selectedCell ? ` Current selection: ${selectedCell.id}.` : ""}
                  </p>
                </div>
                <span className={criticalIncidents > 0 ? "status-pill danger" : "status-pill ok"}>
                  {criticalIncidents > 0 ? `${criticalIncidents} critical` : "healthy"}
                </span>
              </div>
              <div className="cell-grid">
                {cells.map((cell) => (
                  <button
                    type="button"
                    key={cell.id}
                    className={`cell-tile ${cell.status.toLowerCase()} ${selectedCellId === cell.id ? "selected" : ""}`}
                    onClick={() => {
                      setSelectedCellId(cell.id);
                      setLastAction(`Selected ${cell.id}`);
                    }}
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
                  <p className="panel-help">
                    Create a workload to ask for capacity. Placement chooses where it should run.
                    {placedWorkloads.length > 0
                      ? ` ${placedWorkloads.length} ${placedWorkloads.length === 1 ? "workload is" : "workloads are"} already assigned to a cell.`
                      : ""}
                  </p>
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
                <button type="button" className="primary-button" onClick={() => void handleCreateAndPlaceWorkload()} disabled={isMutating}>
                  <Database size={18} />
                  Create workload
                </button>
                <button type="button" className="secondary-button" onClick={() => void createWorkload()} disabled={isMutating}>
                  <Database size={18} />
                  Create request only
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
                <button type="button" className="secondary-button" onClick={() => void handlePlaceWorkload()} disabled={isMutating}>
                  <MoveRight size={18} />
                  Place workload
                </button>
                <button type="button" className="secondary-button" onClick={() => void mutate(() => api.simulateLoadSpike(selectedCellId), "Load spike simulated")} disabled={isMutating || !selectedCellId}>
                  <Zap size={18} />
                  Spike selected cell
                </button>
                <button type="button" className="danger-button" onClick={() => void mutate(() => api.simulateCellFailure(selectedCellId), "Cell failure simulated")} disabled={isMutating || !selectedCellId}>
                  <AlertTriangle size={18} />
                  Fail selected cell
                </button>
              </div>
            </section>
            </div>
          </section>

          <section id="rebalance-section" className="story-section" aria-label="Rebalance">
            <div className="story-heading">
              <span>04</span>
              <div>
                <p className="eyebrow">Rebalance</p>
                <h2>Move work away from risk</h2>
              </div>
            </div>
            <section className="operations-grid">
              <DataPanel
                id="rebalance-panel"
                title="Rebalance"
                eyebrow="Recommendations"
                className={hasRecommendedMoves ? "attention-panel" : ""}
              >
                <div className="stack-list">
                  {recommendations.length === 0 ? (
                    <p className="empty-copy">
                      No moves recommended. The fleet is not asking you to move anything right now.
                    </p>
                  ) : recommendations.map((recommendation) => (
                    <div key={`${recommendation.workloadId}-${recommendation.targetCellId}`} className="move-item">
                      <span className="priority">{recommendation.priority}</span>
                      <div>
                        <strong>{shortId(recommendation.workloadId)}</strong>
                        <span>{recommendation.sourceCellId} → {recommendation.targetCellId}</span>
                        <p>{recommendation.reason}</p>
                        <p>{recommendation.explanation}</p>
                        <p><strong>Next:</strong> {recommendation.operatorAction}</p>
                      </div>
                      <button
                        type="button"
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

              <DataPanel
                id="migrations-panel"
                title="Migrations"
                eyebrow="History"
                className={hasRollbackCandidates ? "attention-panel" : ""}
              >
                <div className="stack-list">
                  {executions.length === 0 ? (
                    <p className="empty-copy">
                      No rebalance executions yet. After you execute a move, it appears here with rollback status.
                    </p>
                  ) : executions.map((execution) => (
                    <div key={execution.id} className="execution-item">
                      <History size={17} />
                      <div>
                        <strong>{shortId(execution.workloadId)}</strong>
                        <span>{execution.sourceCellId} → {execution.targetCellId}</span>
                        <p>{execution.explanation || migrationStory(execution)}</p>
                        <p><strong>Next:</strong> {execution.operatorAction || "Review migration history before taking another action."}</p>
                      </div>
                      <span className={`execution-status ${execution.status.toLowerCase()}`}>
                        {execution.status}
                      </span>
                      <button
                        type="button"
                        className="mini-button"
                        onClick={() => void mutate(
                          () => api.rollbackRebalance(execution.id),
                          "Rebalance migration rolled back"
                        )}
                        disabled={isMutating || execution.status !== "ACTIVE"}
                      >
                        <Undo2 size={15} />
                        Rollback
                      </button>
                    </div>
                  ))}
                </div>
              </DataPanel>
            </section>
          </section>

          <section id="observe-section" className="story-section" aria-label="Observe">
            <div className="story-heading">
              <span>05</span>
              <div>
                <p className="eyebrow">Observe</p>
                <h2>Inspect decisions and audit history</h2>
              </div>
            </div>
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

              <DataPanel title="Latest placement" eyebrow="Scheduler decision">
                {latestPlacement ? (
                  <div className="placement-detail">
                    <p>{latestPlacement.explanation}</p>
                    <p className="plain-explain">
                      The scheduler scored every possible cell, then picked the highest-scoring safe option.
                    </p>
                    <div className="candidate-list">
                      {latestPlacement.candidates.map((candidate) => (
                        <div key={candidate.cellId} className="candidate-row">
                          <span>{candidate.cellId}</span>
                          <span className={`eligibility-pill ${candidate.eligible ? "eligible" : "rejected"}`}>
                            {candidate.eligible ? "eligible" : "rejected"}
                          </span>
                          <strong>{candidate.eligible ? candidate.score.toFixed(2) : "blocked"}</strong>
                          <small>Projected util {candidate.projectedUtilizationPercent.toFixed(1)}%</small>
                          <p>{candidate.policySummary}</p>
                          <p>{candidate.reason}</p>
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
                        <p>{incidentStory(incident)}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </DataPanel>

              <DataPanel title="Capacity risk" eyebrow="Insights">
                {capacityInsight ? (
                  <div className="risk-panel">
                    <div className={`risk-score ${capacityInsight.riskLevel.toLowerCase()}`}>
                      <strong>{capacityInsight.riskScore}</strong>
                      <span>{capacityInsight.riskLevel}</span>
                    </div>
                    <p>{capacityInsight.summary}</p>
                    <p className="plain-explain">{capacityInsight.explanation}</p>
                    <p className="plain-explain"><strong>Next:</strong> {capacityInsight.operatorAction}</p>
                    <div className="risk-facts">
                      <span>Max util <strong>{capacityInsight.maxUtilizationPercent.toFixed(1)}%</strong></span>
                      <span>Overloaded <strong>{capacityInsight.overloadedCells}</strong></span>
                      <span>Down <strong>{capacityInsight.downCells}</strong></span>
                      <span>Moves <strong>{capacityInsight.recommendedMoves}</strong></span>
                    </div>
                  </div>
                ) : (
                  <p className="empty-copy">No capacity insight available.</p>
                )}
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
                        <p>{eventStory(event)}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </DataPanel>
            </section>
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

function ConceptCard({ title, value, children }: { title: string; value: string; children: ReactNode }) {
  return (
    <article className="concept-card">
      <div>
        <strong>{title}</strong>
        <span>{value}</span>
      </div>
      <p>{children}</p>
    </article>
  );
}

function DataPanel({
  eyebrow,
  title,
  children,
  id,
  className = ""
}: {
  eyebrow: string;
  title: string;
  children: ReactNode;
  id?: string;
  className?: string;
}) {
  return (
    <section id={id} className={`panel data-panel ${className}`.trim()}>
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

function eventStory(event: ControlPlaneEvent) {
  if (event.type.includes("PLACEMENT")) {
    return "The scheduler made a decision about where an app should run.";
  }
  if (event.type.includes("FAIL") || event.type.includes("DOWN")) {
    return "A cell became unsafe, so workloads on it may need to move.";
  }
  if (event.type.includes("REBALANCE") || event.type.includes("MIGRATION")) {
    return "The system moved or prepared to move work away from risk.";
  }
  if (event.type.includes("WORKLOAD")) {
    return "A workload changed state in the control plane.";
  }
  return "This is an audit record that helps operators understand what changed.";
}

function incidentStory(incident: Incident) {
  if (incident.type.includes("DOWN")) {
    return "This incident matters because workloads on a down cell cannot be trusted until they are restored elsewhere.";
  }
  if (incident.type.includes("OVERLOADED")) {
    return "This incident matters because a hot cell can become a reliability risk if more demand lands there.";
  }
  if (incident.type.includes("LOAD")) {
    return "This incident records a load change so operators can connect capacity pressure to later decisions.";
  }
  return "This incident explains a reliability signal the control plane is tracking.";
}

function migrationStory(execution: RebalanceExecutionRecord) {
  if (execution.status === "ROLLED_BACK") {
    return "This move was undone, so the workload was sent back to its previous cell.";
  }
  return "This workload was moved away from the source cell. Rollback is still available while the record is active.";
}

function shortId(id: string) {
  return id.length > 12 ? `${id.slice(0, 10)}…` : id;
}
