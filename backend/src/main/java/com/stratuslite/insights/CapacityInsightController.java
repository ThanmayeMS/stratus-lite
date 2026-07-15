package com.stratuslite.insights;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights/capacity")
public class CapacityInsightController {

    private final CapacityInsightService insightService;

    public CapacityInsightController(CapacityInsightService insightService) {
        this.insightService = insightService;
    }

    @GetMapping
    public CapacityInsight currentInsight() {
        return insightService.currentInsight();
    }
}
