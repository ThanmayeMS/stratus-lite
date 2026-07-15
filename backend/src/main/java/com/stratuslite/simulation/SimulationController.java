package com.stratuslite.simulation;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulations")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/load-spike")
    public SimulationResult simulateLoadSpike(@Valid @RequestBody LoadSpikeCommand command) {
        return simulationService.simulateLoadSpike(command);
    }

    @PostMapping("/cell-failure")
    public SimulationResult simulateCellFailure(@Valid @RequestBody CellFailureCommand command) {
        return simulationService.simulateCellFailure(command);
    }
}

