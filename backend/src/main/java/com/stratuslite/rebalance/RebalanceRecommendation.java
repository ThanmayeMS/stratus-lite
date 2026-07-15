package com.stratuslite.rebalance;

import com.stratuslite.placement.PlacementStrategy;

public record RebalanceRecommendation(
        String workloadId,
        String sourceCellId,
        String targetCellId,
        PlacementStrategy strategy,
        String reason,
        String explanation,
        String operatorAction,
        int priority
) {
}
