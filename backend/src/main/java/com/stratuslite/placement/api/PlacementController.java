package com.stratuslite.placement.api;

import com.stratuslite.placement.PlacementRecord;
import com.stratuslite.placement.PlacementService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/placements")
public class PlacementController {

    private final PlacementService placementService;

    public PlacementController(PlacementService placementService) {
        this.placementService = placementService;
    }

    @GetMapping
    public List<PlacementResponse> listPlacements() {
        return placementService.listPlacements().stream()
                .map(PlacementResponse::from)
                .toList();
    }
}

