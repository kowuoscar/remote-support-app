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
 * The V54 migration that trims the Flyway seed down to the test baseline (demo-data-story
 * spec.md Solution "Test baseline"; trim-seed-to-test-baseline ticket). Mirrors {@code
 * SimCardCarrierMigrationTest}'s throwaway-database pattern: migrated to just before V54 (so
 * every earlier seed migration, including the ad hoc demo rows V17/V18/V23/V26/V29/V31/V45
 * inserted, has actually run), loaded with an unrelated hand-made Client, then migrated through
 * V54.
 */
class TrimSeedToTestBaselineMigrationTest {

  private static final String TENANT = "11111111-1111-1111-1111-111111111111";

  // The demo rows every earlier seed migration inserted, that V54 must remove.
  private static final String DEMO_TESTER_USER = "99999999-9999-9999-9999-999999999999";
  private static final String DEMO_CLIENT = "77777777-7777-7777-7777-777777777777";
  private static final String DEMO_AGENT = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee";
  private static final String DEMO_TESTER = "88888888-8888-8888-8888-888888888888";
  private static final String DEMO_CONTRACT = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
  private static final String DEMO_SMARTPHONE = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
  private static final String DEMO_SIM_CARD = "cccccccc-cccc-cccc-cccc-cccccccccccc";
  private static final String SECOND_DEMO_AGENT = "ffffffff-ffff-ffff-ffff-ffffffffffff";
  private static final String SECOND_DEMO_CONTRACT = "dddddddd-dddd-dddd-dddd-dddddddddddd";
  private static final String SECOND_DEMO_SMARTPHONE = "12121212-1212-1212-1212-121212121212";
  private static final String SECOND_DEMO_SIM_CARD = "34343434-3434-3434-3434-343434343434";
  private static final String PLAN_SIM_CARD = "56565656-5656-5656-5656-565656565656";
  private static final String DEMO_TOPUP_REQUEST = "f1000000-0000-0000-0000-000000000001";
  private static final String DEMO_TOPUP_FEE = "f2000000-0000-0000-0000-000000000001";
  private static final String DEMO_OTHER_REQUEST = "f3000000-0000-0000-0000-000000000001";
  private static final String DEMO_PENDING_APPROVAL_REQUEST = "f4400000-0000-0000-0000-000000000001";
  private static final String DEMO_REJECTED_REQUEST = "f4400000-0000-0000-0000-000000000002";

  // The test baseline that must remain, untouched.
  private static final String MANAGER_USER = "22222222-2222-2222-2222-222222222222";
  private static final String AGENT_USER = "33333333-3333-3333-3333-333333333333";
  private static final String TESTER_USER = "44444444-4444-4444-4444-444444444444";
  private static final String SEEDED_AGENT = "55555555-5555-5555-5555-555555555555";
  private static final String SEEDED_STANDING_SALARY = "66666666-6666-6666-6666-666666666666";
  private static final String SEEDED_ATT = "c0000000-0000-0000-0000-000000000001";
  private static final String SEEDED_TMOBILE = "c0000000-0000-0000-0000-000000000002";
  private static final String SEEDED_VERIZON = "c0000000-0000-0000-0000-000000000003";
  private static final String SEEDED_SPRINT = "c0000000-0000-0000-0000-000000000004";

  private String database;
  private String url;

