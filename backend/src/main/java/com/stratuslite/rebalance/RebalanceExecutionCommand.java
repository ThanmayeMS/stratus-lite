package com.stratuslite.rebalance;

import jakarta.validation.constraints.NotBlank;

public record RebalanceExecutionCommand(
        @NotBlank String workloadId,
        @NotBlank String sourceCellId,
        @NotBlank String targetCellId
) {
}
