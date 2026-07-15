package com.stratuslite.workload;

import com.stratuslite.common.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkloadService {

    private final Map<String, Workload> workloads = new LinkedHashMap<>();
    private final Clock clock;

    public WorkloadService() {
        this(Clock.systemUTC());
    }

    WorkloadService(Clock clock) {
        this.clock = clock;
    }

    public synchronized Workload createWorkload(CreateWorkloadCommand command) {
        String workloadId = "wl-" + UUID.randomUUID();
        Workload workload = Workload.requested(workloadId, command, Instant.now(clock));
        workloads.put(workloadId, workload);
        return workload;
    }

    public synchronized List<Workload> listWorkloads() {
        return workloads.values().stream()
                .sorted(Comparator.comparing(Workload::createdAt))
                .toList();
    }

    public synchronized List<Workload> listWorkloadsOnCell(String cellId) {
        return workloads.values().stream()
                .filter(workload -> workload.isAssignedTo(cellId))
                .sorted(Comparator.comparing(Workload::createdAt))
                .toList();
    }

    public synchronized Workload getWorkload(String workloadId) {
        Workload workload = workloads.get(workloadId);
        if (workload == null) {
            throw new ResourceNotFoundException("Workload %s was not found".formatted(workloadId));
        }
        return workload;
    }

    public synchronized Workload markPlaced(String workloadId, String cellId) {
        Workload placed = getWorkload(workloadId).placedOn(cellId, Instant.now(clock));
        workloads.put(workloadId, placed);
        return placed;
    }

    public synchronized List<Workload> markAssignedWorkloadsDegraded(String cellId) {
        List<Workload> degradedWorkloads = listWorkloadsOnCell(cellId).stream()
                .map(workload -> workload.degraded(Instant.now(clock)))
                .toList();

        degradedWorkloads.forEach(workload -> workloads.put(workload.id(), workload));
        return degradedWorkloads;
    }
}
