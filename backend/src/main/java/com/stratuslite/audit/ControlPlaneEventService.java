package com.stratuslite.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ControlPlaneEventService {

    private static final int MAX_LIMIT = 100;

    private final ControlPlaneEventRepository eventRepository;
    private final Clock clock;

    @Autowired
    public ControlPlaneEventService(ControlPlaneEventRepository eventRepository) {
        this(eventRepository, Clock.systemUTC());
    }

    ControlPlaneEventService(ControlPlaneEventRepository eventRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Transactional
    public ControlPlaneEvent record(
            ControlPlaneEventType type,
            ControlPlaneEventSeverity severity,
            String subjectType,
            String subjectId,
            String message
    ) {
        ControlPlaneEvent event = new ControlPlaneEvent(
                "evt-" + UUID.randomUUID(),
                type,
                severity,
                subjectType,
                subjectId,
                message,
                Instant.now(clock)
        );
        eventRepository.save(event);
        return event;
    }

    @Transactional(readOnly = true)
    public List<ControlPlaneEvent> listLatest(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
        return eventRepository.findLatest(limit);
    }
}
