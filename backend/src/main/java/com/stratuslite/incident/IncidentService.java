package com.stratuslite.incident;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final Clock clock;

    @Autowired
    public IncidentService(IncidentRepository incidentRepository) {
        this(incidentRepository, Clock.systemUTC());
    }

    IncidentService(IncidentRepository incidentRepository, Clock clock) {
        this.incidentRepository = incidentRepository;
        this.clock = clock;
    }

    @Transactional
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
        incidentRepository.save(incident);
        return incident;
    }

    @Transactional(readOnly = true)
    public synchronized List<Incident> listIncidents() {
        return incidentRepository.findAll();
    }
}
