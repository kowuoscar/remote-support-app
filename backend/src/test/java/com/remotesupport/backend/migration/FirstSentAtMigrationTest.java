package com.remotesupport.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

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
 * The V63 migration that gives a Client Invoice its {@code first_sent_at} (agent-sends-the-month
 * spec, "Schema: one column"). It runs on a throwaway database in the shared Postgres container:
 * migrated to V62, loaded with a SENT, an APPROVED, a sent-back and a never-sent Client Invoice,
 * then migrated through V63.
 */
class FirstSentAtMigrationTest {

  private static final String T = "b0000000-0000-0000-0000-000000000001";
  private static final String CLIENT = "b0000000-0000-0000-0000-0000000000b1";
  private static final String AGENT = "b0000000-0000-0000-0000-0000000000d1";
  private static final String CONTRACT = "b0000000-0000-0000-0000-0000000000c1";
  private static final String SENT = "b0000000-0000-0000-0000-0000000000e1";
  private static final String APPROVED = "b0000000-0000-0000-0000-0000000000e2";
  private static final String SENT_BACK = "b0000000-0000-0000-0000-0000000000e3";
  private static final String NEVER_SENT = "b0000000-0000-0000-0000-0000000000e4";

  private String database;
  private String url;

  @BeforeEach
  void migrateAFreshDatabaseToV62AndLoadInvoices() throws SQLException {
    database = "v63_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection admin = connect(IntegrationTest.POSTGRES.getJdbcUrl());
        Statement statement = admin.createStatement()) {
      statement.execute("CREATE DATABASE " + database);
    }
    String base = IntegrationTest.POSTGRES.getJdbcUrl();
    url = base.substring(0, base.lastIndexOf('/') + 1) + database;
    flyway("62").migrate();
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
  void theColumnIsNullableAndEmptyBeforeTheMigration() throws SQLException {
    assertThat(
            rows(
                "SELECT count(*) FROM information_schema.columns"
                    + " WHERE table_name = 'client_invoices' AND column_name = 'first_sent_at'"))
        .containsExactly("0");
  }

  @Test
  void aSentAnApprovedAndASentBackRowTakeTheirFirstSentAtFromTheOldColumnsAndANeverSentRowStaysNull()
      throws SQLException {
    flyway("63").migrate();

    assertThat(
            rows(
                "SELECT id, to_char(first_sent_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS')"
                    + " FROM client_invoices ORDER BY id"))
        .containsExactly(
            SENT + "|2026-06-10T09:00:00", // sent_at
            APPROVED + "|2026-07-10T09:00:00", // sent_at, though approved_at is later
            SENT_BACK + "|2026-08-12T09:00:00", // sent_back_at: sent_at was cleared by the send-back
            NEVER_SENT + "|null");
  }

  @Test
  void aRowSentWithNeitherSentAtNorApprovedAtFallsBackToItsCreationTime() throws SQLException {
    execute(
        "INSERT INTO client_invoices (id, tenant_id, contract_id, billing_month, status, currency,"
            + " created_at) VALUES ('b0000000-0000-0000-0000-0000000000e5', '"
            + T
            + "', '"
            + CONTRACT
            + "', DATE '2026-02-01', 'SENT', 'USD', TIMESTAMPTZ '2026-02-03 09:00:00+00')");

    flyway("63").migrate();

    assertThat(
            rows(
                "SELECT to_char(first_sent_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS')"
                    + " FROM client_invoices WHERE id = 'b0000000-0000-0000-0000-0000000000e5'"))
        .containsExactly("2026-02-03T09:00:00");
  }

  @Test
  void everyOtherColumnOfTheInvoicesIsUntouched() throws SQLException {
    String invoices =
        "SELECT id, status, sent_at, approved_at, sent_back_at, lines_stored FROM client_invoices ORDER BY id";
    List<String> before = rows(invoices);

    flyway("63").migrate();

    assertThat(rows(invoices)).isEqualTo(before);
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
        "INSERT INTO client_invoices (id, tenant_id, contract_id, billing_month, status, currency,"
            + " created_at, sent_at, approved_at, sent_back_at) VALUES"
            + " ('"
            + SENT
            + "', '"
            + T
            + "', '"
            + CONTRACT
            + "', DATE '2026-06-01', 'SENT', 'USD', TIMESTAMPTZ '2026-06-01 08:00:00+00',"
            + " TIMESTAMPTZ '2026-06-10 09:00:00+00', NULL, NULL),"
            + " ('"
            + APPROVED
            + "', '"
            + T
            + "', '"
            + CONTRACT
            + "', DATE '2026-07-01', 'APPROVED', 'USD', TIMESTAMPTZ '2026-07-01 08:00:00+00',"
            + " TIMESTAMPTZ '2026-07-10 09:00:00+00', TIMESTAMPTZ '2026-07-15 09:00:00+00', NULL),"
            + " ('"
            + SENT_BACK
            + "', '"
            + T
            + "', '"
            + CONTRACT
            + "', DATE '2026-08-01', 'DRAFT', 'USD', TIMESTAMPTZ '2026-08-01 08:00:00+00',"
            + " NULL, NULL, TIMESTAMPTZ '2026-08-12 09:00:00+00'),"
            + " ('"
            + NEVER_SENT
            + "', '"
            + T
            + "', '"
            + CONTRACT
            + "', DATE '2026-09-01', 'DRAFT', 'USD', TIMESTAMPTZ '2026-09-01 08:00:00+00',"
            + " NULL, NULL, NULL)");
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
