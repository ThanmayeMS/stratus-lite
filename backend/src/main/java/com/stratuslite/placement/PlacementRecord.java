package com.stratuslite.placement;

import java.time.Instant;
import java.util.List;

public record PlacementRecord(
        String workloadId,
        String selectedCellId,
        PlacementStrategy strategy,
        List<CandidateScore> candidates,
        String explanation,
        Instant decidedAt
) {

    public static PlacementRecord from(PlacementDecision decision) {
        return new PlacementRecord(
                decision.workload().id(),
                decision.selectedCell().id(),
                decision.strategy(),
                decision.candidates(),
                decision.explanation(),
                Instant.now()
        );
    }
}

