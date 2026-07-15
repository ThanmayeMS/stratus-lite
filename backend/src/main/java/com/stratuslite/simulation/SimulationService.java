package com.stratuslite.simulation;

import com.stratuslite.audit.ControlPlaneEventService;
import com.stratuslite.audit.ControlPlaneEventSeverity;
import com.stratuslite.audit.ControlPlaneEventType;
import com.stratuslite.fleet.Cell;
import com.stratuslite.fleet.FleetService;
import com.stratuslite.incident.Incident;
import com.stratuslite.incident.IncidentService;
import com.stratuslite.incident.IncidentSeverity;
import com.stratuslite.incident.IncidentType;
import com.stratuslite.workload.WorkloadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SimulationService {

    public static final double OVERLOAD_THRESHOLD = 0.85;

    private final FleetService fleetService;
    private final WorkloadService workloadService;
    private final IncidentService incidentService;
    private final ControlPlaneEventService eventService;

    public SimulationService(
            FleetService fleetService,
            WorkloadService workloadService,
            IncidentService incidentService,
            ControlPlaneEventService eventService
    ) {
        this.fleetService = fleetService;
        this.workloadService = workloadService;
        this.incidentService = incidentService;
        this.eventService = eventService;
    }

    @Transactional
    public SimulationResult simulateLoadSpike(LoadSpikeCommand command) {
        Cell cell = fleetService.applyLoadSpike(command.cellId(), command.load());
        boolean overloaded = cell.isOverloaded(OVERLOAD_THRESHOLD);
        Incident incident = incidentService.record(
                overloaded ? IncidentType.CELL_OVERLOADED : IncidentType.CELL_LOAD_SPIKE,
                overloaded ? IncidentSeverity.WARNING : IncidentSeverity.INFO,
                cell.id(),
                overloaded
                        ? "Cell %s crossed %.0f%% utilization".formatted(cell.id(), OVERLOAD_THRESHOLD * 100.0)
                        : "Load spike applied to cell %s".formatted(cell.id())
        );
        eventService.record(
                ControlPlaneEventType.LOAD_SPIKE_SIMULATED,
                overloaded ? ControlPlaneEventSeverity.WARNING : ControlPlaneEventSeverity.INFO,
                "cell",
                cell.id(),
                "Applied load spike to %s; max utilization is %.2f%%"
                        .formatted(cell.id(), percent(cell.maxUtilization()))
        );

        return new SimulationResult(
                cell.id(),
                cell.status(),
                percent(cell.maxUtilization()),
                0,
                incident,
                overloaded
                        ? "The injected load pushed this cell past the overload threshold, so rebalance may be recommended."
                        : "The injected load was recorded, but the cell still has enough headroom.",
                overloaded
                        ? "Review rebalance recommendations for workloads on this cell."
                        : "Continue monitoring; no immediate migration is required."
        );
    }

    @Transactional
    public SimulationResult simulateCellFailure(CellFailureCommand command) {
        Cell cell = fleetService.markDown(command.cellId());
        int affectedWorkloads = workloadService.markAssignedWorkloadsDegraded(cell.id()).size();
        Incident incident = incidentService.record(
                IncidentType.CELL_DOWN,
                IncidentSeverity.CRITICAL,
                cell.id(),
                "Cell %s marked DOWN; %d workloads degraded".formatted(cell.id(), affectedWorkloads)
        );
        eventService.record(
                ControlPlaneEventType.CELL_FAILURE_SIMULATED,
                ControlPlaneEventSeverity.CRITICAL,
                "cell",
                cell.id(),
                "Marked %s DOWN; %d workloads degraded".formatted(cell.id(), affectedWorkloads)
        );

        return new SimulationResult(
                cell.id(),
                cell.status(),
                percent(cell.maxUtilization()),
                affectedWorkloads,
                incident,
                "The cell was marked DOWN, so assigned workloads were degraded and should be restored elsewhere.",
                affectedWorkloads > 0
                        ? "Open rebalance recommendations and execute migrations for affected workloads."
                        : "No workloads were assigned to this cell; confirm the incident and reset the demo if needed."
        );
    }

    private static double percent(double ratio) {
        return Math.round(ratio * 10_000.0) / 100.0;
    }
}
