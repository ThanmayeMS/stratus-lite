package com.stratuslite.reconciler;

import com.stratuslite.insights.CapacityRiskLevel;
import java.time.Instant;

public record ReconcilerStatus(
        String mode,
        ReconcilerDecision decision,
        int pendingRecommendations,
        int activeMigrations,
        CapacityRiskLevel riskLevel,
        Instant lastRunAt,
        String explanation,
        String operatorAction
) {
}
