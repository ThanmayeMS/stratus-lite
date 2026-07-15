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
    eligible BOOLEAN NOT NULL DEFAULT TRUE,
    score DOUBLE PRECISION NOT NULL,
    projected_utilization_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    policy_summary VARCHAR(700) NOT NULL DEFAULT '',
    reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (placement_workload_id, candidate_order),
    CONSTRAINT fk_placement_candidates_record
        FOREIGN KEY (placement_workload_id)
        REFERENCES placement_records (workload_id)
        ON DELETE CASCADE
);

ALTER TABLE placement_candidates
    ADD COLUMN IF NOT EXISTS eligible BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE placement_candidates
    ADD COLUMN IF NOT EXISTS projected_utilization_percent DOUBLE PRECISION NOT NULL DEFAULT 0;

ALTER TABLE placement_candidates
    ADD COLUMN IF NOT EXISTS policy_summary VARCHAR(700) NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS incidents (
    id VARCHAR(80) PRIMARY KEY,
    type VARCHAR(60) NOT NULL,
    severity VARCHAR(40) NOT NULL,
    cell_id VARCHAR(80) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS control_plane_events (
    id VARCHAR(80) PRIMARY KEY,
    type VARCHAR(80) NOT NULL,
    severity VARCHAR(40) NOT NULL,
    subject_type VARCHAR(80) NOT NULL,
    subject_id VARCHAR(120) NOT NULL,
    message VARCHAR(600) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_control_plane_events_created_at
    ON control_plane_events (created_at DESC);

CREATE TABLE IF NOT EXISTS rebalance_executions (
    id VARCHAR(80) PRIMARY KEY,
    workload_id VARCHAR(80) NOT NULL,
    source_cell_id VARCHAR(80) NOT NULL,
    target_cell_id VARCHAR(80) NOT NULL,
    status VARCHAR(40) NOT NULL,
    explanation VARCHAR(700) NOT NULL DEFAULT '',
    operator_action VARCHAR(500) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    rolled_back_at TIMESTAMP WITH TIME ZONE
);

ALTER TABLE rebalance_executions
    ADD COLUMN IF NOT EXISTS explanation VARCHAR(700) NOT NULL DEFAULT '';

ALTER TABLE rebalance_executions
    ADD COLUMN IF NOT EXISTS operator_action VARCHAR(500) NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_rebalance_executions_created_at
    ON rebalance_executions (created_at DESC);
