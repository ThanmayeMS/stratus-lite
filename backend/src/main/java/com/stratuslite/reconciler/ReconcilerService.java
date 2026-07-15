package com.stratuslite.reconciler;

import com.stratuslite.audit.ControlPlaneEventService;
import com.stratuslite.audit.ControlPlaneEventSeverity;
import com.stratuslite.audit.ControlPlaneEventType;
import com.stratuslite.insights.CapacityInsight;
import com.stratuslite.insights.CapacityInsightService;
import com.stratuslite.rebalance.RebalanceExecutionStatus;
import com.stratuslite.rebalance.RebalanceService;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconcilerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReconcilerService.class);
    private static final String MODE = "MONITOR_ONLY";

    private final RebalanceService rebalanceService;
    private final CapacityInsightService capacityInsightService;
    private final ControlPlaneEventService eventService;
    private final Clock clock;
    private volatile ReconcilerStatus lastStatus;

    @Autowired
    public ReconcilerService(
            RebalanceService rebalanceService,
            CapacityInsightService capacityInsightService,
            ControlPlaneEventService eventService
    ) {
        this(rebalanceService, capacityInsightService, eventService, Clock.systemUTC());
    }

    ReconcilerService(
            RebalanceService rebalanceService,
            CapacityInsightService capacityInsightService,
            ControlPlaneEventService eventService,
            Clock clock
    ) {
        this.rebalanceService = rebalanceService;
        this.capacityInsightService = capacityInsightService;
        this.eventService = eventService;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${stratus.reconciler.fixed-delay-ms:30000}",
            initialDelayString = "${stratus.reconciler.initial-delay-ms:30000}"
    )
    public void scheduledSweep() {
        try {
            runOnce();
        } catch (RuntimeException exception) {
            LOGGER.warn("Reconciler sweep failed", exception);
        }
    }

    @Transactional(readOnly = true)
    public ReconcilerStatus status() {
        ReconcilerStatus current = buildStatus(lastStatus == null ? null : lastStatus.lastRunAt());
        lastStatus = current;
        return current;
    }

    @Transactional
    public ReconcilerStatus runOnce() {
        ReconcilerStatus status = buildStatus(Instant.now(clock));
        lastStatus = status;
        eventService.record(
                ControlPlaneEventType.RECONCILER_SWEEP,
                status.decision() == ReconcilerDecision.ACTION_REQUIRED
                        ? ControlPlaneEventSeverity.WARNING
                        : ControlPlaneEventSeverity.INFO,
                "reconciler",
                "autonomous-loop",
                "Reconciler sweep decided %s with %d pending recommendations and %d active migrations"
                        .formatted(status.decision(), status.pendingRecommendations(), status.activeMigrations())
        );
        return status;
    }

    private ReconcilerStatus buildStatus(Instant runAt) {
        int pendingRecommendations = rebalanceService.recommendations().size();
        int activeMigrations = (int) rebalanceService.executions().stream()
                .filter(execution -> execution.status() == RebalanceExecutionStatus.ACTIVE)
                .count();
        CapacityInsight insight = capacityInsightService.currentInsight();
        ReconcilerDecision decision = decision(pendingRecommendations, activeMigrations);
        return new ReconcilerStatus(
                MODE,
                decision,
                pendingRecommendations,
                activeMigrations,
                insight.riskLevel(),
                runAt,
                explanation(decision, pendingRecommendations, activeMigrations, insight),
                operatorAction(decision, pendingRecommendations)
        );
    }

    private static ReconcilerDecision decision(int pendingRecommendations, int activeMigrations) {
        if (pendingRecommendations > 0) {
            return ReconcilerDecision.ACTION_REQUIRED;
        }
        if (activeMigrations > 0) {
            return ReconcilerDecision.MIGRATION_ACTIVE;
        }
        return ReconcilerDecision.STEADY;
    }

    private static String explanation(
            ReconcilerDecision decision,
            int pendingRecommendations,
            int activeMigrations,
            CapacityInsight insight
    ) {
        return switch (decision) {
            case ACTION_REQUIRED ->
                    "Control loop found %d rebalance recommendations while fleet risk is %s."
                            .formatted(pendingRecommendations, insight.riskLevel());
            case MIGRATION_ACTIVE ->
                    "Control loop found %d active migrations and no new pending recommendations."
                            .formatted(activeMigrations);
            case STEADY ->
                    "Control loop found no pending migrations; fleet risk is %s with score %d."
                            .formatted(insight.riskLevel(), insight.riskScore());
        };
    }

    private static String operatorAction(ReconcilerDecision decision, int pendingRecommendations) {
        return switch (decision) {
            case ACTION_REQUIRED ->
                    "Review %d recommendation%s and execute the safest migration."
                            .formatted(pendingRecommendations, pendingRecommendations == 1 ? "" : "s");
            case MIGRATION_ACTIVE -> "Monitor active migrations and keep rollback available until verified.";
            case STEADY -> "No operator action required; the reconciler will keep watching.";
        };
    }
}
