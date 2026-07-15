package com.stratuslite.rebalance;

import com.stratuslite.audit.ControlPlaneEventService;
import com.stratuslite.audit.ControlPlaneEventSeverity;
import com.stratuslite.audit.ControlPlaneEventType;
import com.stratuslite.common.ResourceNotFoundException;
import com.stratuslite.fleet.Cell;
import com.stratuslite.fleet.CellStatus;
import com.stratuslite.fleet.FleetService;
import com.stratuslite.placement.NoPlacementFoundException;
import com.stratuslite.placement.PlacementDecision;
import com.stratuslite.placement.PlacementEngine;
import com.stratuslite.placement.PlacementStrategy;
import com.stratuslite.simulation.SimulationService;
import com.stratuslite.workload.Workload;
import com.stratuslite.workload.WorkloadService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RebalanceService {

    private final FleetService fleetService;
    private final WorkloadService workloadService;
    private final PlacementEngine placementEngine;
    private final ControlPlaneEventService eventService;
    private final RebalanceExecutionRepository executionRepository;
    private final Clock clock;

    @Autowired
    public RebalanceService(
            FleetService fleetService,
            WorkloadService workloadService,
            PlacementEngine placementEngine,
            ControlPlaneEventService eventService,
            RebalanceExecutionRepository executionRepository
    ) {
        this(fleetService, workloadService, placementEngine, eventService, executionRepository, Clock.systemUTC());
    }

    RebalanceService(
            FleetService fleetService,
            WorkloadService workloadService,
            PlacementEngine placementEngine,
            ControlPlaneEventService eventService,
            RebalanceExecutionRepository executionRepository,
            Clock clock
    ) {
        this.fleetService = fleetService;
        this.workloadService = workloadService;
        this.placementEngine = placementEngine;
        this.eventService = eventService;
        this.executionRepository = executionRepository;
        this.clock = clock;
    }

    public List<RebalanceRecommendation> recommendations() {
        List<Cell> sourceCells = fleetService.listCells().stream()
                .filter(this::needsRebalance)
                .sorted(Comparator.comparing(Cell::id))
                .toList();

        List<RebalanceRecommendation> recommendations = new ArrayList<>();
        int priority = 1;
        for (Cell sourceCell : sourceCells) {
            List<Workload> workloads = workloadService.listWorkloadsOnCell(sourceCell.id());
            for (Workload workload : workloads) {
                recommendMove(sourceCell, workload, priority).ifPresent(recommendations::add);
                if (!recommendations.isEmpty()) {
                    priority = recommendations.size() + 1;
                }
            }
        }

        return recommendations;
    }

    public List<RebalanceExecutionRecord> executions() {
        return executionRepository.findAll();
    }

    @Transactional
    public RebalanceExecutionResult execute(RebalanceExecutionCommand command) {
        RebalanceRecommendation recommendation = recommendations().stream()
                .filter(candidate -> candidate.workloadId().equals(command.workloadId()))
                .filter(candidate -> candidate.sourceCellId().equals(command.sourceCellId()))
                .filter(candidate -> candidate.targetCellId().equals(command.targetCellId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No current rebalance recommendation matches the requested migration"
                ));

        Workload workload = workloadService.getWorkload(recommendation.workloadId());
        if (!recommendation.sourceCellId().equals(workload.assignedCellId())) {
            throw new IllegalStateException(
                    "Workload %s is not assigned to source cell %s"
                            .formatted(workload.id(), recommendation.sourceCellId())
            );
        }

        Cell targetCell = fleetService.getCell(recommendation.targetCellId());
        if (targetCell.status() != CellStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Target cell %s is not ACTIVE".formatted(targetCell.id())
            );
        }
        if (!targetCell.canHost(workload.tier(), workload.region(), workload.demand())) {
            throw new IllegalStateException(
                    "Target cell %s no longer has enough available capacity".formatted(targetCell.id())
            );
        }

        fleetService.releaseCapacity(recommendation.sourceCellId(), workload.demand());
        fleetService.reserveCapacity(recommendation.targetCellId(), workload.demand());
        Workload migrated = workloadService.markMigrated(workload.id(), recommendation.targetCellId());
        RebalanceExecutionRecord execution = RebalanceExecutionRecord.active(
                "rbe-" + UUID.randomUUID(),
                migrated.id(),
                recommendation.sourceCellId(),
                recommendation.targetCellId(),
                Instant.now(clock)
        );
        executionRepository.save(execution);
        eventService.record(
                ControlPlaneEventType.REBALANCE_EXECUTED,
                ControlPlaneEventSeverity.INFO,
                "workload",
                migrated.id(),
                "Migrated workload %s from %s to %s"
                        .formatted(migrated.id(), recommendation.sourceCellId(), recommendation.targetCellId())
        );

        return new RebalanceExecutionResult(
                execution.id(),
                migrated.id(),
                recommendation.sourceCellId(),
                recommendation.targetCellId(),
                migrated.state(),
                execution.status(),
                "Migrated workload %s from %s to %s"
                        .formatted(migrated.id(), recommendation.sourceCellId(), recommendation.targetCellId())
        );
    }

    @Transactional
    public RebalanceExecutionResult rollback(String executionId) {
        RebalanceExecutionRecord execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rebalance execution %s was not found".formatted(executionId)
                ));

        if (execution.status() != RebalanceExecutionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Rebalance execution %s has already been rolled back".formatted(executionId)
            );
        }

        Workload workload = workloadService.getWorkload(execution.workloadId());
        if (!execution.targetCellId().equals(workload.assignedCellId())) {
            throw new IllegalStateException(
                    "Workload %s is no longer assigned to rollback target cell %s"
                            .formatted(workload.id(), execution.targetCellId())
            );
        }

        Cell sourceCell = fleetService.getCell(execution.sourceCellId());
        if (sourceCell.status() != CellStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Rollback source cell %s is not ACTIVE".formatted(sourceCell.id())
            );
        }
        if (!sourceCell.canHost(workload.tier(), workload.region(), workload.demand())) {
            throw new IllegalStateException(
                    "Rollback source cell %s no longer has enough available capacity".formatted(sourceCell.id())
            );
        }

        fleetService.releaseCapacity(execution.targetCellId(), workload.demand());
        fleetService.reserveCapacity(execution.sourceCellId(), workload.demand());
        Workload rolledBackWorkload = workloadService.markMigrated(workload.id(), execution.sourceCellId());
        RebalanceExecutionRecord rolledBackExecution = execution.rolledBack(Instant.now(clock));
        executionRepository.save(rolledBackExecution);
        eventService.record(
                ControlPlaneEventType.REBALANCE_ROLLED_BACK,
                ControlPlaneEventSeverity.WARNING,
                "workload",
                rolledBackWorkload.id(),
                "Rolled back rebalance %s for workload %s from %s to %s"
                        .formatted(
                                execution.id(),
                                rolledBackWorkload.id(),
                                execution.targetCellId(),
                                execution.sourceCellId()
                        )
        );

        return new RebalanceExecutionResult(
                rolledBackExecution.id(),
                rolledBackWorkload.id(),
                execution.targetCellId(),
                execution.sourceCellId(),
                rolledBackWorkload.state(),
                rolledBackExecution.status(),
                "Rolled back workload %s from %s to %s"
                        .formatted(rolledBackWorkload.id(), execution.targetCellId(), execution.sourceCellId())
        );
    }

    private boolean needsRebalance(Cell cell) {
        return cell.status() == CellStatus.DOWN || cell.isOverloaded(SimulationService.OVERLOAD_THRESHOLD);
    }

    private Optional<RebalanceRecommendation> recommendMove(Cell sourceCell, Workload workload, int priority) {
        List<Cell> targetCandidates = fleetService.activeCellsSnapshot().stream()
                .filter(cell -> !cell.id().equals(sourceCell.id()))
                .filter(cell -> cell.status() == CellStatus.ACTIVE)
                .toList();

        try {
            PlacementDecision decision = placementEngine.place(
                    workload.toPlacementRequest(),
                    targetCandidates,
                    PlacementStrategy.LEAST_ALLOCATED
            );
            return Optional.of(new RebalanceRecommendation(
                    workload.id(),
                    sourceCell.id(),
                    decision.selectedCell().id(),
                    PlacementStrategy.LEAST_ALLOCATED,
                    reason(sourceCell),
                    priority
            ));
        } catch (NoPlacementFoundException ignored) {
            return Optional.empty();
        }
    }

    private static String reason(Cell sourceCell) {
        if (sourceCell.status() == CellStatus.DOWN) {
            return "Source cell is DOWN; workload should be restored on a healthy cell";
        }
        return "Source cell crossed the overload threshold; move workload to reduce hotspot risk";
    }
}
