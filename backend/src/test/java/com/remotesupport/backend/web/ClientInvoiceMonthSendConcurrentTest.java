package com.remotesupport.backend.web;

import static com.remotesupport.backend.support.ClientInvoiceMonthTestSupport.currentMonth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.support.CarrierInvoiceTestSupport;
import com.remotesupport.backend.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The month send under concurrency (agent-sends-the-month spec, Testing decisions 2): two month
 * sends of one Agent race, and a month send races a file remove on one of its invoices. Runs
 * outside the shared test transaction, like {@link ClientInvoiceConcurrentSendTest}, because a race
 * needs real committing transactions; whatever it commits is deleted afterwards. The seeded Agent
 * holds no Contract in the test database, so each case gives him its own.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClientInvoiceMonthSendConcurrentTest extends IntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  private final List<UUID> clientIds = new ArrayList<>();
  private final List<UUID> contractIds = new ArrayList<>();

  @AfterEach
  void deleteWhatThisTestCommitted() {
    for (UUID contractId : contractIds) {
      List<UUID> planIds =
          jdbcTemplate.queryForList(
              "SELECT DISTINCT postpaid_plan_id FROM sim_cards WHERE contract_id = ? AND postpaid_plan_id IS NOT NULL",
              UUID.class,
              contractId);
      jdbcTemplate.update(
          "DELETE FROM carrier_invoice_files WHERE client_invoice_id IN (SELECT id FROM client_invoices WHERE contract_id = ?)",
          contractId);
      jdbcTemplate.update(
          "DELETE FROM client_invoice_lines WHERE client_invoice_id IN (SELECT id FROM client_invoices WHERE contract_id = ?)",
          contractId);
      jdbcTemplate.update("DELETE FROM client_invoices WHERE contract_id = ?", contractId);
      jdbcTemplate.update("DELETE FROM sim_cards WHERE contract_id = ?", contractId);
      jdbcTemplate.update("DELETE FROM contracts WHERE id = ?", contractId);
      for (UUID planId : planIds) {
        jdbcTemplate.update("DELETE FROM postpaid_plans WHERE id = ?", planId);
      }
    }
    for (UUID clientId : clientIds) {
      jdbcTemplate.update("DELETE FROM clients WHERE id = ?", clientId);
    }
  }

  /** A Contract of the seeded Agent with one Postpaid SIM, its draft opened and its file attached. */
  private UUID readyContract(String managerToken, String agentToken) throws Exception {
    UUID clientId = createClient(managerToken, "Month Race " + UUID.randomUUID());
    clientIds.add(clientId);
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    contractIds.add(contractId);
    addUniquePostpaidSim(managerToken, contractId);
    CarrierInvoiceTestSupport.attachCarrierInvoicesForEveryPostpaidLineOfCurrentMonth(
        mockMvc, objectMapper, contractId, agentToken);
    return contractId;
  }

  private UUID invoiceIdOf(UUID contractId) {
    return jdbcTemplate.queryForObject("SELECT id FROM client_invoices WHERE contract_id = ?", UUID.class, contractId);
  }

  private long count(String sql, Object... args) {
    return jdbcTemplate.queryForObject(sql, Long.class, args);
  }

  private List<MockHttpServletResponse> race(List<Callable<MockHttpServletResponse>> calls) throws Exception {
    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(calls.size());
    List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
    for (Callable<MockHttpServletResponse> call : calls) {
      futures.add(
          pool.submit(
              () -> {
                go.await();
                return call.call();
              }));
    }
    go.countDown();
    List<MockHttpServletResponse> responses = new ArrayList<>();
    for (Future<MockHttpServletResponse> future : futures) {
      responses.add(future.get());
    }
    pool.shutdown();
    return responses;
  }

  private Callable<MockHttpServletResponse> monthSend(String agentToken) {
    return () ->
        mockMvc
            .perform(
                post("/api/me/client-invoice-months/" + currentMonth() + "/send")
                    .header("Authorization", "Bearer " + agentToken))
            .andReturn()
            .getResponse();
  }

  @Test
  void twoMonthSendsOfOneAgentGiveOne200AndOneMonthAlreadySentAndEachLineOnce() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID first = readyContract(managerToken, agentToken);
    UUID second = readyContract(managerToken, agentToken);

    List<MockHttpServletResponse> responses = race(List.of(monthSend(agentToken), monthSend(agentToken)));

    assertThat(responses.stream().map(MockHttpServletResponse::getStatus)).containsExactlyInAnyOrder(200, 409);
    MockHttpServletResponse refused = responses.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
    assertThat(objectMapper.readTree(refused.getContentAsString()).get("code").asText())
        .isEqualTo("MONTH_ALREADY_SENT");
    for (UUID contractId : List.of(first, second)) {
      assertThat(count("SELECT COUNT(*) FROM client_invoices WHERE contract_id = ? AND status = 'SENT'", contractId))
          .isEqualTo(1L);
      assertThat(
              count(
                  "SELECT COUNT(*) FROM client_invoice_lines l JOIN client_invoices ci ON ci.id = l.client_invoice_id"
                      + " WHERE ci.contract_id = ?",
                  contractId))
          .as("one line row per line")
          .isEqualTo(1L);
    }
    assertThat(
            count(
                "SELECT COUNT(DISTINCT first_sent_at) FROM client_invoices WHERE contract_id IN (?, ?)", first, second))
        .as("one instant for the whole month")
        .isEqualTo(1L);
  }

  @Test
  void aMonthSendRacingAFileRemoveEitherSendsEveryInvoiceWithItsFileOrSendsNone() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    int rounds = 5;
    for (int round = 0; round < rounds; round++) {
      UUID raced = readyContract(managerToken, agentToken);
      UUID other = readyContract(managerToken, agentToken);
      UUID racedInvoice = invoiceIdOf(raced);
      JsonNode sim =
          objectMapper
              .readTree(
                  mockMvc
                      .perform(get("/api/client-invoices/" + racedInvoice).header("Authorization", "Bearer " + agentToken))
                      .andReturn()
                      .getResponse()
                      .getContentAsString())
              .get("basePostpaidSims")
              .get(0);
      String fileId = sim.get("carrierInvoiceFileId").asText();
      Callable<MockHttpServletResponse> remove =
          () ->
              mockMvc
                  .perform(
                      delete("/api/client-invoices/" + racedInvoice + "/files/" + fileId)
                          .header("Authorization", "Bearer " + agentToken))
                  .andReturn()
                  .getResponse();

      List<MockHttpServletResponse> responses = race(List.of(monthSend(agentToken), remove));

      MockHttpServletResponse send = responses.get(0);
      MockHttpServletResponse removal = responses.get(1);
      long sent = count("SELECT COUNT(*) FROM client_invoices WHERE contract_id IN (?, ?) AND status = 'SENT'", raced, other);
      long filesOfRaced = count("SELECT COUNT(*) FROM carrier_invoice_files WHERE client_invoice_id = ?", racedInvoice);
      if (send.getStatus() == 200) {
        assertThat(removal.getStatus()).as("the remove lost the race to the send").isEqualTo(409);
        assertThat(sent).isEqualTo(2L);
        assertThat(filesOfRaced).as("a sent invoice keeps its file").isEqualTo(1L);
      } else {
        assertThat(send.getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(send.getContentAsString()).get("code").asText())
            .isEqualTo("CARRIER_INVOICES_MISSING");
        assertThat(removal.getStatus()).as("the remove won the race").isEqualTo(204);
        assertThat(sent).as("no invoice of the month is sent").isZero();
        assertThat(filesOfRaced).isZero();
      }
      cleanUpRound(raced, other);
    }
  }

  /** Clears the round's rows so the next round's month holds only its own two Contracts. */
  private void cleanUpRound(UUID... contracts) {
    deleteWhatThisTestCommitted();
    contractIds.removeAll(List.of(contracts));
    clientIds.clear();
  }
}
