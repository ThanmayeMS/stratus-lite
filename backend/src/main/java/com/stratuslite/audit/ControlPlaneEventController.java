package com.stratuslite.audit;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class ControlPlaneEventController {

    private final ControlPlaneEventService eventService;

    public ControlPlaneEventController(ControlPlaneEventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public List<ControlPlaneEvent> listEvents(
            @RequestParam(defaultValue = "25") int limit
    ) {
        return eventService.listLatest(limit);
    }
}
