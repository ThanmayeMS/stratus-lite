package com.stratuslite.fleet;

public record CellView(
        String id,
        String region,
        ServiceTier tier,
        CellStatus status,
        ResourceVector totalCapacity,
        ResourceVector usedCapacity,
        ResourceVector availableCapacity,
        double utilizationPercent
) {

    public static CellView from(Cell cell) {
        return new CellView(
                cell.id(),
                cell.region(),
                cell.tier(),
                cell.status(),
                cell.totalCapacity(),
                cell.usedCapacity(),
                cell.availableCapacity(),
                Math.round(cell.usedCapacity().averageUtilizationAgainst(cell.totalCapacity()) * 10_000.0) / 100.0
        );
    }
}

