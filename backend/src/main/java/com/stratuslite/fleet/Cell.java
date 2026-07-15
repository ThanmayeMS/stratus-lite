package com.stratuslite.fleet;

import java.util.Objects;

public record Cell(
        String id,
        String region,
        ServiceTier tier,
        CellStatus status,
        ResourceVector totalCapacity,
        ResourceVector usedCapacity
) {

    public Cell {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(region, "region is required");
        Objects.requireNonNull(tier, "tier is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(totalCapacity, "totalCapacity is required");
        Objects.requireNonNull(usedCapacity, "usedCapacity is required");
        if (!totalCapacity.canFit(usedCapacity)) {
            throw new IllegalArgumentException("used capacity cannot exceed total capacity");
        }
    }

    public ResourceVector availableCapacity() {
        return totalCapacity.minus(usedCapacity);
    }

    public double maxUtilization() {
        return usedCapacity.maxUtilizationAgainst(totalCapacity);
    }

    public boolean isOverloaded(double utilizationThreshold) {
        return maxUtilization() >= utilizationThreshold;
    }

    public boolean canHost(ServiceTier requiredTier, String requiredRegion, ResourceVector demand) {
        return status == CellStatus.ACTIVE
                && region.equals(requiredRegion)
                && tier.supports(requiredTier)
                && availableCapacity().canFit(demand);
    }

    public Cell reserve(ResourceVector demand) {
        if (!availableCapacity().canFit(demand)) {
            throw new IllegalArgumentException("cell does not have enough available capacity");
        }
        return new Cell(id, region, tier, status, totalCapacity, usedCapacity.plus(demand));
    }

    public Cell applyLoad(ResourceVector load) {
        ResourceVector newUsage = usedCapacity.plus(load);
        if (!totalCapacity.canFit(newUsage)) {
            throw new IllegalArgumentException("load spike would exceed total cell capacity");
        }
        return new Cell(id, region, tier, status, totalCapacity, newUsage);
    }

    public Cell withStatus(CellStatus newStatus) {
        return new Cell(id, region, tier, newStatus, totalCapacity, usedCapacity);
    }
}
