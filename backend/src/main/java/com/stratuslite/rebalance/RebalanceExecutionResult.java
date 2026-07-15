package com.stratuslite.rebalance;

import com.stratuslite.workload.WorkloadState;

public record RebalanceExecutionResult(
        String executionId,
        String workloadId,
        String sourceCellId,
        String targetCellId,
        WorkloadState state,
        RebalanceExecutionStatus status,
        String message,
        String explanation,
        String operatorAction
) {
}
