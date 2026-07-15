package com.stratuslite.rebalance;

import java.time.Instant;
import java.util.Objects;

public record RebalanceExecutionRecord(
        String id,
        String workloadId,
        String sourceCellId,
        String targetCellId,
        RebalanceExecutionStatus status,
        String explanation,
        String operatorAction,
        Instant createdAt,
        Instant rolledBackAt
) {

    public RebalanceExecutionRecord {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(workloadId, "workloadId is required");
        Objects.requireNonNull(sourceCellId, "sourceCellId is required");
        Objects.requireNonNull(targetCellId, "targetCellId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(explanation, "explanation is required");
        Objects.requireNonNull(operatorAction, "operatorAction is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (status == RebalanceExecutionStatus.ROLLED_BACK) {
            Objects.requireNonNull(rolledBackAt, "rolledBackAt is required for rolled back executions");
        }
    }

    public static RebalanceExecutionRecord active(
            String id,
            String workloadId,
            String sourceCellId,
            String targetCellId,
            String explanation,
            String operatorAction,
            Instant createdAt
    ) {
        return new RebalanceExecutionRecord(
                id,
                workloadId,
                sourceCellId,
                targetCellId,
                RebalanceExecutionStatus.ACTIVE,
                explanation,
                operatorAction,
                createdAt,
                null
        );
    }

    public RebalanceExecutionRecord rolledBack(Instant now, String explanation, String operatorAction) {
        return new RebalanceExecutionRecord(
                id,
                workloadId,
                sourceCellId,
                targetCellId,
                RebalanceExecutionStatus.ROLLED_BACK,
                explanation,
                operatorAction,
                createdAt,
                now
        );
    }
}
