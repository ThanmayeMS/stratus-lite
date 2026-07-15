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
    expect(screen.getAllByText("cell-use1-a").length).toBeGreaterThan(0);
    expect(screen.getAllByText("wl-demo").length).toBeGreaterThan(0);
    expect(screen.getByText("CELL_OVERLOADED")).toBeInTheDocument();
    expect(screen.getByText("Capacity risk")).toBeInTheDocument();
    expect(screen.getByText("LOW")).toBeInTheDocument();
    expect(screen.getByText("PLACEMENT_CREATED")).toBeInTheDocument();
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
        "/api/workloads/wl-demo/place?strategy=LEAST_ALLOCATED",
        expect.objectContaining({ method: "POST" })
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
        "/api/rebalance/executions",
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
        "/api/rebalance/executions/rbe-demo/rollback",
        expect.objectContaining({ method: "POST" })
      );
    });
  });
});

function mockFetch(input: RequestInfo | URL, init?: RequestInit) {
  const url = input.toString();
  const body = responseFor(url, init?.method ?? "GET");
  return Promise.resolve({
    ok: true,
    json: () => Promise.resolve(body)
  } as Response);
}

function responseFor(url: string, method: string) {
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
          { cellId: "cell-use1-a", score: 69.06, reason: "lower projected utilization preserves more headroom" }
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
      summary: "Fleet has healthy headroom and no urgent migration pressure"
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
      message: "Migrated workload wl-demo from cell-use1-a to cell-use1-b"
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
      message: "Rolled back workload wl-demo from cell-use1-b to cell-use1-a"
    };
  }
  return {
    workloadId: "wl-demo",
    selectedCellId: "cell-use1-a",
    strategy: "LEAST_ALLOCATED",
    explanation: "Selected cell cell-use1-a with score 69.06 using LEAST_ALLOCATED",
    decidedAt: "2026-07-15T00:00:00Z",
    candidates: []
  };
}
