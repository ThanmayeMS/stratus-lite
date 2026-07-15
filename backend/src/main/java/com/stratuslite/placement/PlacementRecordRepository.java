package com.stratuslite.placement;

import com.stratuslite.common.ResourceNotFoundException;
import com.stratuslite.fleet.Cell;
import com.stratuslite.fleet.CellRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlacementRecordRepository {

    private final JdbcTemplate jdbcTemplate;
    private final CellRepository cellRepository;

    public PlacementRecordRepository(JdbcTemplate jdbcTemplate, CellRepository cellRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.cellRepository = cellRepository;
    }

    public List<PlacementRecord> findAll() {
        return jdbcTemplate.query(
                """
                        SELECT workload_id, selected_cell_id, strategy, explanation, decided_at
                        FROM placement_records
                        ORDER BY decided_at
                        """,
                this::mapPlacementRecord
        );
    }

    public Optional<PlacementRecord> findByWorkloadId(String workloadId) {
        List<PlacementRecord> records = jdbcTemplate.query(
                """
                        SELECT workload_id, selected_cell_id, strategy, explanation, decided_at
                        FROM placement_records
                        WHERE workload_id = ?
                        """,
                this::mapPlacementRecord,
                workloadId
        );
        return records.stream().findFirst();
    }

    public void save(PlacementRecord record) {
        int updated = jdbcTemplate.update(
                """
                        UPDATE placement_records
                        SET selected_cell_id = ?,
                            strategy = ?,
                            explanation = ?,
                            decided_at = ?
                        WHERE workload_id = ?
                        """,
                record.selectedCellId(),
                record.strategy().name(),
                record.explanation(),
                Timestamp.from(record.decidedAt()),
                record.workloadId()
        );

        if (updated == 0) {
            jdbcTemplate.update(
                    """
                            INSERT INTO placement_records (
                                workload_id, selected_cell_id, strategy, explanation, decided_at
                            )
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    record.workloadId(),
                    record.selectedCellId(),
                    record.strategy().name(),
                    record.explanation(),
                    Timestamp.from(record.decidedAt())
            );
        }

        jdbcTemplate.update(
                "DELETE FROM placement_candidates WHERE placement_workload_id = ?",
                record.workloadId()
        );

        for (int index = 0; index < record.candidates().size(); index++) {
            CandidateScore candidate = record.candidates().get(index);
            jdbcTemplate.update(
                    """
                            INSERT INTO placement_candidates (
                                placement_workload_id, candidate_order, cell_id, score, reason
                            )
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    record.workloadId(),
                    index,
                    candidate.cell().id(),
                    candidate.score(),
                    candidate.reason()
            );
        }
    }

    private PlacementRecord mapPlacementRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        String workloadId = resultSet.getString("workload_id");
        return new PlacementRecord(
                workloadId,
                resultSet.getString("selected_cell_id"),
                PlacementStrategy.valueOf(resultSet.getString("strategy")),
                findCandidates(workloadId),
                resultSet.getString("explanation"),
                resultSet.getTimestamp("decided_at").toInstant()
        );
    }

    private List<CandidateScore> findCandidates(String workloadId) {
        return jdbcTemplate.query(
                """
                        SELECT cell_id, score, reason
                        FROM placement_candidates
                        WHERE placement_workload_id = ?
                        ORDER BY candidate_order
                        """,
                this::mapCandidateScore,
                workloadId
        );
    }

    private CandidateScore mapCandidateScore(ResultSet resultSet, int rowNumber) throws SQLException {
        String cellId = resultSet.getString("cell_id");
        Cell cell = cellRepository.findById(cellId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Candidate cell %s was not found".formatted(cellId)
                ));
        return new CandidateScore(
                cell,
                resultSet.getDouble("score"),
                resultSet.getString("reason")
        );
    }
}
