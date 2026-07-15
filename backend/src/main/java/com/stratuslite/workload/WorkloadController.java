package com.stratuslite.workload;

import com.stratuslite.placement.PlacementService;
import com.stratuslite.placement.PlacementStrategy;
import com.stratuslite.placement.api.PlacementResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workloads")
public class WorkloadController {

    private final WorkloadService workloadService;
    private final PlacementService placementService;

    public WorkloadController(WorkloadService workloadService, PlacementService placementService) {
        this.workloadService = workloadService;
        this.placementService = placementService;
    }

    @GetMapping
    public List<WorkloadResponse> listWorkloads() {
        return workloadService.listWorkloads().stream()
                .map(WorkloadResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkloadResponse createWorkload(@Valid @RequestBody CreateWorkloadCommand command) {
        return WorkloadResponse.from(workloadService.createWorkload(command));
    }

    @PostMapping("/{workloadId}/place")
    public PlacementResponse placeWorkload(
            @PathVariable String workloadId,
            @RequestParam(defaultValue = "BEST_FIT") PlacementStrategy strategy
    ) {
        return PlacementResponse.from(placementService.placeWorkload(workloadId, strategy));
    }
}

