package com.stratuslite.audit;

import java.time.Instant;

public record ControlPlaneEvent(
        String id,
        ControlPlaneEventType type,
        ControlPlaneEventSeverity severity,
        String subjectType,
        String subjectId,
        String message,
        Instant createdAt
) {
}
