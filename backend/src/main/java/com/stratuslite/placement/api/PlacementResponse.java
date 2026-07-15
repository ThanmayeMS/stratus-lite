package com.stratuslite.placement.api;

import com.stratuslite.placement.PlacementRecord;
import com.stratuslite.placement.PlacementStrategy;
import java.time.Instant;
import java.util.List;

public record PlacementResponse(
        String workloadId,
        String selectedCellId,
        PlacementStrategy strategy,
        String explanation,
        Instant decidedAt,
        List<CandidateScoreResponse> candidates
) {

    public static PlacementResponse from(PlacementRecord placementRecord) {
        return new PlacementResponse(
                placementRecord.workloadId(),
                placementRecord.selectedCellId(),
                placementRecord.strategy(),
                placementRecord.explanation(),
                placementRecord.decidedAt(),
                placementRecord.candidates().stream()
                        .map(CandidateScoreResponse::from)
                        .toList()
        );
    }
}

