import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { App } from "./App";

const cells = [
  {
    id: "cell-use1-a",
    region: "us-east",
    tier: "STANDARD",
    status: "ACTIVE",
    totalCapacity: { cpuCores: 16, memoryGb: 64, storageGb: 1000, iops: 20000 },
    usedCapacity: { cpuCores: 4, memoryGb: 16, storageGb: 250, iops: 4000 },
    availableCapacity: { cpuCores: 12, memoryGb: 48, storageGb: 750, iops: 16000 },
    utilizationPercent: 23.75
  },
  {
    id: "cell-use1-b",
    region: "us-east",
    tier: "PREMIUM",
    status: "ACTIVE",
    totalCapacity: { cpuCores: 32, memoryGb: 128, storageGb: 2000, iops: 50000 },
    usedCapacity: { cpuCores: 18, memoryGb: 72, storageGb: 1100, iops: 28000 },
    availableCapacity: { cpuCores: 14, memoryGb: 56, storageGb: 900, iops: 22000 },
    utilizationPercent: 55.88
  }
];

const workloads = [
  {
    id: "wl-demo",
    tenantId: "tenant-alpha",
    region: "us-east",
    tier: "STANDARD",
    demand: { cpuCores: 2, memoryGb: 4, storageGb: 50, iops: 1000 },
    state: "REQUESTED",
    assignedCellId: null,
    createdAt: "2026-07-15T00:00:00Z",
    updatedAt: "2026-07-15T00:00:00Z"
  }
];

describe("App", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn(mockFetch));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  test("renders fleet, workload, incident, and rebalance data", async () => {
    render(<App />);

    expect(await screen.findByText("Capacity and workload placement dashboard")).toBeInTheDocument();
    expect(screen.getByRole("navigation", { name: /dashboard sections/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /start/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /overview/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /operate/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /observe/i })).toBeInTheDocument();
    expect(screen.getByText("What this dashboard does")).toBeInTheDocument();
    expect(screen.queryByText("Learn mode")).not.toBeInTheDocument();
    expect(screen.getAllByText("Cell").length).toBeGreaterThan(0);
    expect(screen.getByRole("button", { name: /run failure drill/i })).toBeInTheDocument();
    expect(screen.getAllByText("cell-use1-a").length).toBeGreaterThan(0);
    expect(screen.getAllByText("wl-demo").length).toBeGreaterThan(0);
    expect(screen.getByText("CELL_OVERLOADED")).toBeInTheDocument();
    expect(screen.getByText("Capacity risk")).toBeInTheDocument();
    expect(screen.getByText("LOW")).toBeInTheDocument();
    expect(screen.getByText("PLACEMENT_CREATED")).toBeInTheDocument();
    expect(screen.getByText("eligible")).toBeInTheDocument();
    expect(screen.getByText("rejected")).toBeInTheDocument();
    expect(screen.getByText(/Projected util 31.3%/i)).toBeInTheDocument();
    expect(screen.getByText(/Rejected by policy/i)).toBeInTheDocument();
    expect(screen.getByText(/Risk score combines/i)).toBeInTheDocument();
    expect(screen.getByText(/Target decision/i)).toBeInTheDocument();
    expect(screen.getByText("Autonomous loop")).toBeInTheDocument();
    expect(screen.getByText("Action Required")).toBeInTheDocument();
    expect(screen.getByText("Operational metrics")).toBeInTheDocument();
    expect(screen.getByText(/Metrics summarize/i)).toBeInTheDocument();
    expect(screen.getByText("1 rebalance move ready")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /rebalance/i }).length).toBeGreaterThan(1);
    expect(screen.getByRole("button", { name: /migrations/i })).toBeInTheDocument();
    expect(screen.getAllByText("cell-use1-a → cell-use1-b").length).toBeGreaterThan(1);
    expect(screen.getByText("ACTIVE")).toBeInTheDocument();
  });

  test("places the selected workload", async () => {
    const user = userEvent.setup();
    render(<App />);

    await screen.findAllByText("wl-demo");
    await user.selectOptions(screen.getByLabelText("Requested workload"), "wl-demo");
    await user.click(screen.getByRole("button", { name: /place workload/i }));

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/workloads/wl-demo/place?strategy=LEAST_ALLOCATED"),
        expect.objectContaining({ method: "POST" })
      );
    });
  });

  test("creates a workload from the action button", async () => {
    const user = userEvent.setup();
    render(<App />);

    await screen.findAllByText("wl-demo");
    await user.click(screen.getByRole("button", { name: /create workload/i }));

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/workloads"),
        expect.objectContaining({
          method: "POST",
          body: expect.stringContaining("\"tenantId\":\"tenant-alpha\"")
        })
      );
    });
  });

  test("executes a rebalance recommendation", async () => {
    const user = userEvent.setup();
    render(<App />);

    await screen.findAllByText("cell-use1-a → cell-use1-b");
    await user.click(screen.getByRole("button", { name: /execute/i }));

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/rebalance/executions"),
        expect.objectContaining({
          method: "POST",
          body: JSON.stringify({
            workloadId: "wl-demo",
            sourceCellId: "cell-use1-a",
            targetCellId: "cell-use1-b"
          })
        })
      );
    });
  });

  test("rolls back an active rebalance execution", async () => {
    const user = userEvent.setup();
    render(<App />);

    await screen.findByText("ACTIVE");
    await user.click(screen.getByRole("button", { name: /rollback/i }));

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/rebalance/executions/rbe-demo/rollback"),
        expect.objectContaining({ method: "POST" })
      );
    });
  });

  test("runs the beginner failure drill end to end", async () => {
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("button", { name: /run failure drill/i });
    await user.click(screen.getByRole("button", { name: /run failure drill/i }));

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/admin/reset"),
        expect.objectContaining({ method: "POST" })
      );
      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/workloads"),
        expect.objectContaining({ method: "POST" })
      );
      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/workloads/wl-created/place?strategy=LEAST_ALLOCATED"),
        expect.objectContaining({ method: "POST" })
      );
      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/simulations/cell-failure"),
        expect.objectContaining({ method: "POST" })
      );
      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/rebalance/executions"),
        expect.objectContaining({ method: "POST" })
      );
    });
  });

  test("runs the reconciler check on demand", async () => {
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("button", { name: /run check/i });
    await user.click(screen.getByRole("button", { name: /run check/i }));

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining("/api/reconciler/run"),
        expect.objectContaining({ method: "POST" })
      );
    });
  });
});

