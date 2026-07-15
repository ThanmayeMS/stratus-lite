package com.stratuslite.incident;

import java.time.Instant;

public record Incident(
        String id,
        IncidentType type,
        IncidentSeverity severity,
        String cellId,
        String message,
        Instant createdAt
) {
}

