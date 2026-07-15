export type ServiceTier = "BASIC" | "STANDARD" | "PREMIUM";
export type CellStatus = "ACTIVE" | "DRAINING" | "DOWN";
export type WorkloadState = "REQUESTED" | "PLACED" | "RUNNING" | "MIGRATING" | "DEGRADED" | "DECOMMISSIONED";
export type PlacementStrategy = "BEST_FIT" | "LEAST_ALLOCATED" | "BALANCED";
export type IncidentSeverity = "INFO" | "WARNING" | "CRITICAL";
export type CapacityRiskLevel = "LOW" | "ELEVATED" | "HIGH" | "CRITICAL";
export type RebalanceExecutionStatus = "ACTIVE" | "ROLLED_BACK";

export interface ResourceVector {
  cpuCores: number;
  memoryGb: number;
  storageGb: number;
  iops: number;
}

export interface Cell {
  id: string;
  region: string;
  tier: ServiceTier;
  status: CellStatus;
  totalCapacity: ResourceVector;
  usedCapacity: ResourceVector;
  availableCapacity: ResourceVector;
  utilizationPercent: number;
}

export interface Workload {
  id: string;
  tenantId: string;
  region: string;
  tier: ServiceTier;
  demand: ResourceVector;
  state: WorkloadState;
  assignedCellId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CandidateScore {
  cellId: string;
  eligible: boolean;
  score: number;
  projectedUtilizationPercent: number;
  policySummary: string;
  reason: string;
}

export interface Placement {
  workloadId: string;
  selectedCellId: string;
  strategy: PlacementStrategy;
  explanation: string;
  decidedAt: string;
  candidates: CandidateScore[];
}

export interface Incident {
  id: string;
  type: string;
  severity: IncidentSeverity;
  cellId: string;
  message: string;
  createdAt: string;
}

export interface ControlPlaneEvent {
  id: string;
  type: string;
  severity: IncidentSeverity;
  subjectType: string;
  subjectId: string;
  message: string;
  createdAt: string;
}

export interface RebalanceRecommendation {
  workloadId: string;
  sourceCellId: string;
  targetCellId: string;
  strategy: PlacementStrategy;
  reason: string;
  explanation: string;
  operatorAction: string;
  priority: number;
}

export interface RebalanceExecutionResult {
  executionId: string;
  workloadId: string;
  sourceCellId: string;
  targetCellId: string;
  state: WorkloadState;
  status: RebalanceExecutionStatus;
  message: string;
  explanation: string;
  operatorAction: string;
}

export interface RebalanceExecutionRecord {
  id: string;
  workloadId: string;
  sourceCellId: string;
  targetCellId: string;
  status: RebalanceExecutionStatus;
  explanation: string;
  operatorAction: string;
  createdAt: string;
  rolledBackAt: string | null;
}

export interface CapacityInsight {
  totalCells: number;
  activeCells: number;
  drainingCells: number;
  downCells: number;
  overloadedCells: number;
  totalWorkloads: number;
  degradedWorkloads: number;
  openIncidents: number;
  criticalIncidents: number;
  recommendedMoves: number;
  maxUtilizationPercent: number;
  riskScore: number;
  riskLevel: CapacityRiskLevel;
  summary: string;
  explanation: string;
  operatorAction: string;
}

export interface SimulationResult {
  cellId: string;
  cellStatus: CellStatus;
  maxUtilizationPercent: number;
  affectedWorkloads: number;
  incident: Incident;
  explanation: string;
  operatorAction: string;
}

export interface CreateWorkloadPayload {
  tenantId: string;
  region: string;
  tier: ServiceTier;
  demand: ResourceVector;
}

export interface DemoResetResponse {
  message: string;
}

export const STRATUS_API_BASE_URL =
  import.meta.env.VITE_STRATUS_API_BASE_URL ?? (import.meta.env.DEV ? "http://localhost:8081/api" : "/api");

function apiUrl(path: string) {
  if (!path.startsWith("/api")) {
    return path;
  }
  return `${STRATUS_API_BASE_URL}${path.slice("/api".length)}`;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(apiUrl(path), {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options.headers
    }
  });

  if (!response.ok) {
    const error = await response.json().catch(() => null);
    if (response.status === 404 && path.startsWith("/api/")) {
      throw new Error(
        `Stratus backend did not answer ${path}. Start the Spring Boot backend on port 8081, then hard-refresh the dashboard.`
      );
    }
    throw new Error(error?.message ?? error?.detail ?? `Request failed with status ${response.status}`);
  }

  return response.json() as Promise<T>;
}

export const api = {
  cells: () => request<Cell[]>("/api/cells"),
  workloads: () => request<Workload[]>("/api/workloads"),
  placements: () => request<Placement[]>("/api/placements"),
  incidents: () => request<Incident[]>("/api/incidents"),
  capacityInsight: () => request<CapacityInsight>("/api/insights/capacity"),
  events: () => request<ControlPlaneEvent[]>("/api/events?limit=20"),
  recommendations: () => request<RebalanceRecommendation[]>("/api/rebalance/recommendations"),
  executions: () => request<RebalanceExecutionRecord[]>("/api/rebalance/executions"),
  executeRebalance: (recommendation: RebalanceRecommendation) =>
    request<RebalanceExecutionResult>("/api/rebalance/executions", {
      method: "POST",
      body: JSON.stringify({
        workloadId: recommendation.workloadId,
        sourceCellId: recommendation.sourceCellId,
        targetCellId: recommendation.targetCellId
      })
    }),
  rollbackRebalance: (executionId: string) =>
    request<RebalanceExecutionResult>(`/api/rebalance/executions/${executionId}/rollback`, {
      method: "POST"
    }),
  resetDemo: () =>
    request<DemoResetResponse>("/api/admin/reset", {
      method: "POST"
    }),
  createWorkload: (payload: CreateWorkloadPayload) =>
    request<Workload>("/api/workloads", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  placeWorkload: (workloadId: string, strategy: PlacementStrategy) =>
    request<Placement>(`/api/workloads/${workloadId}/place?strategy=${strategy}`, {
      method: "POST"
    }),
  simulateLoadSpike: (cellId: string) =>
    request<SimulationResult>("/api/simulations/load-spike", {
      method: "POST",
      body: JSON.stringify({
        cellId,
        load: {
          cpuCores: 8,
          memoryGb: 32,
          storageGb: 600,
          iops: 12000
        }
      })
    }),
  simulateCellFailure: (cellId: string) =>
    request<SimulationResult>("/api/simulations/cell-failure", {
      method: "POST",
      body: JSON.stringify({ cellId })
    })
};
