package com.stratuslite.insights;

public record CapacityInsight(
        int totalCells,
        int activeCells,
        int drainingCells,
        int downCells,
        int overloadedCells,
        int totalWorkloads,
        int degradedWorkloads,
        int openIncidents,
        int criticalIncidents,
        int recommendedMoves,
        double maxUtilizationPercent,
        int riskScore,
        CapacityRiskLevel riskLevel,
        String summary,
        String explanation,
        String operatorAction
) {
}
