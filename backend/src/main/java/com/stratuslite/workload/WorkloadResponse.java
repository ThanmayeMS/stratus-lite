package com.stratuslite.workload;

import com.stratuslite.fleet.ResourceVector;
import com.stratuslite.fleet.ServiceTier;
import java.time.Instant;

public record WorkloadResponse(
        String id,
        String tenantId,
        String region,
        ServiceTier tier,
        ResourceVector demand,
        WorkloadState state,
        String assignedCellId,
        Instant createdAt,
        Instant updatedAt
) {

    public static WorkloadResponse from(Workload workload) {
        return new WorkloadResponse(
                workload.id(),
                workload.tenantId(),
                workload.region(),
                workload.tier(),
                workload.demand(),
                workload.state(),
                workload.assignedCellId(),
                workload.createdAt(),
                workload.updatedAt()
        );
    }
}

