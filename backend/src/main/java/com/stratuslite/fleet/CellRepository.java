package com.stratuslite.fleet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CellRepository {

    private final JdbcTemplate jdbcTemplate;

    public CellRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Cell> findAll() {
        return jdbcTemplate.query(
                """
                        SELECT id, region, tier, status,
                               total_cpu_cores, total_memory_gb, total_storage_gb, total_iops,
                               used_cpu_cores, used_memory_gb, used_storage_gb, used_iops
                        FROM cells
                        ORDER BY id
                        """,
                this::mapCell
        );
    }

    public Optional<Cell> findById(String cellId) {
        List<Cell> cells = jdbcTemplate.query(
                """
                        SELECT id, region, tier, status,
                               total_cpu_cores, total_memory_gb, total_storage_gb, total_iops,
                               used_cpu_cores, used_memory_gb, used_storage_gb, used_iops
                        FROM cells
                        WHERE id = ?
                        """,
                this::mapCell,
                cellId
        );
        return cells.stream().findFirst();
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cells", Long.class);
        return count == null ? 0 : count;
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM cells");
    }

    public void save(Cell cell) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE cells
                        SET region = ?,
                            tier = ?,
                            status = ?,
                            total_cpu_cores = ?,
                            total_memory_gb = ?,
                            total_storage_gb = ?,
                            total_iops = ?,
                            used_cpu_cores = ?,
                            used_memory_gb = ?,
                            used_storage_gb = ?,
                            used_iops = ?
                        WHERE id = ?
                        """,
                cell.region(),
                cell.tier().name(),
                cell.status().name(),
                cell.totalCapacity().cpuCores(),
                cell.totalCapacity().memoryGb(),
                cell.totalCapacity().storageGb(),
                cell.totalCapacity().iops(),
                cell.usedCapacity().cpuCores(),
                cell.usedCapacity().memoryGb(),
                cell.usedCapacity().storageGb(),
                cell.usedCapacity().iops(),
                cell.id()
        );

        if (updated == 0) {
            jdbcTemplate.update(
                    """
                            INSERT INTO cells (
                                id, region, tier, status,
                                total_cpu_cores, total_memory_gb, total_storage_gb, total_iops,
                                used_cpu_cores, used_memory_gb, used_storage_gb, used_iops
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    cell.id(),
                    cell.region(),
                    cell.tier().name(),
                    cell.status().name(),
                    cell.totalCapacity().cpuCores(),
                    cell.totalCapacity().memoryGb(),
                    cell.totalCapacity().storageGb(),
                    cell.totalCapacity().iops(),
                    cell.usedCapacity().cpuCores(),
                    cell.usedCapacity().memoryGb(),
                    cell.usedCapacity().storageGb(),
                    cell.usedCapacity().iops()
            );
        }
    }

    private Cell mapCell(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Cell(
                resultSet.getString("id"),
                resultSet.getString("region"),
                ServiceTier.valueOf(resultSet.getString("tier")),
                CellStatus.valueOf(resultSet.getString("status")),
                new ResourceVector(
                        resultSet.getInt("total_cpu_cores"),
                        resultSet.getInt("total_memory_gb"),
                        resultSet.getInt("total_storage_gb"),
                        resultSet.getInt("total_iops")
                ),
                new ResourceVector(
                        resultSet.getInt("used_cpu_cores"),
                        resultSet.getInt("used_memory_gb"),
                        resultSet.getInt("used_storage_gb"),
                        resultSet.getInt("used_iops")
                )
        );
    }
}
