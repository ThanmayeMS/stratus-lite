package com.stratuslite.simulation;

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

    public SimulationService(
            FleetService fleetService,
            WorkloadService workloadService,
            IncidentService incidentService
    ) {
        this.fleetService = fleetService;
        this.workloadService = workloadService;
        this.incidentService = incidentService;
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

        return new SimulationResult(
                cell.id(),
                cell.status(),
                percent(cell.maxUtilization()),
                0,
                incident
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

        return new SimulationResult(
                cell.id(),
                cell.status(),
                percent(cell.maxUtilization()),
                affectedWorkloads,
                incident
        );
    }

    private static double percent(double ratio) {
        return Math.round(ratio * 10_000.0) / 100.0;
    }
}
