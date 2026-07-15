package com.stratuslite.fleet;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cells")
public class FleetController {

    private final FleetService fleetService;

    public FleetController(FleetService fleetService) {
        this.fleetService = fleetService;
    }

    @GetMapping
    public List<CellView> listCells() {
        return fleetService.listCells().stream()
                .map(CellView::from)
                .toList();
    }
}

