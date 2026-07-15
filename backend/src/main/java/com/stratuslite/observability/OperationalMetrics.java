package com.stratuslite.observability;

import com.stratuslite.insights.CapacityRiskLevel;

public record OperationalMetrics(
        int workloadRequests,
        int activeWorkloads,
        int placementDecisions,
        int rejectedPlacementCandidates,
        int pendingRebalanceRecommendations,
        int totalMigrations,
        int activeMigrations,
        int rolledBackMigrations,
        int openIncidents,
        int recentAuditEvents,
        double maxUtilizationPercent,
        int riskScore,
        CapacityRiskLevel riskLevel,
        String explanation
) {
}
