package com.aerosense.aerosense.common;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

  private final JdbcTemplate jdbcTemplate;

  public HealthController(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @GetMapping
  public HealthResponse health() {
    String databaseStatus = "UP";
    try {
      jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    } catch (DataAccessException exception) {
      databaseStatus = "DOWN";
    }

    String overallStatus = databaseStatus.equals("UP") ? "UP" : "DEGRADED";
    return new HealthResponse(
        overallStatus, Map.of("postgres", databaseStatus, "analytics", "UNKNOWN"));
  }
}
