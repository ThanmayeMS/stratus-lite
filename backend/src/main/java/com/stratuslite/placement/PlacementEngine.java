package com.stratuslite.placement;

import com.stratuslite.fleet.Cell;
import com.stratuslite.fleet.ResourceVector;
import com.stratuslite.workload.WorkloadRequest;
import java.util.ArrayList;
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
                .map(cell -> score(workload, cell, strategy))
                .sorted()
                .toList();

        CandidateScore winner = candidates.stream()
                .filter(CandidateScore::eligible)
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new NoPlacementFoundException(
                        "No active cell can host workload %s in region %s"
                                .formatted(workload.id(), workload.region())
                ));

        return new PlacementDecision(workload, winner.cell(), strategy, candidates);
    }

    private CandidateScore score(WorkloadRequest workload, Cell cell, PlacementStrategy strategy) {
        ResourceVector demand = workload.demand();
        List<String> blockers = policyBlockers(workload, cell);
        if (!blockers.isEmpty()) {
            return new CandidateScore(
                    cell,
                    false,
                    0.0,
                    round(cell.usedCapacity().averageUtilizationAgainst(cell.totalCapacity()) * 100.0),
                    "Rejected by policy: %s".formatted(String.join("; ", blockers)),
                    "not eligible for this workload"
            );
        }

        ResourceVector projectedUsage = cell.usedCapacity().plus(demand);
        double averageUtilization = projectedUsage.averageUtilizationAgainst(cell.totalCapacity());
        double variance = projectedUsage.utilizationVarianceAgainst(cell.totalCapacity());
        double projectedUtilizationPercent = round(averageUtilization * 100.0);
        String policySummary = "Accepted: active in %s, %s supports %s, capacity fits; projected utilization %.2f%%"
                .formatted(cell.region(), cell.tier(), workload.tier(), projectedUtilizationPercent);

        return switch (strategy) {
            case BEST_FIT -> new CandidateScore(
                    cell,
                    true,
                    projectedUtilizationPercent,
                    projectedUtilizationPercent,
                    policySummary,
                    "higher projected utilization means tighter packing"
            );
            case LEAST_ALLOCATED -> new CandidateScore(
                    cell,
                    true,
                    round((1.0 - averageUtilization) * 100.0),
                    projectedUtilizationPercent,
                    policySummary,
                    "lower projected utilization preserves more headroom"
            );
            case BALANCED -> new CandidateScore(
                    cell,
                    true,
                    round((1.0 - variance) * 100.0),
                    projectedUtilizationPercent,
                    policySummary,
                    "lower projected resource variance keeps the cell balanced"
            );
        };
    }

    private List<String> policyBlockers(WorkloadRequest workload, Cell cell) {
        List<String> blockers = new ArrayList<>();
        if (cell.status() != com.stratuslite.fleet.CellStatus.ACTIVE) {
            blockers.add("cell is %s".formatted(cell.status()));
        }
        if (!cell.region().equals(workload.region())) {
            blockers.add("region mismatch: workload needs %s".formatted(workload.region()));
        }
        if (!cell.tier().supports(workload.tier())) {
            blockers.add("%s tier does not support %s workload".formatted(cell.tier(), workload.tier()));
        }
        String capacityBlocker = capacityBlocker(cell.availableCapacity(), workload.demand());
        if (capacityBlocker != null) {
            blockers.add(capacityBlocker);
        }
        return blockers;
    }

    private String capacityBlocker(ResourceVector available, ResourceVector demand) {
        if (available.canFit(demand)) {
            return null;
        }

        List<String> deficits = new ArrayList<>();
        if (available.cpuCores() < demand.cpuCores()) {
            deficits.add("CPU %d/%d".formatted(available.cpuCores(), demand.cpuCores()));
        }
        if (available.memoryGb() < demand.memoryGb()) {
            deficits.add("memory %d/%d".formatted(available.memoryGb(), demand.memoryGb()));
        }
        if (available.storageGb() < demand.storageGb()) {
            deficits.add("storage %d/%d".formatted(available.storageGb(), demand.storageGb()));
        }
        if (available.iops() < demand.iops()) {
            deficits.add("IOPS %d/%d".formatted(available.iops(), demand.iops()));
        }
        return "capacity too small (%s available/needed)".formatted(String.join(", ", deficits));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
