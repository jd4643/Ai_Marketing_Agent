package com.marketing.strategy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class StrategyIntelRepositoryIntegrationTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
      .withDatabaseName("marketing_ai")
      .withUsername("postgres")
      .withPassword("postgres");

  @Test
  void analyticsMigrationsSeedTemplatesAndIntelInsertWorks() throws Exception {
    try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
         Statement s = c.createStatement()) {
      String v1 = Files.readString(Path.of("..", "analytics-service", "src", "main", "resources", "db", "migration", "V1__init.sql"));
      String v2 = Files.readString(Path.of("..", "analytics-service", "src", "main", "resources", "db", "migration", "V2__strategy_intelligence.sql"));
      for (String stmt : (v1 + "\n" + v2).split(";\\n")) {
        String sql = stmt.trim();
        if (!sql.isEmpty()) {
          s.execute(sql);
        }
      }
      UUID business = UUID.randomUUID();
      s.execute("INSERT INTO business_profile(id,business_name,industry,created_at,updated_at) VALUES ('" + business + "','Acme','jewelry',NOW(),NOW())");
      var rs = s.executeQuery("SELECT COUNT(*) FROM strategy_template");
      rs.next();
      assertTrue(rs.getInt(1) >= 6);
      s.execute("INSERT INTO strategy_run_intel(id,request_id,business_id,objective,monthly_budget,chosen_template_key,decision_path_json,confidence_score,score_breakdown_json,created_at) VALUES (gen_random_uuid(),gen_random_uuid(), '" + business + "','sales',1200,'ONLINE_MID_TICKET_META_FUNNEL_RETARGET','[]'::jsonb,65,'{}'::jsonb,NOW())");
      var rs2 = s.executeQuery("SELECT COUNT(*) FROM strategy_run_intel");
      rs2.next();
      assertTrue(rs2.getInt(1) > 0);
    }
  }
}
