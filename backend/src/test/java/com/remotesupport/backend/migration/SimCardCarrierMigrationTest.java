package com.remotesupport.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.remotesupport.backend.support.IntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The V23 migration that turns a SIM Card's free-text carrier into a reference to a Carrier
 * (carrier-catalog spec, Migration; sim-card-carrier ticket). It runs on a throwaway database in
 * the shared Postgres container: migrated to just before V23, loaded with SIM Cards whose carrier
 * names differ only in case or whitespace, then migrated through V23.
 */
class SimCardCarrierMigrationTest {

  private static final String TENANT = "a0000000-0000-0000-0000-000000000001";
  private static final String US_CONTRACT = "a0000000-0000-0000-0000-0000000000c1";
  private static final String UK_CONTRACT = "a0000000-0000-0000-0000-0000000000c2";
  private static final String EXISTING_CRICKET = "a0000000-0000-0000-0000-0000000000a1";
  private static final String ARCHIVED_SPRINT = "a0000000-0000-0000-0000-0000000000a2";
  private static final String SEEDED_VERIZON = "c0000000-0000-0000-0000-000000000003";
  private static final String SEEDED_DEMO_SIM_CARD = "cccccccc-cccc-cccc-cccc-cccccccccccc";

  private String database;
  private String url;

  @BeforeEach
  void migrateAFreshDatabaseToJustBeforeV23() throws SQLException {
    database = "v23_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection admin = connect(IntegrationTest.POSTGRES.getJdbcUrl());
        Statement statement = admin.createStatement()) {
      statement.execute("CREATE DATABASE " + database);
    }
    String base = IntegrationTest.POSTGRES.getJdbcUrl();
    url = base.substring(0, base.lastIndexOf('/') + 1) + database;
    flyway(latestVersionBeforeV23()).migrate();
  }

  /** Whichever migration comes last before V23 — V21/V22 belong to a sibling ticket. */
  private String latestVersionBeforeV23() {
    return Arrays.stream(flyway("latest").info().all())
        .map(MigrationInfo::getVersion)
        .filter(version -> version.compareTo(MigrationVersion.fromVersion("23")) < 0)
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
  void groupsFreeTextCarriersPerCountryReusesActiveCarriersAndLinksEverySimCard() throws SQLException {
    insertFixture();
    Map<String, String> feesBefore = monthlyFees();
    List<String> invoicesBefore = clientInvoices();

    flyway("23").migrate();

    // Three spellings of one name are one Carrier, named after the oldest SIM Card's spelling.
    String boostUs = carrierOf("us-boost-1");
    assertThat(carrierOf("us-boost-2")).isEqualTo(boostUs);
    assertThat(carrierOf("us-boost-3")).isEqualTo(boostUs);
    assertThat(carrier(boostUs)).isEqualTo("UNITED_STATES|Boost Mobile|active");

    // The same name in another Country is another Carrier.
    String boostUk = carrierOf("uk-boost");
    assertThat(boostUk).isNotEqualTo(boostUs);
    assertThat(carrier(boostUk)).isEqualTo("UNITED_KINGDOM|Boost Mobile|active");

    // An active Carrier of that name is reused; an archived one is not.
    assertThat(carrierOf("us-cricket")).isEqualTo(EXISTING_CRICKET);
    String sprint = carrierOf("us-sprint");
    assertThat(sprint).isNotEqualTo(ARCHIVED_SPRINT);
    assertThat(carrier(sprint)).isEqualTo("UNITED_STATES|SPRINT|active");

    // No carrier, or a blank one, stays unlinked.
    assertThat(carrierOf("us-none")).isNull();
    assertThat(carrierOf("us-blank")).isNull();

    // Exactly the Carriers above exist in the tenant: nothing extra was created.
    assertThat(count("SELECT count(*) FROM carriers WHERE tenant_id = '" + TENANT + "'")).isEqualTo(5);

    // The seeded demo SIM Card's "Verizon" reuses the seeded US Verizon.
    assertThat(carrierOf(SEEDED_DEMO_SIM_CARD)).isEqualTo(SEEDED_VERIZON);

    // No monthly fee and no Client Invoice moved.
    assertThat(monthlyFees()).isEqualTo(feesBefore);
    assertThat(clientInvoices()).isEqualTo(invoicesBefore);

    // The free-text column is gone.
    assertThat(
            count(
                "SELECT count(*) FROM information_schema.columns"
                    + " WHERE table_name = 'sim_cards' AND column_name = 'carrier'"))
        .isZero();
  }

  private void insertFixture() throws SQLException {
    execute(
        "INSERT INTO tenants (id, name) VALUES ('" + TENANT + "', 'Migration tenant')",
        "INSERT INTO clients (id, tenant_id, name) VALUES ('a0000000-0000-0000-0000-0000000000b1', '"
            + TENANT
            + "', 'Client')",
        "INSERT INTO agents (id, tenant_id, name, country, currency, salary_amount) VALUES"
            + " ('a0000000-0000-0000-0000-0000000000d1', '" + TENANT + "', 'US Agent', 'UNITED_STATES', 'USD', 1000),"
            + " ('a0000000-0000-0000-0000-0000000000d2', '" + TENANT + "', 'UK Agent', 'UNITED_KINGDOM', 'GBP', 1000)",
        "INSERT INTO contracts (id, tenant_id, client_id, agent_id, currency) VALUES"
            + " ('" + US_CONTRACT + "', '" + TENANT + "', 'a0000000-0000-0000-0000-0000000000b1',"
            + " 'a0000000-0000-0000-0000-0000000000d1', 'USD'),"
            + " ('" + UK_CONTRACT + "', '" + TENANT + "', 'a0000000-0000-0000-0000-0000000000b1',"
            + " 'a0000000-0000-0000-0000-0000000000d2', 'GBP')",
        "INSERT INTO carriers (id, tenant_id, country, name, archived_at) VALUES"
            + " ('" + EXISTING_CRICKET + "', '" + TENANT + "', 'UNITED_STATES', 'cricket', NULL),"
            + " ('" + ARCHIVED_SPRINT + "', '" + TENANT + "', 'UNITED_STATES', 'Sprint', now())",
        "INSERT INTO client_invoices (id, tenant_id, contract_id, billing_month, status, currency)"
            + " VALUES ('a0000000-0000-0000-0000-0000000000e1', '" + TENANT + "', '" + US_CONTRACT
            + "', DATE '2026-08-01', 'DRAFT', 'USD')");
    insertSimCard("us-boost-1", US_CONTRACT, "Boost Mobile", "POSTPAID", "30.00", "2026-01-01");
    insertSimCard("us-boost-2", US_CONTRACT, "boost mobile ", "POSTPAID", "31.50", "2026-02-01");
    insertSimCard("us-boost-3", US_CONTRACT, "  BOOST MOBILE", "PREPAID", null, "2026-03-01");
    insertSimCard("us-cricket", US_CONTRACT, " Cricket", "POSTPAID", "12.00", "2026-01-02");
    insertSimCard("us-sprint", US_CONTRACT, "SPRINT", "PREPAID", null, "2026-01-03");
    insertSimCard("us-none", US_CONTRACT, null, "POSTPAID", "9.99", "2026-01-04");
    insertSimCard("us-blank", US_CONTRACT, "   ", "PREPAID", null, "2026-01-05");
    insertSimCard("uk-boost", UK_CONTRACT, "Boost Mobile", "POSTPAID", "20.00", "2026-01-06");
  }

  private void insertSimCard(
      String number, String contract, String carrier, String flavor, String fee, String createdAt)
      throws SQLException {
    execute(
        "INSERT INTO sim_cards (id, tenant_id, contract_id, number, carrier, flavor, monthly_fee_amount,"
            + " status, created_at) VALUES (gen_random_uuid(), '" + TENANT + "', '" + contract + "', '"
            + number + "', " + (carrier == null ? "NULL" : "'" + carrier + "'") + ", '" + flavor + "', "
            + (fee == null ? "NULL" : fee) + ", 'ACTIVE', TIMESTAMPTZ '" + createdAt + "T00:00:00Z')");
  }

  /** The Carrier id of the SIM Card with this number, or with this id. */
  private String carrierOf(String numberOrId) throws SQLException {
    return single(
        "SELECT carrier_id FROM sim_cards WHERE number = '" + numberOrId + "' OR id::text = '" + numberOrId + "'");
  }

  private String carrier(String id) throws SQLException {
    return single(
        "SELECT country || '|' || name || '|' || CASE WHEN archived_at IS NULL THEN 'active' ELSE 'archived' END"
            + " FROM carriers WHERE id = '" + id + "'");
  }

  private Map<String, String> monthlyFees() throws SQLException {
    Map<String, String> fees = new TreeMap<>();
    try (Connection connection = connect(url);
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT id, monthly_fee_amount FROM sim_cards")) {
      while (rows.next()) {
        fees.put(rows.getString(1), rows.getString(2));
      }
    }
    return fees;
  }

  private List<String> clientInvoices() throws SQLException {
    List<String> invoices = new ArrayList<>();
    try (Connection connection = connect(url);
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT * FROM client_invoices ORDER BY id")) {
      int columns = rows.getMetaData().getColumnCount();
      while (rows.next()) {
        StringBuilder row = new StringBuilder();
        for (int column = 1; column <= columns; column++) {
          row.append(rows.getString(column)).append('|');
        }
        invoices.add(row.toString());
      }
    }
    return invoices;
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
