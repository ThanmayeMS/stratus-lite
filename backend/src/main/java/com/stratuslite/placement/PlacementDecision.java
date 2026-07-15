package com.stratuslite.placement;

import com.stratuslite.fleet.Cell;
import com.stratuslite.workload.WorkloadRequest;
import java.util.List;

public record PlacementDecision(
        WorkloadRequest workload,
        Cell selectedCell,
        PlacementStrategy strategy,
        List<CandidateScore> candidates
) {

    public String explanation() {
        CandidateScore winner = candidates.stream()
                .filter(candidate -> candidate.cell().id().equals(selectedCell.id()))
                .findFirst()
                .orElseThrow();
        return "Selected cell %s with score %.2f using %s: %s"
                .formatted(selectedCell.id(), winner.score(), strategy, winner.reason());
    }
}

