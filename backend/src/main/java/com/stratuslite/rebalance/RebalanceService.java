package com.stratuslite.rebalance;

import com.stratuslite.fleet.Cell;
import com.stratuslite.fleet.CellStatus;
import com.stratuslite.fleet.FleetService;
import com.stratuslite.placement.NoPlacementFoundException;
import com.stratuslite.placement.PlacementDecision;
import com.stratuslite.placement.PlacementEngine;
import com.stratuslite.placement.PlacementStrategy;
import com.stratuslite.simulation.SimulationService;
import com.stratuslite.workload.Workload;
import com.stratuslite.workload.WorkloadService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class RebalanceService {

    private final FleetService fleetService;
    private final WorkloadService workloadService;
    private final PlacementEngine placementEngine;

    public RebalanceService(
            FleetService fleetService,
            WorkloadService workloadService,
            PlacementEngine placementEngine
    ) {
        this.fleetService = fleetService;
        this.workloadService = workloadService;
        this.placementEngine = placementEngine;
    }

    public List<RebalanceRecommendation> recommendations() {
        List<Cell> sourceCells = fleetService.listCells().stream()
                .filter(this::needsRebalance)
                .sorted(Comparator.comparing(Cell::id))
                .toList();

        List<RebalanceRecommendation> recommendations = new ArrayList<>();
        int priority = 1;
        for (Cell sourceCell : sourceCells) {
            List<Workload> workloads = workloadService.listWorkloadsOnCell(sourceCell.id());
            for (Workload workload : workloads) {
                recommendMove(sourceCell, workload, priority).ifPresent(recommendations::add);
                if (!recommendations.isEmpty()) {
                    priority = recommendations.size() + 1;
                }
            }
        }

        return recommendations;
    }

    private boolean needsRebalance(Cell cell) {
        return cell.status() == CellStatus.DOWN || cell.isOverloaded(SimulationService.OVERLOAD_THRESHOLD);
    }

    private Optional<RebalanceRecommendation> recommendMove(Cell sourceCell, Workload workload, int priority) {
        List<Cell> targetCandidates = fleetService.activeCellsSnapshot().stream()
                .filter(cell -> !cell.id().equals(sourceCell.id()))
                .filter(cell -> cell.status() == CellStatus.ACTIVE)
                .toList();

        try {
            PlacementDecision decision = placementEngine.place(
                    workload.toPlacementRequest(),
                    targetCandidates,
                    PlacementStrategy.LEAST_ALLOCATED
            );
            return Optional.of(new RebalanceRecommendation(
                    workload.id(),
                    sourceCell.id(),
                    decision.selectedCell().id(),
                    PlacementStrategy.LEAST_ALLOCATED,
                    reason(sourceCell),
                    priority
            ));
        } catch (NoPlacementFoundException ignored) {
            return Optional.empty();
        }
    }

    private static String reason(Cell sourceCell) {
        if (sourceCell.status() == CellStatus.DOWN) {
            return "Source cell is DOWN; workload should be restored on a healthy cell";
        }
        return "Source cell crossed the overload threshold; move workload to reduce hotspot risk";
    }
}
