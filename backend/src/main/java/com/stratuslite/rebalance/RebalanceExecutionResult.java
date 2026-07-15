package com.stratuslite.rebalance;

import com.stratuslite.workload.WorkloadState;

public record RebalanceExecutionResult(
        String workloadId,
        String sourceCellId,
        String targetCellId,
        WorkloadState state,
        String message
) {
}
