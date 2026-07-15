package com.stratuslite.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ControlPlaneEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public ControlPlaneEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ControlPlaneEvent> findLatest(int limit) {
        return jdbcTemplate.query(
                """
                        SELECT id, type, severity, subject_type, subject_id, message, created_at
                        FROM control_plane_events
                        ORDER BY created_at DESC
                        LIMIT ?
                        """,
                this::mapEvent,
                limit
        );
    }

    public void save(ControlPlaneEvent event) {
        jdbcTemplate.update(
                """
                        INSERT INTO control_plane_events (
                            id, type, severity, subject_type, subject_id, message, created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                event.id(),
                event.type().name(),
                event.severity().name(),
                event.subjectType(),
                event.subjectId(),
                event.message(),
                Timestamp.from(event.createdAt())
        );
    }

    private ControlPlaneEvent mapEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ControlPlaneEvent(
                resultSet.getString("id"),
                ControlPlaneEventType.valueOf(resultSet.getString("type")),
                ControlPlaneEventSeverity.valueOf(resultSet.getString("severity")),
                resultSet.getString("subject_type"),
                resultSet.getString("subject_id"),
                resultSet.getString("message"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }
}
