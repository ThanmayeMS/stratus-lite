package com.stratuslite.workload;

import com.stratuslite.fleet.ResourceVector;
import com.stratuslite.fleet.ServiceTier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateWorkloadCommand(
        @NotBlank String tenantId,
        @NotBlank String region,
        @NotNull ServiceTier tier,
        @Valid @NotNull ResourceVector demand
) {
}

