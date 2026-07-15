package com.stratuslite.reconciler;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciler")
public class ReconcilerController {

    private final ReconcilerService reconcilerService;

    public ReconcilerController(ReconcilerService reconcilerService) {
        this.reconcilerService = reconcilerService;
    }

    @GetMapping("/status")
    public ReconcilerStatus status() {
        return reconcilerService.status();
    }

    @PostMapping("/run")
    public ReconcilerStatus run() {
        return reconcilerService.runOnce();
    }
}
