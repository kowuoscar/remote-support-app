package com.remotesupport.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * The V31 migration that turns every Repair Request and Repair Fee into Other
 * (request-types-and-flow spec, Types; other-replaces-repair ticket AC: "A migration turns every
 * Repair Request and Repair Fee into Other, setting the description 'Repair' on a Request that had
 * none"). Mirrors {@link SimCardCarrierMigrationTest}'s pattern: a throwaway database in the shared
 * Postgres container, migrated to just before V31, loaded with a fixture, then migrated through it.
 */
class RepairToOtherMigrationTest {

  private static final String TENANT = "b0000000-0000-0000-0000-000000000001";
  private static final String CLIENT = "b0000000-0000-0000-0000-0000000000b1";
  private static final String AGENT = "b0000000-0000-0000-0000-0000000000d1";
  private static final String CONTRACT = "b0000000-0000-0000-0000-0000000000c1";
  private static final String TESTER_USER = "b0000000-0000-0000-0000-00000000ee01";
  private static final String AGENT_USER = "b0000000-0000-0000-0000-00000000ee02";
  private static final String TESTER = "b0000000-0000-0000-0000-0000000000e1";

  private static final String REPAIR_REQUEST_NO_DESCRIPTION = "b0000000-0000-0000-0000-0000000da001";
  private static final String REPAIR_REQUEST_WITH_DESCRIPTION = "b0000000-0000-0000-0000-0000000da002";
  private static final String TOPUP_REQUEST = "b0000000-0000-0000-0000-0000000da003";

  private static final String REPAIR_FEE = "b0000000-0000-0000-0000-0000000fee01";
  private static final String TOPUP_FEE = "b0000000-0000-0000-0000-0000000fee02";

  private static final String CLIENT_INVOICE = "b0000000-0000-0000-0000-0000000ca001";

  private String database;
  private String url;

  @BeforeEach
  void migrateAFreshDatabaseToJustBeforeV31() throws SQLException {
    database = "v31_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection admin = connect(IntegrationTest.POSTGRES.getJdbcUrl());
        Statement statement = admin.createStatement()) {
      statement.execute("CREATE DATABASE " + database);
    }
    String base = IntegrationTest.POSTGRES.getJdbcUrl();
    url = base.substring(0, base.lastIndexOf('/') + 1) + database;
    flyway(latestVersionBefore("31")).migrate();
  }

  /** Whichever migration comes last before the given target. */
  private String latestVersionBefore(String target) {
    return Arrays.stream(flyway("latest").info().all())
        .map(MigrationInfo::getVersion)
        .filter(version -> version.compareTo(MigrationVersion.fromVersion(target)) < 0)
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
  void rewritesRepairRequestsAndFeesToOtherWithoutMovingAnyInvoiceTotal() throws SQLException {
    insertFixture();
    Map<String, String> feesBefore = feeAmountsAndBillingMonths();
    List<String> invoicesBefore = clientInvoices();
    List<String> feeSnapshotsBefore = clientInvoiceFeeSnapshots();

    flyway("31").migrate();

    // Every Repair Request becomes Other; one that had no description gets "Repair", one that
    // already had a description keeps it.
    assertThat(requestTypeAndDescription(REPAIR_REQUEST_NO_DESCRIPTION)).isEqualTo("OTHER|Repair");
    assertThat(requestTypeAndDescription(REPAIR_REQUEST_WITH_DESCRIPTION))
        .isEqualTo("OTHER|Cracked screen, replaced on-site");
    // A Request of another type is untouched.
    assertThat(requestTypeAndDescription(TOPUP_REQUEST)).isEqualTo("TOPUP|");

    // Every Repair Fee becomes Other; another type is untouched.
    assertThat(feeType(REPAIR_FEE)).isEqualTo("OTHER");
    assertThat(feeType(TOPUP_FEE)).isEqualTo("TOPUP");

    // No Fee amount or billing month moved, and the sent Client Invoice's snapshot — and which
    // Fees it pinned — is byte-for-byte identical, so its total is unchanged (other-replaces-
    // repair ticket AC).
    assertThat(feeAmountsAndBillingMonths()).isEqualTo(feesBefore);
    assertThat(clientInvoices()).isEqualTo(invoicesBefore);
    assertThat(clientInvoiceFeeSnapshots()).isEqualTo(feeSnapshotsBefore);

    // REPAIR is gone from both CHECK constraints, not merely unused.
    assertThatThrownBy(
            () ->
                execute(
                    "INSERT INTO requests (id, tenant_id, contract_id, tester_id, type, status,"
                        + " raised_by_user_id, agent_authored) VALUES (gen_random_uuid(), '"
                        + TENANT
                        + "', '"
                        + CONTRACT
                        + "', '"
                        + TESTER
                        + "', 'REPAIR', 'SUBMITTED', '"
                        + TESTER_USER
                        + "', FALSE)"))
        .isInstanceOf(SQLException.class);
    assertThatThrownBy(
            () ->
                execute(
                    "INSERT INTO fees (id, tenant_id, contract_id, request_id, fee_type, amount,"
                        + " currency, billing_month) VALUES (gen_random_uuid(), '"
                        + TENANT
                        + "', '"
                        + CONTRACT
                        + "', '"
                        + TOPUP_REQUEST
                        + "', 'REPAIR', 10.00, 'USD', DATE '2026-01-01')"))
        .isInstanceOf(SQLException.class);
  }

  private void insertFixture() throws SQLException {
    execute(
        "INSERT INTO tenants (id, name) VALUES ('" + TENANT + "', 'Migration tenant')",
        "INSERT INTO clients (id, tenant_id, name) VALUES ('" + CLIENT + "', '" + TENANT + "', 'Client')",
        "INSERT INTO agents (id, tenant_id, name, country, currency, salary_amount) VALUES ('"
            + AGENT
            + "', '"
            + TENANT
            + "', 'Agent', 'UNITED_STATES', 'USD', 1000)",
        "INSERT INTO contracts (id, tenant_id, client_id, agent_id, currency) VALUES ('"
            + CONTRACT
            + "', '"
            + TENANT
            + "', '"
            + CLIENT
            + "', '"
            + AGENT
            + "', 'USD')",
        "INSERT INTO users (id, tenant_id, username, password_hash, role) VALUES ('"
            + TESTER_USER
            + "', '"
            + TENANT
            + "', 'tester@migration.example', 'x', 'TESTER')",
        "INSERT INTO users (id, tenant_id, username, password_hash, role) VALUES ('"
            + AGENT_USER
            + "', '"
            + TENANT
            + "', 'agent@migration.example', 'x', 'AGENT')",
        "INSERT INTO testers (id, tenant_id, client_id, user_id, is_primary_contact) VALUES ('"
            + TESTER
            + "', '"
            + TENANT
            + "', '"
            + CLIENT
            + "', '"
            + TESTER_USER
            + "', true)",
        "INSERT INTO requests (id, tenant_id, contract_id, tester_id, type, status, raised_by_user_id,"
            + " agent_authored) VALUES ('"
            + REPAIR_REQUEST_NO_DESCRIPTION
            + "', '"
            + TENANT
            + "', '"
            + CONTRACT
            + "', '"
            + TESTER
            + "', 'REPAIR', 'COMPLETED', '"
            + TESTER_USER
            + "', FALSE)",
        "INSERT INTO requests (id, tenant_id, contract_id, tester_id, type, status, raised_by_user_id,"
            + " agent_authored, description) VALUES ('"
            + REPAIR_REQUEST_WITH_DESCRIPTION
            + "', '"
            + TENANT
            + "', '"
            + CONTRACT
            + "', '"
            + TESTER
            + "', 'REPAIR', 'COMPLETED', '"
            + AGENT_USER
            + "', TRUE, 'Cracked screen, replaced on-site')",
        "INSERT INTO requests (id, tenant_id, contract_id, tester_id, type, status, raised_by_user_id,"
            + " agent_authored) VALUES ('"
            + TOPUP_REQUEST
            + "', '"
            + TENANT
            + "', '"
            + CONTRACT
            + "', '"
            + TESTER
            + "', 'TOPUP', 'COMPLETED', '"
            + TESTER_USER
            + "', FALSE)",
        "INSERT INTO fees (id, tenant_id, contract_id, request_id, fee_type, amount, currency,"
            + " billing_month) VALUES ('"
            + REPAIR_FEE
            + "', '"
            + TENANT
            + "', '"
            + CONTRACT
            + "', '"
            + REPAIR_REQUEST_NO_DESCRIPTION
            + "', 'REPAIR', 75.50, 'USD', DATE '2026-08-01')",
        "INSERT INTO fees (id, tenant_id, contract_id, request_id, fee_type, amount, currency,"
            + " billing_month) VALUES ('"
            + TOPUP_FEE
            + "', '"
            + TENANT
            + "', '"
            + CONTRACT
            + "', '"
            + TOPUP_REQUEST
            + "', 'TOPUP', 15.00, 'USD', DATE '2026-08-01')",
        // A sent Client Invoice whose frozen snapshot pins the Repair Fee — the exact shape the
        // ticket's AC calls out ("a sent Client Invoice, with totals identical before and after").
        "INSERT INTO client_invoices (id, tenant_id, contract_id, billing_month, status, currency,"
            + " sent_at, snapshot_base_amount) VALUES ('"
            + CLIENT_INVOICE
            + "', '"
            + TENANT
            + "', '"
            + CONTRACT
            + "', DATE '2026-08-01', 'SENT', 'USD', now(), 40.00)",
        "INSERT INTO client_invoice_fee_snapshots (id, tenant_id, client_invoice_id, fee_id) VALUES"
            + " (gen_random_uuid(), '"
            + TENANT
            + "', '"
            + CLIENT_INVOICE
            + "', '"
            + REPAIR_FEE
            + "')",
        "INSERT INTO client_invoice_fee_snapshots (id, tenant_id, client_invoice_id, fee_id) VALUES"
            + " (gen_random_uuid(), '"
            + TENANT
            + "', '"
            + CLIENT_INVOICE
            + "', '"
            + TOPUP_FEE
            + "')");
  }

  /** {@code type|description}, with a null description rendered as the empty string. */
  private String requestTypeAndDescription(String requestId) throws SQLException {
    return single("SELECT type || '|' || COALESCE(description, '') FROM requests WHERE id = '" + requestId + "'");
  }

  private String feeType(String feeId) throws SQLException {
    return single("SELECT fee_type FROM fees WHERE id = '" + feeId + "'");
  }

  private Map<String, String> feeAmountsAndBillingMonths() throws SQLException {
    Map<String, String> fees = new TreeMap<>();
    try (Connection connection = connect(url);
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("SELECT id, amount, billing_month FROM fees ORDER BY id")) {
      while (rows.next()) {
        fees.put(rows.getString(1), rows.getString(2) + "|" + rows.getString(3));
      }
    }
    return fees;
  }

  private List<String> clientInvoices() throws SQLException {
    return rowsOf("SELECT id, contract_id, billing_month, status, currency, snapshot_base_amount"
        + " FROM client_invoices ORDER BY id");
  }

  private List<String> clientInvoiceFeeSnapshots() throws SQLException {
    return rowsOf(
        "SELECT client_invoice_id, fee_id FROM client_invoice_fee_snapshots ORDER BY client_invoice_id, fee_id");
  }

  private List<String> rowsOf(String sql) throws SQLException {
    List<String> rows = new ArrayList<>();
    try (Connection connection = connect(url);
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql)) {
      int columns = resultSet.getMetaData().getColumnCount();
      while (resultSet.next()) {
        StringBuilder row = new StringBuilder();
        for (int column = 1; column <= columns; column++) {
          row.append(resultSet.getString(column)).append('|');
        }
        rows.add(row.toString());
      }
    }
    return rows;
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
