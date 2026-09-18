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
 * The V34/V35 migrations that give a SIM Card an Installed-in Smartphone (spec.md Solution --
 * Fleet model; sim-installed-in-smartphone ticket). Mirrors {@code SmartphoneOwnerMigrationTest}'s
 * throwaway-database pattern.
 */
class SimCardInstalledInSmartphoneMigrationTest {

  private static final String SEEDED_DEMO_SMARTPHONE = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
  private static final String SEEDED_DEMO_SIM_CARD = "cccccccc-cccc-cccc-cccc-cccccccccccc";
  private static final String SEEDED_PLAN_SIM_CARD = "56565656-5656-5656-5656-565656565656";
  private static final String SEEDED_SECOND_CONTRACT_SIM_CARD = "34343434-3434-3434-3434-343434343434";

  private String database;
  private String url;

  @BeforeEach
  void migrateAFreshDatabaseToJustBeforeV34() throws SQLException {
    database = "v34_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection admin = connect(IntegrationTest.POSTGRES.getJdbcUrl());
        Statement statement = admin.createStatement()) {
      statement.execute("CREATE DATABASE " + database);
    }
    String base = IntegrationTest.POSTGRES.getJdbcUrl();
    url = base.substring(0, base.lastIndexOf('/') + 1) + database;
    flyway(latestVersionBefore(34)).migrate();
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
  void v34AddsAnOptionalInstalledInSmartphoneColumn() throws SQLException {
    flyway("34").migrate();

    assertThat(
            count(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'sim_cards'"
                    + " AND column_name = 'installed_in_smartphone_id'"))
        .isEqualTo(1);

    // Nullable: a SIM Card can still be inserted with no Installed-in Smartphone.
    execute(
        "INSERT INTO sim_cards (id, tenant_id, contract_id, number, flavor, status)"
            + " SELECT gen_random_uuid(), tenant_id, contract_id, '+1-555-0000', 'PREPAID', 'ACTIVE'"
            + " FROM sim_cards LIMIT 1");
  }

  @Test
  void v35SeedsInstalledAndUninstalledSimCardsIncludingOneSmartphoneWithTwo() throws SQLException {
    flyway("34").migrate();
    flyway("35").migrate();

    assertThat(installedInOf(SEEDED_DEMO_SIM_CARD)).isEqualTo(SEEDED_DEMO_SMARTPHONE);
    assertThat(installedInOf(SEEDED_PLAN_SIM_CARD)).isEqualTo(SEEDED_DEMO_SMARTPHONE);
    assertThat(installedInOf(SEEDED_SECOND_CONTRACT_SIM_CARD)).isNull();

    assertThat(
            count(
                "SELECT count(*) FROM sim_cards WHERE installed_in_smartphone_id = '"
                    + SEEDED_DEMO_SMARTPHONE
                    + "'"))
        .isEqualTo(2);
  }

  private String installedInOf(String simCardId) throws SQLException {
    return single("SELECT installed_in_smartphone_id FROM sim_cards WHERE id = '" + simCardId + "'");
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
