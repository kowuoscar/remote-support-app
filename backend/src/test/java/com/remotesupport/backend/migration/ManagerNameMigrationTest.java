package com.remotesupport.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.remotesupport.backend.support.IntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The migration that gives a Login an optional Manager name (real-manager-dashboard spec.md
 * Solution, "Backend: the Manager name"): a nullable {@code users.name}, no data written. Mirrors
 * {@code SmartphoneOwnerMigrationTest}'s throwaway-database pattern: migrated to just before
 * V60, then through it.
 */
class ManagerNameMigrationTest {

  private String database;
  private String url;

  @BeforeEach
  void migrateAFreshDatabaseToJustBeforeV60() throws SQLException {
    database = "v60_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection admin = connect(IntegrationTest.POSTGRES.getJdbcUrl());
        Statement statement = admin.createStatement()) {
      statement.execute("CREATE DATABASE " + database);
    }
    String base = IntegrationTest.POSTGRES.getJdbcUrl();
    url = base.substring(0, base.lastIndexOf('/') + 1) + database;
    flyway("59").migrate();
  }

  @AfterEach
  void dropTheDatabase() throws SQLException {
    try (Connection admin = connect(IntegrationTest.POSTGRES.getJdbcUrl());
        Statement statement = admin.createStatement()) {
      statement.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
    }
  }

  @Test
  void v60AddsANullableNameColumnAndWritesNoData() throws SQLException {
    long usersBefore = count("SELECT count(*) FROM users");
    assertThat(usersBefore).isPositive();

    flyway("60").migrate();

    assertThat(
            count(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'users'"
                    + " AND column_name = 'name' AND is_nullable = 'YES'"
                    + " AND character_maximum_length = 100"))
        .isEqualTo(1);
    assertThat(count("SELECT count(*) FROM users")).isEqualTo(usersBefore);
    assertThat(count("SELECT count(*) FROM users WHERE name IS NOT NULL")).isZero();
    // The seeded Manager (V2) keeps no name.
    assertThat(count("SELECT count(*) FROM users WHERE role = 'MANAGER' AND name IS NULL"))
        .isPositive();
  }

  private long count(String sql) throws SQLException {
    try (Connection connection = connect(url);
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      assertThat(rows.next()).isTrue();
      return rows.getLong(1);
    }
  }

  private Flyway flyway(String target) {
    return Flyway.configure()
        .dataSource(url, IntegrationTest.POSTGRES.getUsername(), IntegrationTest.POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .target(target)
        .load();
  }

  private static Connection connect(String jdbcUrl) throws SQLException {
    return DriverManager.getConnection(
        jdbcUrl, IntegrationTest.POSTGRES.getUsername(), IntegrationTest.POSTGRES.getPassword());
  }
}
