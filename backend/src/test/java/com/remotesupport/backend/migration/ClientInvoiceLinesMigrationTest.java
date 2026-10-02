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
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The V56 migration that gives a Client Invoice its own stored lines (edit-client-invoice-lines
 * spec, Migration; store-client-invoice-lines ticket). It runs on a throwaway database in the
 * shared Postgres container: migrated to V55, loaded with a SENT, an APPROVED and a DRAFT Client
 * Invoice, then migrated through V56.
 */
class ClientInvoiceLinesMigrationTest {

  private static final String T = "a0000000-0000-0000-0000-000000000001";
  private static final String CLIENT = "a0000000-0000-0000-0000-0000000000b1";
  private static final String AGENT = "a0000000-0000-0000-0000-0000000000d1";
  private static final String CONTRACT = "a0000000-0000-0000-0000-0000000000c1";
  private static final String TESTER_USER = "a0000000-0000-0000-0000-0000000000f1";
  private static final String TESTER = "a0000000-0000-0000-0000-0000000000f2";
  private static final String REQUEST = "a0000000-0000-0000-0000-0000000000f3";
  private static final String FEE_ONE = "a0000000-0000-0000-0000-0000000000a1";
  private static final String FEE_TWO = "a0000000-0000-0000-0000-0000000000a2";
  private static final String SIM = "a0000000-0000-0000-0000-0000000000a3";
  private static final String SENT = "a0000000-0000-0000-0000-0000000000e1";
  private static final String APPROVED = "a0000000-0000-0000-0000-0000000000e2";
  private static final String DRAFT = "a0000000-0000-0000-0000-0000000000e3";

  private String database;
  private String url;