  @BeforeEach
  void migrateAFreshDatabaseToJustBeforeV54() throws SQLException {
    database = "v54_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection admin = connect(IntegrationTest.POSTGRES.getJdbcUrl());
        Statement statement = admin.createStatement()) {
      statement.execute("CREATE DATABASE " + database);
    }
    String base = IntegrationTest.POSTGRES.getJdbcUrl();
    url = base.substring(0, base.lastIndexOf('/') + 1) + database;
    flyway(latestVersionBefore(54)).migrate();
  }

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
  void removesEveryDemoRowLeavingTheBaselineAndAnyHandMadeRowUntouched() throws SQLException {
    // Sanity check: the demo rows are really there before V54 runs.
    assertThat(exists("users", DEMO_TESTER_USER)).isTrue();
    assertThat(exists("clients", DEMO_CLIENT)).isTrue();
    assertThat(exists("agents", DEMO_AGENT)).isTrue();
    assertThat(exists("agents", SECOND_DEMO_AGENT)).isTrue();
    assertThat(exists("testers", DEMO_TESTER)).isTrue();
    assertThat(exists("contracts", DEMO_CONTRACT)).isTrue();
    assertThat(exists("contracts", SECOND_DEMO_CONTRACT)).isTrue();
    assertThat(exists("smartphones", DEMO_SMARTPHONE)).isTrue();
    assertThat(exists("smartphones", SECOND_DEMO_SMARTPHONE)).isTrue();
    assertThat(exists("sim_cards", DEMO_SIM_CARD)).isTrue();
    assertThat(exists("sim_cards", SECOND_DEMO_SIM_CARD)).isTrue();
    assertThat(exists("sim_cards", PLAN_SIM_CARD)).isTrue();
    assertThat(exists("requests", DEMO_TOPUP_REQUEST)).isTrue();
    assertThat(exists("fees", DEMO_TOPUP_FEE)).isTrue();
    assertThat(exists("requests", DEMO_OTHER_REQUEST)).isTrue();
    assertThat(exists("requests", DEMO_PENDING_APPROVAL_REQUEST)).isTrue();
    assertThat(exists("requests", DEMO_REJECTED_REQUEST)).isTrue();
    // V23's own dynamic Carrier creation, for the second Contract's "EE" SIM Card.
    String demoUkCarrierId = single("SELECT carrier_id FROM sim_cards WHERE id = '" + SECOND_DEMO_SIM_CARD + "'");
    assertThat(demoUkCarrierId).isNotNull();
    assertThat(count("SELECT count(*) FROM carriers WHERE id = '" + demoUkCarrierId + "'")).isEqualTo(1);

    // An unrelated hand-made row, exactly like a developer adding one locally.
    String handMadeClientId = UUID.randomUUID().toString();
    execute(
        "INSERT INTO clients (id, tenant_id, name) VALUES ('"
            + handMadeClientId
            + "', '"
            + TENANT
            + "', 'Hand-made Client')");

    flyway("54").migrate();

    // Every demo row is gone.
    assertThat(exists("users", DEMO_TESTER_USER)).isFalse();
    assertThat(exists("clients", DEMO_CLIENT)).isFalse();
    assertThat(exists("agents", DEMO_AGENT)).isFalse();
    assertThat(exists("agents", SECOND_DEMO_AGENT)).isFalse();
    assertThat(exists("testers", DEMO_TESTER)).isFalse();
    assertThat(exists("contracts", DEMO_CONTRACT)).isFalse();
    assertThat(exists("contracts", SECOND_DEMO_CONTRACT)).isFalse();
    assertThat(exists("smartphones", DEMO_SMARTPHONE)).isFalse();
    assertThat(exists("smartphones", SECOND_DEMO_SMARTPHONE)).isFalse();
    assertThat(exists("sim_cards", DEMO_SIM_CARD)).isFalse();
    assertThat(exists("sim_cards", SECOND_DEMO_SIM_CARD)).isFalse();
    assertThat(exists("sim_cards", PLAN_SIM_CARD)).isFalse();
    assertThat(exists("requests", DEMO_TOPUP_REQUEST)).isFalse();
    assertThat(exists("fees", DEMO_TOPUP_FEE)).isFalse();
    assertThat(exists("requests", DEMO_OTHER_REQUEST)).isFalse();
    assertThat(exists("requests", DEMO_PENDING_APPROVAL_REQUEST)).isFalse();
    assertThat(exists("requests", DEMO_REJECTED_REQUEST)).isFalse();
    assertThat(count("SELECT count(*) FROM carriers WHERE id = '" + demoUkCarrierId + "'")).isZero();

    // The hand-made row survives untouched.
    assertThat(exists("clients", handMadeClientId)).isTrue();
    assertThat(single("SELECT name FROM clients WHERE id = '" + handMadeClientId + "'"))
        .isEqualTo("Hand-made Client");

    // The test baseline remains, exactly.
    assertThat(exists("users", MANAGER_USER)).isTrue();
    assertThat(exists("users", AGENT_USER)).isTrue();
    assertThat(exists("users", TESTER_USER)).isTrue();
    assertThat(exists("agents", SEEDED_AGENT)).isTrue();
    assertThat(exists("agent_standing_amounts", SEEDED_STANDING_SALARY)).isTrue();
    assertThat(exists("carriers", SEEDED_ATT)).isTrue();
    assertThat(exists("carriers", SEEDED_TMOBILE)).isTrue();
    assertThat(exists("carriers", SEEDED_VERIZON)).isTrue();
    assertThat(exists("carriers", SEEDED_SPRINT)).isTrue();
    // Exactly the United States catalog's four Carriers remain in the tenant -- the demo Client's
    // dynamic United Kingdom one is the only other Carrier that ever existed.
    assertThat(count("SELECT count(*) FROM carriers WHERE tenant_id = '" + TENANT + "'")).isEqualTo(4);
    assertThat(count("SELECT count(*) FROM topup_options")).isEqualTo(7);
    assertThat(count("SELECT count(*) FROM postpaid_plans")).isEqualTo(7);
    // Only the hand-made Client (this test) and no other Client remains.
    assertThat(count("SELECT count(*) FROM clients WHERE tenant_id = '" + TENANT + "'")).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM contracts WHERE tenant_id = '" + TENANT + "'")).isZero();
    assertThat(count("SELECT count(*) FROM requests WHERE tenant_id = '" + TENANT + "'")).isZero();
    assertThat(count("SELECT count(*) FROM fees WHERE tenant_id = '" + TENANT + "'")).isZero();
  }

  @Test
  void migratesCleanlyOnAFreshDatabaseLeavingExactlyTheBaseline() throws SQLException {
    flyway("latest").migrate();

    assertThat(count("SELECT count(*) FROM tenants")).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM users")).isEqualTo(3);
    assertThat(count("SELECT count(*) FROM agents")).isEqualTo(1);
    assertThat(exists("agents", SEEDED_AGENT)).isTrue();
    assertThat(count("SELECT count(*) FROM clients")).isZero();
    assertThat(count("SELECT count(*) FROM testers")).isZero();
    assertThat(count("SELECT count(*) FROM contracts")).isZero();
    assertThat(count("SELECT count(*) FROM smartphones")).isZero();
    assertThat(count("SELECT count(*) FROM sim_cards")).isZero();
    assertThat(count("SELECT count(*) FROM requests")).isZero();
    assertThat(count("SELECT count(*) FROM fees")).isZero();
    assertThat(count("SELECT count(*) FROM carriers")).isEqualTo(4);
    assertThat(count("SELECT count(*) FROM topup_options")).isEqualTo(7);
    assertThat(count("SELECT count(*) FROM postpaid_plans")).isEqualTo(7);
  }

  private boolean exists(String table, String id) throws SQLException {
    return count("SELECT count(*) FROM " + table + " WHERE id = '" + id + "'") > 0;
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
