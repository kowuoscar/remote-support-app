package com.remotesupport.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

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
 * The V32/V33 migrations that give a Smartphone an Owner and drop its free-text "assigned to"
 * (spec.md Solution -- Fleet model; smartphone-owner-and-optional-serial ticket). Mirrors {@code
 * SimCardCarrierMigrationTest}'s throwaway-database pattern: migrated to just before V32, loaded
 * with a fresh Smartphone alongside the seeded ones (V17/V18, which insert "assigned to" values
 * and are never edited), then migrated through V32 and V33.
 */
class SmartphoneOwnerMigrationTest {

  private static final String TENANT = "11111111-1111-1111-1111-111111111111";
  private static final String SEEDED_DEMO_CONTRACT = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
  private static final String SEEDED_DEMO_SMARTPHONE = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
  private static final String SEEDED_SECOND_DEMO_SMARTPHONE = "12121212-1212-1212-1212-121212121212";
  private static final String FRESH_SMARTPHONE = "a0000000-0000-0000-0000-0000000000f1";

  private String database;
  private String url;

  @BeforeEach
  void migrateAFreshDatabaseToJustBeforeV32() throws SQLException {
    database = "v32_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection admin = connect(IntegrationTest.POSTGRES.getJdbcUrl());
        Statement statement = admin.createStatement()) {
      statement.execute("CREATE DATABASE " + database);
    }
    String base = IntegrationTest.POSTGRES.getJdbcUrl();
    url = base.substring(0, base.lastIndexOf('/') + 1) + database;
    flyway(latestVersionBefore(32)).migrate();
  }

  /** Whichever migration comes last before {@code version} -- V30/V31 belong to a sibling ticket. */
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
  void v32DefaultsEveryExistingSmartphoneToCompanyExceptTheSeededClientOwnedOne() throws SQLException {
    // Before V32: "assigned to" still exists, no "owner" column yet.
    execute(
        "INSERT INTO smartphones (id, tenant_id, contract_id, model, serial, assigned_to, status)"
            + " VALUES ('" + FRESH_SMARTPHONE + "', '" + TENANT + "', '" + SEEDED_DEMO_CONTRACT
            + "', 'Nokia 3310', 'SN-FRESH-1', 'Some desk', 'ACTIVE')");

    flyway("32").migrate();

    assertThat(ownerOf(FRESH_SMARTPHONE)).isEqualTo("COMPANY");
    assertThat(ownerOf(SEEDED_DEMO_SMARTPHONE)).isEqualTo("COMPANY");
    // Seed data holds both Owners (ticket AC) without a new row: the demo Client's second
    // Contract's seeded Smartphone (V18) becomes Client-owned.
    assertThat(ownerOf(SEEDED_SECOND_DEMO_SMARTPHONE)).isEqualTo("CLIENT");

    // "assigned to" still exists at V32 -- V33 is what drops it.
    assertThat(
            count(
                "SELECT count(*) FROM information_schema.columns"
                    + " WHERE table_name = 'smartphones' AND column_name = 'assigned_to'"))
        .isEqualTo(1);

    // owner is NOT NULL: no smartphone can be inserted without one.
    assertThatInsertingWithoutOwnerFails();
  }

  @Test
  void v33DropsAssignedToAndMakesSerialOptional() throws SQLException {
    flyway("32").migrate();
    flyway("33").migrate();

    assertThat(
            count(
                "SELECT count(*) FROM information_schema.columns"
                    + " WHERE table_name = 'smartphones' AND column_name = 'assigned_to'"))
        .isZero();

    // A Smartphone can now be created without a serial.
    execute(
        "INSERT INTO smartphones (id, tenant_id, contract_id, model, owner, status)"
            + " VALUES (gen_random_uuid(), '" + TENANT + "', '" + SEEDED_DEMO_CONTRACT
            + "', 'Pixel 9', 'COMPANY', 'ACTIVE')");
    assertThat(count("SELECT count(*) FROM smartphones WHERE model = 'Pixel 9' AND serial IS NULL"))
        .isEqualTo(1);
  }

  private void assertThatInsertingWithoutOwnerFails() {
    try {
      execute(
          "INSERT INTO smartphones (id, tenant_id, contract_id, model, serial, status)"
              + " VALUES (gen_random_uuid(), '" + TENANT + "', '" + SEEDED_DEMO_CONTRACT
              + "', 'No Owner', 'SN-NO-OWNER', 'ACTIVE')");
      throw new AssertionError("Expected the missing NOT NULL owner to be rejected");
    } catch (RuntimeException expected) {
      // Wraps the SQLException NOT NULL violation -- the point of this assertion.
    }
  }

  private String ownerOf(String id) throws SQLException {
    return single("SELECT owner FROM smartphones WHERE id = '" + id + "'");
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

  private void execute(String... sql) {
    try (Connection connection = connect(url);
        Statement statement = connection.createStatement()) {
      for (String each : sql) {
        statement.execute(each);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
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
