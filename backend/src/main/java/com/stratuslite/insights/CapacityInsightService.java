package com.stratuslite.insights;

import com.stratuslite.fleet.Cell;
import com.stratuslite.fleet.CellStatus;
import com.stratuslite.fleet.FleetService;
import com.stratuslite.incident.IncidentSeverity;
import com.stratuslite.incident.IncidentService;
import com.stratuslite.rebalance.RebalanceService;
import com.stratuslite.simulation.SimulationService;
import com.stratuslite.workload.WorkloadService;
import com.stratuslite.workload.WorkloadState;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CapacityInsightService {

    private final FleetService fleetService;
    private final WorkloadService workloadService;
    private final IncidentService incidentService;
    private final RebalanceService rebalanceService;

    public CapacityInsightService(
            FleetService fleetService,
            WorkloadService workloadService,
            IncidentService incidentService,
            RebalanceService rebalanceService
    ) {
        this.fleetService = fleetService;
        this.workloadService = workloadService;
        this.incidentService = incidentService;
        this.rebalanceService = rebalanceService;
    }

    @Transactional(readOnly = true)
    public CapacityInsight currentInsight() {
        List<Cell> cells = fleetService.listCells();
        var workloads = workloadService.listWorkloads();
        var incidents = incidentService.listIncidents();
        var recommendations = rebalanceService.recommendations();

        int activeCells = countCells(cells, CellStatus.ACTIVE);
        int drainingCells = countCells(cells, CellStatus.DRAINING);
        int downCells = countCells(cells, CellStatus.DOWN);
        int overloadedCells = (int) cells.stream()
                .filter(cell -> cell.status() == CellStatus.ACTIVE)
                .filter(cell -> cell.isOverloaded(SimulationService.OVERLOAD_THRESHOLD))
                .count();
        int degradedWorkloads = (int) workloads.stream()
                .filter(workload -> workload.state() == WorkloadState.DEGRADED)
                .count();
        int criticalIncidents = (int) incidents.stream()
                .filter(incident -> incident.severity() == IncidentSeverity.CRITICAL)
                .count();
        double maxUtilizationPercent = cells.stream()
                .mapToDouble(Cell::maxUtilization)
                .max()
                .orElse(0.0) * 100.0;

        int riskScore = riskScore(
                maxUtilizationPercent,
                overloadedCells,
                downCells,
                degradedWorkloads,
                criticalIncidents,
                recommendations.size()
        );
        CapacityRiskLevel riskLevel = riskLevel(riskScore);

        return new CapacityInsight(
                cells.size(),
                activeCells,
                drainingCells,
                downCells,
                overloadedCells,
                workloads.size(),
                degradedWorkloads,
                incidents.size(),
                criticalIncidents,
                recommendations.size(),
                roundPercent(maxUtilizationPercent),
                riskScore,
                riskLevel,
                summary(riskLevel),
                explanation(
                        maxUtilizationPercent,
                        overloadedCells,
                        downCells,
                        degradedWorkloads,
                        criticalIncidents,
                        recommendations.size()
                ),
                operatorAction(riskLevel, recommendations.size())
        );
    }

    private static int countCells(List<Cell> cells, CellStatus status) {
        return (int) cells.stream()
                .filter(cell -> cell.status() == status)
                .count();
    }

    private static int riskScore(
            double maxUtilizationPercent,
            int overloadedCells,
            int downCells,
            int degradedWorkloads,
            int criticalIncidents,
            int recommendedMoves
    ) {
        long score = Math.round(maxUtilizationPercent * 0.5)
                + (long) overloadedCells * 15
                + (long) downCells * 20
                + (long) degradedWorkloads * 10
                + (long) criticalIncidents * 25
                + (long) recommendedMoves * 5;
        return (int) Math.min(100, score);
    }

    private static CapacityRiskLevel riskLevel(int riskScore) {
        if (riskScore >= 85) {
            return CapacityRiskLevel.CRITICAL;
        }
        if (riskScore >= 65) {
            return CapacityRiskLevel.HIGH;
        }
        if (riskScore >= 40) {
            return CapacityRiskLevel.ELEVATED;
        }
        return CapacityRiskLevel.LOW;
    }

    private static String summary(CapacityRiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> "Fleet has healthy headroom and no urgent migration pressure";
            case ELEVATED -> "Fleet has rising pressure; review recommendations before more demand arrives";
            case HIGH -> "Fleet is near a reliability threshold; prioritize mitigation";
            case CRITICAL -> "Fleet has active reliability risk; execute restore or rebalance actions";
        };
    }

    private static String explanation(
            double maxUtilizationPercent,
            int overloadedCells,
            int downCells,
            int degradedWorkloads,
            int criticalIncidents,
            int recommendedMoves
    ) {
        return "Risk score combines max utilization %.2f%%, %d overloaded cells, %d down cells, %d degraded workloads, %d critical incidents, and %d recommended moves."
                .formatted(
                        roundPercent(maxUtilizationPercent),
                        overloadedCells,
                        downCells,
                        degradedWorkloads,
                        criticalIncidents,
                        recommendedMoves
                );
    }

    private static String operatorAction(CapacityRiskLevel riskLevel, int recommendedMoves) {
        if (recommendedMoves > 0) {
            return "Review and execute rebalance recommendations before adding more workload.";
        }
        return switch (riskLevel) {
            case LOW -> "No immediate action required; continue monitoring utilization.";
            case ELEVATED -> "Review placement decisions and avoid adding large workloads to hot cells.";
            case HIGH -> "Investigate incidents and reduce pressure before accepting more demand.";
            case CRITICAL -> "Restore failed cells or migrate affected workloads immediately.";
        };
    }

    private static double roundPercent(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
