package com.stratuslite.rebalance;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rebalance")
public class RebalanceController {

    private final RebalanceService rebalanceService;

    public RebalanceController(RebalanceService rebalanceService) {
        this.rebalanceService = rebalanceService;
    }

    @GetMapping("/recommendations")
    public List<RebalanceRecommendation> recommendations() {
        return rebalanceService.recommendations();
    }

    @GetMapping("/executions")
    public List<RebalanceExecutionRecord> executions() {
        return rebalanceService.executions();
    }

    @PostMapping("/executions")
    public RebalanceExecutionResult execute(@Valid @RequestBody RebalanceExecutionCommand command) {
        return rebalanceService.execute(command);
    }

    @PostMapping("/executions/{executionId}/rollback")
    public RebalanceExecutionResult rollback(@PathVariable String executionId) {
        return rebalanceService.rollback(executionId);
    }
}
