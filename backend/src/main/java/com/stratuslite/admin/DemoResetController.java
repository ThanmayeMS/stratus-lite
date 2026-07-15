package com.stratuslite.admin;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class DemoResetController {

    private final DemoResetService demoResetService;

    public DemoResetController(DemoResetService demoResetService) {
        this.demoResetService = demoResetService;
    }

    @PostMapping("/reset")
    public DemoResetResponse resetDemo() {
        demoResetService.resetDemo();
        return new DemoResetResponse("Demo state reset");
    }
}
