package com.stratuslite.rebalance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RebalanceExecutionRepository {

    private final JdbcTemplate jdbcTemplate;

    public RebalanceExecutionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RebalanceExecutionRecord> findAll() {
        return jdbcTemplate.query(
                """
                        SELECT id, workload_id, source_cell_id, target_cell_id, status, created_at, rolled_back_at
                        FROM rebalance_executions
                        ORDER BY created_at DESC
                        """,
                this::mapExecution
        );
    }

    public Optional<RebalanceExecutionRecord> findById(String executionId) {
        List<RebalanceExecutionRecord> records = jdbcTemplate.query(
                """
                        SELECT id, workload_id, source_cell_id, target_cell_id, status, created_at, rolled_back_at
                        FROM rebalance_executions
                        WHERE id = ?
                        """,
                this::mapExecution,
                executionId
        );
        return records.stream().findFirst();
    }

    public void save(RebalanceExecutionRecord record) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE rebalance_executions
                        SET workload_id = ?,
                            source_cell_id = ?,
                            target_cell_id = ?,
                            status = ?,
                            created_at = ?,
                            rolled_back_at = ?
                        WHERE id = ?
                        """,
                record.workloadId(),
                record.sourceCellId(),
                record.targetCellId(),
                record.status().name(),
                Timestamp.from(record.createdAt()),
                timestamp(record.rolledBackAt()),
                record.id()
        );

        if (updated == 0) {
            jdbcTemplate.update(
                    """
                            INSERT INTO rebalance_executions (
                                id, workload_id, source_cell_id, target_cell_id, status, created_at, rolled_back_at
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    record.id(),
                    record.workloadId(),
                    record.sourceCellId(),
                    record.targetCellId(),
                    record.status().name(),
                    Timestamp.from(record.createdAt()),
                    timestamp(record.rolledBackAt())
            );
        }
    }

    private RebalanceExecutionRecord mapExecution(ResultSet resultSet, int rowNumber) throws SQLException {
        Timestamp rolledBackAt = resultSet.getTimestamp("rolled_back_at");
        return new RebalanceExecutionRecord(
                resultSet.getString("id"),
                resultSet.getString("workload_id"),
                resultSet.getString("source_cell_id"),
                resultSet.getString("target_cell_id"),
                RebalanceExecutionStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                rolledBackAt == null ? null : rolledBackAt.toInstant()
        );
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
