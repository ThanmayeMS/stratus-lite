package com.stratuslite.workload;

import com.stratuslite.fleet.ResourceVector;
import com.stratuslite.fleet.ServiceTier;
import java.util.Objects;

public record WorkloadRequest(
        String id,
        String tenantId,
        String region,
        ServiceTier tier,
        ResourceVector demand
) {

    public WorkloadRequest {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(region, "region is required");
        Objects.requireNonNull(tier, "tier is required");
        Objects.requireNonNull(demand, "demand is required");
    }
}

