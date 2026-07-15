package com.stratuslite.admin;

import com.stratuslite.fleet.FleetService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoResetService {

    private final JdbcTemplate jdbcTemplate;
    private final FleetService fleetService;

    public DemoResetService(JdbcTemplate jdbcTemplate, FleetService fleetService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fleetService = fleetService;
    }

    @Transactional
    public void resetDemo() {
        jdbcTemplate.update("DELETE FROM rebalance_executions");
        jdbcTemplate.update("DELETE FROM control_plane_events");
        jdbcTemplate.update("DELETE FROM incidents");
        jdbcTemplate.update("DELETE FROM placement_candidates");
        jdbcTemplate.update("DELETE FROM placement_records");
        jdbcTemplate.update("DELETE FROM workloads");
        fleetService.resetFleet();
    }
}
