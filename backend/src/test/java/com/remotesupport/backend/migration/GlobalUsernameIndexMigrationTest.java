package com.remotesupport.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.remotesupport.backend.support.IntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The V55 migration that adds a global, case-insensitive, trim-insensitive unique index over
 * {@code users.username} (globally-unique-usernames spec.md Solution "The database is what makes
 * the rule true"; global-username-index ticket). Mirrors {@code
 * TrimSeedToTestBaselineMigrationTest}'s/{@code SmartphoneOwnerMigrationTest}'s throwaway-database
 * pattern: migrated to just before V55, then through it.
 */
class GlobalUsernameIndexMigrationTest {

  private static final String SEEDED_TENANT = "11111111-1111-1111-1111-111111111111";

  private String database;
  private String url;

  @BeforeEach
  void migrateAFreshDatabaseToJustBeforeV55() throws SQLException {
    database = "v55_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection admin = connect(IntegrationTest.POSTGRES.getJdbcUrl());
        Statement statement = admin.createStatement()) {
      statement.execute("CREATE DATABASE " + database);
    }
    String base = IntegrationTest.POSTGRES.getJdbcUrl();
    url = base.substring(0, base.lastIndexOf('/') + 1) + database;
    flyway(latestVersionBefore(55)).migrate();
  }

  /** Whichever migration comes last before {@code version}. */
  private String latestVersionBefore(int version) {
    return Arrays.stream(flyway("latest").info().all())
        .map(MigrationInfo::getVersion)
        .filter(v -> v.compareTo(MigrationVersion.fromVersion(String.valueOf(version))) < 0)
        .max(Comparator.naturalOrder())
        .orElseThrow()
        .getVersion();
  }

  @AfterEach
  void dropTheDatabase() throws SQLException {
    try (Connection admin = connect(IntegrationTest.POSTGRES.getJdbcUrl());
        Statement statement = admin.createStatement()) {
      statement.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
    }
  }

  @Test
  void aCleanDatabaseReachesV55WithBothUsernameIndexesInPlace() throws SQLException {
    flyway("55").migrate();

    assertThat(indexExists("uq_users_username_global")).isTrue();
    assertThat(indexExists("uq_users_tenant_username")).isTrue();
  }

  @Test
  void aHandInsertedDuplicateNormalizedUsernameAcrossTenantsFailsTheMigrationNamingIt()
      throws SQLException {
    UUID secondTenantId = UUID.randomUUID();
    execute("INSERT INTO tenants (id, name) VALUES ('" + secondTenantId + "', 'Second Tenant')");
    // Differ by case and surrounding whitespace -- the pre-check normalizes exactly as the index
    // itself will, so a duplicate hidden by either is still caught.
    execute(
        "INSERT INTO users (id, tenant_id, username, password_hash, role)"
            + " VALUES (gen_random_uuid(), '"
            + SEEDED_TENANT
            + "', '  Duplicate.User@Example.com ', 'x', 'MANAGER')");
    execute(
        "INSERT INTO users (id, tenant_id, username, password_hash, role)"
            + " VALUES (gen_random_uuid(), '"
            + secondTenantId
            + "', 'duplicate.user@example.com', 'x', 'MANAGER')");

    assertThatThrownBy(() -> flyway("55").migrate())
        .as("V55 must stop rather than pick a winner")
        .hasMessageContaining("duplicate.user@example.com")
        .hasMessageContaining("2 rows")
        .hasMessageContaining(SEEDED_TENANT)
        .hasMessageContaining(secondTenantId.toString());

    // Nothing was written: the deployment stays on V54, with no V55 row at all in the history.
    assertThat(indexExists("uq_users_username_global")).isFalse();
    assertThat(count("SELECT count(*) FROM flyway_schema_history WHERE version = '55'")).isZero();
  }

  private boolean indexExists(String name) throws SQLException {
    return count("SELECT count(*) FROM pg_indexes WHERE indexname = '" + name + "'") > 0;
  }

  private long count(String sql) throws SQLException {
    return Long.parseLong(single(sql));
  }

  private String single(String sql) throws SQLException {
    try (Connection connection = connect(url);
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      assertThat(rows.next()).as("a row for: %s", sql).isTrue();
      return rows.getString(1);
    }
  }

  private void execute(String... sql) throws SQLException {
    try (Connection connection = connect(url);
        Statement statement = connection.createStatement()) {
      for (String each : sql) {
        statement.execute(each);
      }
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
