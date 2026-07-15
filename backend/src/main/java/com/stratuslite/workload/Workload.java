package com.stratuslite.workload;

import com.stratuslite.fleet.ResourceVector;
import com.stratuslite.fleet.ServiceTier;
import java.time.Instant;
import java.util.Objects;

public record Workload(
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

    public Workload {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(region, "region is required");
        Objects.requireNonNull(tier, "tier is required");
        Objects.requireNonNull(demand, "demand is required");
        Objects.requireNonNull(state, "state is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static Workload requested(String id, CreateWorkloadCommand command, Instant now) {
        return new Workload(
                id,
                command.tenantId(),
                command.region(),
                command.tier(),
                command.demand(),
                WorkloadState.REQUESTED,
                null,
                now,
                now
        );
    }

    public Workload placedOn(String cellId, Instant now) {
        return new Workload(id, tenantId, region, tier, demand, WorkloadState.PLACED, cellId, createdAt, now);
    }

    public WorkloadRequest toPlacementRequest() {
        return new WorkloadRequest(id, tenantId, region, tier, demand);
    }

    public void requirePlaceable() {
        if (state != WorkloadState.REQUESTED) {
            throw new IllegalStateException(
                    "Workload %s cannot be placed from state %s".formatted(id, state)
            );
        }
    }
}