  @BeforeEach
  void migrateAFreshDatabaseToV55AndLoadInvoices() throws SQLException {
    database = "v56_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection admin = connect(IntegrationTest.POSTGRES.getJdbcUrl());
        Statement statement = admin.createStatement()) {
      statement.execute("CREATE DATABASE " + database);
    }
    String base = IntegrationTest.POSTGRES.getJdbcUrl();
    url = base.substring(0, base.lastIndexOf('/') + 1) + database;
    flyway("55").migrate();
    insertFixture();
  }

  @AfterEach
  void dropTheDatabase() throws SQLException {
    try (Connection admin = connect(IntegrationTest.POSTGRES.getJdbcUrl());
        Statement statement = admin.createStatement()) {
      statement.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
    }
  }

  @Test
  void sentAndApprovedGetOneBaseAmountLineEqualToTheOldBaseAmount() throws SQLException {
    flyway("56").migrate();

    assertThat(
            rows(
                "SELECT client_invoice_id, amount, computed_amount FROM client_invoice_lines"
                    + " WHERE kind = 'BASE_AMOUNT' ORDER BY client_invoice_id"))
        .containsExactly(SENT + "|40.00|40.00", APPROVED + "|55.25|55.25");
  }

  @Test
  void eachFeeSnapshotRowBecomesAFeeLineAtThatFeesAmount() throws SQLException {
    flyway("56").migrate();

    assertThat(
            rows(
                "SELECT client_invoice_id, fee_id, amount, computed_amount FROM client_invoice_lines"
                    + " WHERE kind = 'FEE' ORDER BY client_invoice_id, fee_id"))
        .containsExactly(
            SENT + "|" + FEE_ONE + "|10.00|10.00",
            SENT + "|" + FEE_TWO + "|15.50|15.50",
            APPROVED + "|" + FEE_TWO + "|15.50|15.50");
    assertThat(
            rows(
                "SELECT count(*) FROM client_invoice_lines"
                    + " WHERE edited_at IS NOT NULL OR edited_by IS NOT NULL"))
        .containsExactly("0");
  }

  @Test
  void sentAndApprovedAreMarkedLinesStoredAndDraftIsNot() throws SQLException {
    flyway("56").migrate();

    assertThat(rows("SELECT id, lines_stored::text FROM client_invoices ORDER BY id"))
        .containsExactly(SENT + "|true", APPROVED + "|true", DRAFT + "|false");
  }

  @Test
  void draftGetsNoLines() throws SQLException {
    flyway("56").migrate();

    assertThat(
            rows("SELECT count(*) FROM client_invoice_lines WHERE client_invoice_id = '" + DRAFT + "'"))
        .containsExactly("0");
  }

  @Test
  void snapshotDataAndAgentInvoicesAreUntouched() throws SQLException {
    String invoices = "SELECT id, status, snapshot_base_amount FROM client_invoices ORDER BY id";
    String snapshots = "SELECT * FROM client_invoice_fee_snapshots ORDER BY id";
    String agentInvoices = "SELECT * FROM agent_invoices ORDER BY id";
    List<String> invoicesBefore = rows(invoices);
    List<String> snapshotsBefore = rows(snapshots);
    List<String> agentInvoicesBefore = rows(agentInvoices);
    assertThat(agentInvoicesBefore).isNotEmpty();

    flyway("56").migrate();

    assertThat(rows(invoices)).isEqualTo(invoicesBefore);
    assertThat(rows(snapshots)).isEqualTo(snapshotsBefore);
    assertThat(rows(agentInvoices)).isEqualTo(agentInvoicesBefore);
  }

  @Test
  void tableRefusesNegativeAmountWrongReferenceAndDuplicateLine() throws SQLException {
    flyway("56").migrate();

    // Negative amount.
    assertThatThrownBy(() -> insertLine(DRAFT, "FEE", null, FEE_ONE, "-0.01", "10.00"))
        .isInstanceOf(SQLException.class);
    // Wrong reference for the kind.
    assertThatThrownBy(() -> insertLine(DRAFT, "FEE", null, null, "10.00", "10.00"))
        .isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> insertLine(DRAFT, "POSTPAID_SIM", null, null, "10.00", "10.00"))
        .isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> insertLine(DRAFT, "POSTPAID_SIM", SIM, FEE_ONE, "10.00", "10.00"))
        .isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> insertLine(DRAFT, "BASE_AMOUNT", SIM, null, "10.00", "10.00"))
        .isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> insertLine(DRAFT, "BASE_AMOUNT", null, FEE_ONE, "10.00", "10.00"))
        .isInstanceOf(SQLException.class);

    // The valid shapes are accepted, once each.
    insertLine(DRAFT, "POSTPAID_SIM", SIM, null, "12.00", "10.00");
    insertLine(DRAFT, "FEE", null, FEE_ONE, "10.00", "10.00");
    assertThatThrownBy(() -> insertLine(DRAFT, "POSTPAID_SIM", SIM, null, "13.00", "10.00"))
        .isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> insertLine(DRAFT, "FEE", null, FEE_ONE, "11.00", "10.00"))
        .isInstanceOf(SQLException.class);
  }

  private void insertLine(
      String invoice, String kind, String sim, String fee, String amount, String computed)
      throws SQLException {
    execute(
        "INSERT INTO client_invoice_lines (id, tenant_id, client_invoice_id, kind, sim_card_id, fee_id,"
            + " computed_amount, amount) VALUES (gen_random_uuid(), '"
            + T
            + "', '"
            + invoice
            + "', '"
            + kind
            + "', "
            + (sim == null ? "NULL" : "'" + sim + "'")
            + ", "
            + (fee == null ? "NULL" : "'" + fee + "'")
            + ", "
            + computed
            + ", "
            + amount
            + ")");
  }

  private void insertFixture() throws SQLException {
    execute(
        "INSERT INTO tenants (id, name) VALUES ('" + T + "', 'Migration tenant')",
        "INSERT INTO clients (id, tenant_id, name) VALUES ('" + CLIENT + "', '" + T + "', 'Client')",
        "INSERT INTO agents (id, tenant_id, name, country, currency, salary_amount) VALUES ('"
            + AGENT
            + "', '"
            + T
            + "', 'Agent', 'UNITED_STATES', 'USD', 1000)",
        "INSERT INTO contracts (id, tenant_id, client_id, agent_id, currency) VALUES ('"
            + CONTRACT
            + "', '"
            + T
            + "', '"
            + CLIENT
            + "', '"
            + AGENT
            + "', 'USD')",
        "INSERT INTO users (id, tenant_id, username, password_hash, role) VALUES ('"
            + TESTER_USER
            + "', '"
            + T
            + "', 'tester@migration.example', 'x', 'TESTER')",
        "INSERT INTO testers (id, tenant_id, client_id, user_id, is_primary_contact) VALUES ('"
            + TESTER
            + "', '"
            + T
            + "', '"
            + CLIENT
            + "', '"
            + TESTER_USER
            + "', true)",
        "INSERT INTO requests (id, tenant_id, contract_id, tester_id, type, status, raised_by_user_id,"
            + " agent_authored) VALUES ('"
            + REQUEST
            + "', '"
            + T
            + "', '"
            + CONTRACT
            + "', '"
            + TESTER
            + "', 'TOPUP', 'COMPLETED', '"
            + TESTER_USER
            + "', FALSE)",
        "INSERT INTO fees (id, tenant_id, contract_id, request_id, fee_type, amount, currency,"
            + " billing_month) VALUES ('"
            + FEE_ONE
            + "', '"
            + T
            + "', '"
            + CONTRACT
            + "', '"
            + REQUEST
            + "', 'TOPUP', 10.00, 'USD', DATE '2026-08-01'), ('"
            + FEE_TWO
            + "', '"
            + T
            + "', '"
            + CONTRACT
            + "', '"
            + REQUEST
            + "', 'TOPUP', 15.50, 'USD', DATE '2026-08-01')",
        "INSERT INTO sim_cards (id, tenant_id, contract_id, number, flavor, monthly_fee_amount, status)"
            + " VALUES ('"
            + SIM
            + "', '"
            + T
            + "', '"
            + CONTRACT
            + "', 'sim-1', 'POSTPAID', 30.00, 'ACTIVE')",
        "INSERT INTO client_invoices (id, tenant_id, contract_id, billing_month, status, currency,"
            + " sent_at, approved_at, snapshot_base_amount) VALUES ('"
            + SENT
            + "', '"
            + T
            + "', '"
            + CONTRACT
            + "', DATE '2026-08-01', 'SENT', 'USD', now(), NULL, 40.00), ('"
            + APPROVED
            + "', '"
            + T
            + "', '"
            + CONTRACT
            + "', DATE '2026-09-01', 'APPROVED', 'USD', now(), now(), 55.25), ('"
            + DRAFT
            + "', '"
            + T
            + "', '"
            + CONTRACT
            + "', DATE '2026-10-01', 'DRAFT', 'USD', NULL, NULL, NULL)",
        "INSERT INTO client_invoice_fee_snapshots (id, tenant_id, client_invoice_id, fee_id) VALUES"
            + " (gen_random_uuid(), '"
            + T
            + "', '"
            + SENT
            + "', '"
            + FEE_ONE
            + "'), (gen_random_uuid(), '"
            + T
            + "', '"
            + SENT
            + "', '"
            + FEE_TWO
            + "'), (gen_random_uuid(), '"
            + T
            + "', '"
            + APPROVED
            + "', '"
            + FEE_TWO
            + "')",
        "INSERT INTO agent_invoices (id, tenant_id, agent_id, billing_month, status, currency, sent_at,"
            + " snapshot_local_support_fees, snapshot_salary) VALUES (gen_random_uuid(), '"
            + T
            + "', '"
            + AGENT
            + "', DATE '2026-08-01', 'SENT', 'USD', now(), 25.50, 1000.00)");
  }

  private List<String> rows(String sql) throws SQLException {
    List<String> result = new ArrayList<>();
    try (Connection connection = connect(url);
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      int columns = rows.getMetaData().getColumnCount();
      while (rows.next()) {
        StringBuilder row = new StringBuilder();
        for (int column = 1; column <= columns; column++) {
          row.append(column > 1 ? "|" : "").append(rows.getString(column));
        }
        result.add(row.toString());
      }
    }
    return result;
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
        .dataSource(
            url, IntegrationTest.POSTGRES.getUsername(), IntegrationTest.POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .target(target)
        .load();
  }

  private static Connection connect(String jdbcUrl) throws SQLException {
    return DriverManager.getConnection(
        jdbcUrl, IntegrationTest.POSTGRES.getUsername(), IntegrationTest.POSTGRES.getPassword());
  }
}
