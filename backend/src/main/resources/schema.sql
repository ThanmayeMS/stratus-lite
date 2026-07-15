CREATE TABLE IF NOT EXISTS cells (
    id VARCHAR(80) PRIMARY KEY,
    region VARCHAR(80) NOT NULL,
    tier VARCHAR(40) NOT NULL,
    status VARCHAR(40) NOT NULL,
    total_cpu_cores INTEGER NOT NULL,
    total_memory_gb INTEGER NOT NULL,
    total_storage_gb INTEGER NOT NULL,
    total_iops INTEGER NOT NULL,
    used_cpu_cores INTEGER NOT NULL,
    used_memory_gb INTEGER NOT NULL,
    used_storage_gb INTEGER NOT NULL,
    used_iops INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS workloads (
    id VARCHAR(80) PRIMARY KEY,
    tenant_id VARCHAR(120) NOT NULL,
    region VARCHAR(80) NOT NULL,
    tier VARCHAR(40) NOT NULL,
    demand_cpu_cores INTEGER NOT NULL,
    demand_memory_gb INTEGER NOT NULL,
    demand_storage_gb INTEGER NOT NULL,
    demand_iops INTEGER NOT NULL,
    state VARCHAR(40) NOT NULL,
    assigned_cell_id VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_workloads_assigned_cell_id
    ON workloads (assigned_cell_id);

CREATE TABLE IF NOT EXISTS placement_records (
    workload_id VARCHAR(80) PRIMARY KEY,
    selected_cell_id VARCHAR(80) NOT NULL,
    strategy VARCHAR(40) NOT NULL,
    explanation VARCHAR(500) NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS placement_candidates (
    placement_workload_id VARCHAR(80) NOT NULL,
    candidate_order INTEGER NOT NULL,
    cell_id VARCHAR(80) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (placement_workload_id, candidate_order),
    CONSTRAINT fk_placement_candidates_record
        FOREIGN KEY (placement_workload_id)
        REFERENCES placement_records (workload_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS incidents (
    id VARCHAR(80) PRIMARY KEY,
    type VARCHAR(60) NOT NULL,
    severity VARCHAR(40) NOT NULL,
    cell_id VARCHAR(80) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
