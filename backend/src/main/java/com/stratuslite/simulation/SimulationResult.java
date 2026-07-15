package com.stratuslite.simulation;

import com.stratuslite.fleet.CellStatus;
import com.stratuslite.incident.Incident;

public record SimulationResult(
        String cellId,
        CellStatus cellStatus,
        double maxUtilizationPercent,
        int affectedWorkloads,
        Incident incident
) {
}

