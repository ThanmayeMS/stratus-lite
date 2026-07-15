package com.stratuslite.placement;

import com.stratuslite.common.ResourceNotFoundException;
import com.stratuslite.fleet.FleetService;
import com.stratuslite.workload.Workload;
import com.stratuslite.workload.WorkloadService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlacementService {

    private final PlacementEngine placementEngine;
    private final FleetService fleetService;
    private final WorkloadService workloadService;
    private final PlacementRecordRepository placementRecordRepository;

    public PlacementService(
            PlacementEngine placementEngine,
            FleetService fleetService,
            WorkloadService workloadService,
            PlacementRecordRepository placementRecordRepository
    ) {
        this.placementEngine = placementEngine;
        this.fleetService = fleetService;
        this.workloadService = workloadService;
        this.placementRecordRepository = placementRecordRepository;
    }

    @Transactional
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
        placementRecordRepository.save(record);
        return record;
    }

    @Transactional(readOnly = true)
    public synchronized PlacementRecord getPlacement(String workloadId) {
        return placementRecordRepository.findByWorkloadId(workloadId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Placement for workload %s was not found".formatted(workloadId)
                ));
    }

    @Transactional(readOnly = true)
    public synchronized List<PlacementRecord> listPlacements() {
        return placementRecordRepository.findAll();
    }
}
