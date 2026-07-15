package com.stratuslite.placement;

import com.stratuslite.fleet.Cell;
import com.stratuslite.fleet.ResourceVector;
import com.stratuslite.workload.WorkloadRequest;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlacementEngine {

    public PlacementDecision place(
            WorkloadRequest workload,
            List<Cell> cells,
            PlacementStrategy strategy
    ) {
        List<CandidateScore> candidates = cells.stream()
                .filter(cell -> cell.canHost(workload.tier(), workload.region(), workload.demand()))
                .map(cell -> score(cell, workload.demand(), strategy))
                .sorted()
                .toList();

        CandidateScore winner = candidates.stream()
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new NoPlacementFoundException(
                        "No active cell can host workload %s in region %s"
                                .formatted(workload.id(), workload.region())
                ));

        return new PlacementDecision(workload, winner.cell(), strategy, candidates);
    }

    private CandidateScore score(Cell cell, ResourceVector demand, PlacementStrategy strategy) {
        ResourceVector projectedUsage = cell.usedCapacity().plus(demand);
        double averageUtilization = projectedUsage.averageUtilizationAgainst(cell.totalCapacity());
        double variance = projectedUsage.utilizationVarianceAgainst(cell.totalCapacity());

        return switch (strategy) {
            case BEST_FIT -> new CandidateScore(
                    cell,
                    round(averageUtilization * 100.0),
                    "higher projected utilization means tighter packing"
            );
            case LEAST_ALLOCATED -> new CandidateScore(
                    cell,
                    round((1.0 - averageUtilization) * 100.0),
                    "lower projected utilization preserves more headroom"
            );
            case BALANCED -> new CandidateScore(
                    cell,
                    round((1.0 - variance) * 100.0),
                    "lower projected resource variance keeps the cell balanced"
            );
        };
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
