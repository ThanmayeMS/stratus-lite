package com.stratuslite.incident;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IncidentService {

    private final Map<String, Incident> incidents = new LinkedHashMap<>();
    private final Clock clock;

    public IncidentService() {
        this(Clock.systemUTC());
    }

    IncidentService(Clock clock) {
        this.clock = clock;
    }

    public synchronized Incident record(
            IncidentType type,
            IncidentSeverity severity,
            String cellId,
            String message
    ) {
        Incident incident = new Incident(
                "inc-" + UUID.randomUUID(),
                type,
                severity,
                cellId,
                message,
                Instant.now(clock)
        );
        incidents.put(incident.id(), incident);
        return incident;
    }

    public synchronized List<Incident> listIncidents() {
        return incidents.values().stream()
                .sorted(Comparator.comparing(Incident::createdAt))
                .toList();
    }
}

