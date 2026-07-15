package com.stratuslite.observability;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class OperationalMetricsController {

    private final OperationalMetricsService metricsService;

    public OperationalMetricsController(OperationalMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/operations")
    public OperationalMetrics operations() {
        return metricsService.currentMetrics();
    }
}
