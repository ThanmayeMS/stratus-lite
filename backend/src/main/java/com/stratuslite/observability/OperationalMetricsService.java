package com.stratuslite.observability;

import com.stratuslite.audit.ControlPlaneEventService;
import com.stratuslite.insights.CapacityInsight;
import com.stratuslite.insights.CapacityInsightService;
import com.stratuslite.placement.PlacementService;
import com.stratuslite.rebalance.RebalanceExecutionStatus;
import com.stratuslite.rebalance.RebalanceService;
import com.stratuslite.workload.WorkloadService;
import com.stratuslite.workload.WorkloadState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalMetricsService {

    private final WorkloadService workloadService;
    private final PlacementService placementService;
    private final RebalanceService rebalanceService;
    private final CapacityInsightService capacityInsightService;
    private final ControlPlaneEventService eventService;

    public OperationalMetricsService(
            WorkloadService workloadService,
            PlacementService placementService,
            RebalanceService rebalanceService,
            CapacityInsightService capacityInsightService,
            ControlPlaneEventService eventService
    ) {
        this.workloadService = workloadService;
        this.placementService = placementService;
        this.rebalanceService = rebalanceService;
        this.capacityInsightService = capacityInsightService;
        this.eventService = eventService;
    }

    @Transactional(readOnly = true)
    public OperationalMetrics currentMetrics() {
        var workloads = workloadService.listWorkloads();
        var placements = placementService.listPlacements();
        var recommendations = rebalanceService.recommendations();
        var executions = rebalanceService.executions();
        CapacityInsight insight = capacityInsightService.currentInsight();

        int activeWorkloads = (int) workloads.stream()
                .filter(workload -> workload.state() == WorkloadState.PLACED || workload.state() == WorkloadState.RUNNING)
                .count();
        int rejectedPlacementCandidates = placements.stream()
                .mapToInt(placement -> (int) placement.candidates().stream()
                        .filter(candidate -> !candidate.eligible())
                        .count())
                .sum();
        int activeMigrations = (int) executions.stream()
                .filter(execution -> execution.status() == RebalanceExecutionStatus.ACTIVE)
                .count();
        int rolledBackMigrations = (int) executions.stream()
                .filter(execution -> execution.status() == RebalanceExecutionStatus.ROLLED_BACK)
                .count();

        return new OperationalMetrics(
                workloads.size(),
                activeWorkloads,
                placements.size(),
                rejectedPlacementCandidates,
                recommendations.size(),
                executions.size(),
                activeMigrations,
                rolledBackMigrations,
                insight.openIncidents(),
                eventService.listLatest(100).size(),
                insight.maxUtilizationPercent(),
                insight.riskScore(),
                insight.riskLevel(),
                explanation(placements.size(), rejectedPlacementCandidates, recommendations.size(), executions.size())
        );
    }

    private static String explanation(
            int placementDecisions,
            int rejectedPlacementCandidates,
            int pendingRebalanceRecommendations,
            int totalMigrations
    ) {
        return "Metrics summarize %d placement decisions, %d rejected placement candidates, %d pending rebalance recommendations, and %d migration executions."
                .formatted(
                        placementDecisions,
                        rejectedPlacementCandidates,
                        pendingRebalanceRecommendations,
                        totalMigrations
                );
    }
}
