package com.stratuslite.workload;

import com.stratuslite.fleet.ResourceVector;
import com.stratuslite.fleet.ServiceTier;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkloadRepository {

    private final JdbcTemplate jdbcTemplate;

    public WorkloadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Workload> findAll() {
        return jdbcTemplate.query(
                """
                        SELECT id, tenant_id, region, tier,
                               demand_cpu_cores, demand_memory_gb, demand_storage_gb, demand_iops,
                               state, assigned_cell_id, created_at, updated_at
                        FROM workloads
                        ORDER BY created_at
                        """,
                this::mapWorkload
        );
    }

    public List<Workload> findByAssignedCellId(String cellId) {
        return jdbcTemplate.query(
                """
                        SELECT id, tenant_id, region, tier,
                               demand_cpu_cores, demand_memory_gb, demand_storage_gb, demand_iops,
                               state, assigned_cell_id, created_at, updated_at
                        FROM workloads
                        WHERE assigned_cell_id = ?
                        ORDER BY created_at
                        """,
                this::mapWorkload,
                cellId
        );
    }

    public Optional<Workload> findById(String workloadId) {
        List<Workload> workloads = jdbcTemplate.query(
                """
                        SELECT id, tenant_id, region, tier,
                               demand_cpu_cores, demand_memory_gb, demand_storage_gb, demand_iops,
                               state, assigned_cell_id, created_at, updated_at
                        FROM workloads
                        WHERE id = ?
                        """,
                this::mapWorkload,
                workloadId
        );
        return workloads.stream().findFirst();
    }

    public void save(Workload workload) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE workloads
                        SET tenant_id = ?,
                            region = ?,
                            tier = ?,
                            demand_cpu_cores = ?,
                            demand_memory_gb = ?,
                            demand_storage_gb = ?,
                            demand_iops = ?,
                            state = ?,
                            assigned_cell_id = ?,
                            created_at = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                workload.tenantId(),
                workload.region(),
                workload.tier().name(),
                workload.demand().cpuCores(),
                workload.demand().memoryGb(),
                workload.demand().storageGb(),
                workload.demand().iops(),
                workload.state().name(),
                workload.assignedCellId(),
                Timestamp.from(workload.createdAt()),
                Timestamp.from(workload.updatedAt()),
                workload.id()
        );

        if (updated == 0) {
            jdbcTemplate.update(
                    """
                            INSERT INTO workloads (
                                id, tenant_id, region, tier,
                                demand_cpu_cores, demand_memory_gb, demand_storage_gb, demand_iops,
                                state, assigned_cell_id, created_at, updated_at
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    workload.id(),
                    workload.tenantId(),
                    workload.region(),
                    workload.tier().name(),
                    workload.demand().cpuCores(),
                    workload.demand().memoryGb(),
                    workload.demand().storageGb(),
                    workload.demand().iops(),
                    workload.state().name(),
                    workload.assignedCellId(),
                    Timestamp.from(workload.createdAt()),
                    Timestamp.from(workload.updatedAt())
            );
        }
    }

    private Workload mapWorkload(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Workload(
                resultSet.getString("id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("region"),
                ServiceTier.valueOf(resultSet.getString("tier")),
                new ResourceVector(
                        resultSet.getInt("demand_cpu_cores"),
                        resultSet.getInt("demand_memory_gb"),
                        resultSet.getInt("demand_storage_gb"),
                        resultSet.getInt("demand_iops")
                ),
                WorkloadState.valueOf(resultSet.getString("state")),
                resultSet.getString("assigned_cell_id"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
