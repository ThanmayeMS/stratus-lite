package com.stratuslite.incident;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IncidentRepository {

    private final JdbcTemplate jdbcTemplate;

    public IncidentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Incident> findAll() {
        return jdbcTemplate.query(
                """
                        SELECT id, type, severity, cell_id, message, created_at
                        FROM incidents
                        ORDER BY created_at
                        """,
                this::mapIncident
        );
    }

    public void save(Incident incident) {
        jdbcTemplate.update(
                """
                        INSERT INTO incidents (id, type, severity, cell_id, message, created_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                incident.id(),
                incident.type().name(),
                incident.severity().name(),
                incident.cellId(),
                incident.message(),
                Timestamp.from(incident.createdAt())
        );
    }

    private Incident mapIncident(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Incident(
                resultSet.getString("id"),
                IncidentType.valueOf(resultSet.getString("type")),
                IncidentSeverity.valueOf(resultSet.getString("severity")),
                resultSet.getString("cell_id"),
                resultSet.getString("message"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }
}
