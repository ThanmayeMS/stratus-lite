package com.stratuslite.placement;

import com.stratuslite.common.ResourceNotFoundException;
import com.stratuslite.fleet.FleetService;
import com.stratuslite.workload.Workload;
import com.stratuslite.workload.WorkloadService;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PlacementService {

    private final PlacementEngine placementEngine;
    private final FleetService fleetService;
    private final WorkloadService workloadService;
    private final Map<String, PlacementRecord> placementRecords = new LinkedHashMap<>();

    public PlacementService(
            PlacementEngine placementEngine,
            FleetService fleetService,
            WorkloadService workloadService
    ) {
        this.placementEngine = placementEngine;
        this.fleetService = fleetService;
        this.workloadService = workloadService;
    }

    public synchronized PlacementRecord placeWorkload(String workloadId, PlacementStrategy strategy) {
        Workload workload = workloadService.getWorkload(workloadId);
        workload.requirePlaceable();

        PlacementDecision decision = placementEngine.place(
                workload.toPlacementRequest(),
                fleetService.activeCellsSnapshot(),
                strategy
        );

        fleetService.reserveCapacity(decision.selectedCell().id(), workload.demand());
        workloadService.markPlaced(workloadId, decision.selectedCell().id());

        PlacementRecord record = PlacementRecord.from(decision);
        placementRecords.put(workloadId, record);
        return record;
    }

    public synchronized PlacementRecord getPlacement(String workloadId) {
        PlacementRecord record = placementRecords.get(workloadId);
        if (record == null) {
            throw new ResourceNotFoundException("Placement for workload %s was not found".formatted(workloadId));
        }
        return record;
    }

    public synchronized List<PlacementRecord> listPlacements() {
        return placementRecords.values().stream()
                .sorted(Comparator.comparing(PlacementRecord::decidedAt))
                .toList();
    }
}