function mockFetch(input: RequestInfo | URL, init?: RequestInit) {
  const url = new URL(input.toString(), "http://localhost");
  const body = responseFor(`${url.pathname}${url.search}`, init?.method ?? "GET");
  return Promise.resolve({
    ok: true,
    json: () => Promise.resolve(body)
  } as Response);
}

function responseFor(url: string, method: string) {
  if (url === "/api/workloads" && method === "POST") {
    return {
      id: "wl-created",
      tenantId: "tenant-alpha",
      region: "us-east",
      tier: "STANDARD",
      demand: { cpuCores: 2, memoryGb: 4, storageGb: 50, iops: 1000 },
      state: "REQUESTED",
      assignedCellId: null,
      createdAt: "2026-07-15T00:00:00Z",
      updatedAt: "2026-07-15T00:00:00Z"
    };
  }
  if (url === "/api/cells") {
    return cells;
  }
  if (url === "/api/workloads") {
    return workloads;
  }
  if (url === "/api/placements") {
    return [
      {
        workloadId: "wl-demo",
        selectedCellId: "cell-use1-a",
        strategy: "LEAST_ALLOCATED",
        explanation: "Selected cell cell-use1-a with score 69.06 using LEAST_ALLOCATED",
        decidedAt: "2026-07-15T00:00:00Z",
        candidates: [
          {
            cellId: "cell-use1-a",
            eligible: true,
            score: 69.06,
            projectedUtilizationPercent: 31.25,
            policySummary: "Accepted: active in us-east, STANDARD supports STANDARD, capacity fits; projected utilization 31.25%",
            reason: "lower projected utilization preserves more headroom"
          },
          {
            cellId: "cell-usw2-a",
            eligible: false,
            score: 0,
            projectedUtilizationPercent: 18.1,
            policySummary: "Rejected by policy: region mismatch: workload needs us-east",
            reason: "not eligible for this workload"
          }
        ]
      }
    ];
  }
  if (url === "/api/incidents") {
    return [
      {
        id: "inc-demo",
        type: "CELL_OVERLOADED",
        severity: "WARNING",
        cellId: "cell-use1-a",
        message: "Cell cell-use1-a crossed 85% utilization",
        createdAt: "2026-07-15T00:00:00Z"
      }
    ];
  }
  if (url === "/api/insights/capacity") {
    return {
      totalCells: 2,
      activeCells: 2,
      drainingCells: 0,
      downCells: 0,
      overloadedCells: 0,
      totalWorkloads: 1,
      degradedWorkloads: 0,
      openIncidents: 1,
      criticalIncidents: 0,
      recommendedMoves: 1,
      maxUtilizationPercent: 55.88,
      riskScore: 33,
      riskLevel: "LOW",
      summary: "Fleet has healthy headroom and no urgent migration pressure",
      explanation:
        "Risk score combines max utilization 55.88%, 0 overloaded cells, 0 down cells, 0 degraded workloads, 0 critical incidents, and 1 recommended moves.",
      operatorAction: "Review and execute rebalance recommendations before adding more workload."
    };
  }
  if (url === "/api/reconciler/status" || url === "/api/reconciler/run") {
    return {
      mode: "MONITOR_ONLY",
      decision: "ACTION_REQUIRED",
      pendingRecommendations: 1,
      activeMigrations: 1,
      riskLevel: "LOW",
      lastRunAt: "2026-07-15T00:00:00Z",
      explanation: "Control loop found 1 rebalance recommendations while fleet risk is LOW.",
      operatorAction: "Review 1 recommendation and execute the safest migration."
    };
  }
  if (url === "/api/metrics/operations") {
    return {
      workloadRequests: 1,
      activeWorkloads: 0,
      placementDecisions: 1,
      rejectedPlacementCandidates: 1,
      pendingRebalanceRecommendations: 1,
      totalMigrations: 1,
      activeMigrations: 1,
      rolledBackMigrations: 0,
      openIncidents: 1,
      recentAuditEvents: 4,
      maxUtilizationPercent: 55.88,
      riskScore: 33,
      riskLevel: "LOW",
      explanation:
        "Metrics summarize 1 placement decisions, 1 rejected placement candidates, 1 pending rebalance recommendations, and 1 migration executions."
    };
  }
  if (url === "/api/events?limit=20") {
    return [
      {
        id: "evt-demo",
        type: "PLACEMENT_CREATED",
        severity: "INFO",
        subjectType: "workload",
        subjectId: "wl-demo",
        message: "Placed workload wl-demo on cell-use1-a using LEAST_ALLOCATED",
        createdAt: "2026-07-15T00:00:00Z"
      }
    ];
  }
  if (url === "/api/rebalance/recommendations") {
    return [
      {
        workloadId: "wl-demo",
        sourceCellId: "cell-use1-a",
        targetCellId: "cell-use1-b",
        strategy: "LEAST_ALLOCATED",
        reason: "Source cell crossed the overload threshold; move workload to reduce hotspot risk",
        explanation:
          "Source cell cell-use1-a triggered rebalance: Source cell crossed the overload threshold; move workload to reduce hotspot risk Target decision: Selected cell cell-use1-b with score 44.12 using LEAST_ALLOCATED.",
        operatorAction: "Execute migration for workload wl-demo to cell-use1-b, then verify migration history.",
        priority: 1
      }
    ];
  }
  if (url === "/api/rebalance/executions" && method === "GET") {
    return [
      {
        id: "rbe-demo",
        workloadId: "wl-demo",
        sourceCellId: "cell-use1-a",
        targetCellId: "cell-use1-b",
        status: "ACTIVE",
        explanation: "Executed because the source cell was risky and the target was selected by policy.",
        operatorAction: "Monitor workload wl-demo on cell-use1-b; rollback remains available while active.",
        createdAt: "2026-07-15T00:00:00Z",
        rolledBackAt: null
      }
    ];
  }
  if (url === "/api/rebalance/executions" && method === "POST") {
    return {
      executionId: "rbe-demo",
      workloadId: "wl-demo",
      sourceCellId: "cell-use1-a",
      targetCellId: "cell-use1-b",
      state: "RUNNING",
      status: "ACTIVE",
      message: "Migrated workload wl-demo from cell-use1-a to cell-use1-b",
      explanation: "Executed because the source cell was risky and the target was selected by policy.",
      operatorAction: "Monitor workload wl-demo on cell-use1-b; rollback remains available while active."
    };
  }
  if (url === "/api/rebalance/executions/rbe-demo/rollback") {
    return {
      executionId: "rbe-demo",
      workloadId: "wl-demo",
      sourceCellId: "cell-use1-b",
      targetCellId: "cell-use1-a",
      state: "RUNNING",
      status: "ROLLED_BACK",
      message: "Rolled back workload wl-demo from cell-use1-b to cell-use1-a",
      explanation: "Rolled back because the original source cell is ACTIVE and has enough capacity.",
      operatorAction: "Verify workload wl-demo is healthy on cell-use1-a before starting another migration."
    };
  }
  if (url === "/api/admin/reset") {
    return {
      message: "Demo state reset"
    };
  }
  if (url === "/api/simulations/cell-failure") {
    return {
      cellId: "cell-use1-a",
      cellStatus: "DOWN",
      maxUtilizationPercent: 55.88,
      affectedWorkloads: 1,
      incident: {
        id: "inc-failure",
        type: "CELL_FAILED",
        severity: "CRITICAL",
        cellId: "cell-use1-a",
        message: "Cell cell-use1-a is down",
        createdAt: "2026-07-15T00:00:00Z"
      },
      explanation: "The cell was marked DOWN, so assigned workloads were degraded and should be restored elsewhere.",
      operatorAction: "Open rebalance recommendations and execute migrations for affected workloads."
    };
  }
  return {
    workloadId: "wl-demo",
    selectedCellId: "cell-use1-a",
    strategy: "LEAST_ALLOCATED",
    explanation: "Selected cell cell-use1-a with score 69.06 using LEAST_ALLOCATED",
    decidedAt: "2026-07-15T00:00:00Z",
    candidates: [
      {
        cellId: "cell-use1-a",
        eligible: true,
        score: 69.06,
        projectedUtilizationPercent: 31.25,
        policySummary: "Accepted: active in us-east, STANDARD supports STANDARD, capacity fits; projected utilization 31.25%",
        reason: "lower projected utilization preserves more headroom"
      }
    ]
  };
}
