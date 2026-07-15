package com.stratuslite.workload;

import com.stratuslite.common.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkloadService {

    private final WorkloadRepository workloadRepository;
    private final Clock clock;

    @Autowired
    public WorkloadService(WorkloadRepository workloadRepository) {
        this(workloadRepository, Clock.systemUTC());
    }

    WorkloadService(WorkloadRepository workloadRepository, Clock clock) {
        this.workloadRepository = workloadRepository;
        this.clock = clock;
    }

    @Transactional
    public synchronized Workload createWorkload(CreateWorkloadCommand command) {
        String workloadId = "wl-" + UUID.randomUUID();
        Workload workload = Workload.requested(workloadId, command, Instant.now(clock));
        workloadRepository.save(workload);
        return workload;
    }

    @Transactional(readOnly = true)
    public synchronized List<Workload> listWorkloads() {
        return workloadRepository.findAll();
    }

    @Transactional(readOnly = true)
    public synchronized List<Workload> listWorkloadsOnCell(String cellId) {
        return workloadRepository.findByAssignedCellId(cellId);
    }

    @Transactional(readOnly = true)
    public synchronized Workload getWorkload(String workloadId) {
        return workloadRepository.findById(workloadId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Workload %s was not found".formatted(workloadId)
                ));
    }

    @Transactional
    public synchronized Workload markPlaced(String workloadId, String cellId) {
        Workload placed = getWorkload(workloadId).placedOn(cellId, Instant.now(clock));
        workloadRepository.save(placed);
        return placed;
    }

    @Transactional
    public synchronized Workload markMigrated(String workloadId, String cellId) {
        Workload migrated = getWorkload(workloadId).migratedTo(cellId, Instant.now(clock));
        workloadRepository.save(migrated);
        return migrated;
    }

    @Transactional
    public synchronized List<Workload> markAssignedWorkloadsDegraded(String cellId) {
        List<Workload> degradedWorkloads = listWorkloadsOnCell(cellId).stream()
                .map(workload -> workload.degraded(Instant.now(clock)))
                .toList();

        degradedWorkloads.forEach(workloadRepository::save);
        return degradedWorkloads;
    }
}
