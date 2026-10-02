package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Client Invoice line edit racing the Agent Invoice's send, and its approve
 * (local-support-fees-follow-billed-client-invoice-lines ticket; spec "Testing decisions" 4). The
 * edit holds the Client Invoice's lock, then the Agent Invoice's; the Agent Invoice's send and
 * approve re-read it under that lock. So the final Local Support Fees equal the rule's value for
 * the final states: against a send the edit is counted exactly once, against an approve exactly
 * when its follow audit line was written, and never lost to a stale write. Runs outside the shared
 * test transaction, like {@link ClientInvoiceLineEditRaceTest}; whatever it commits is deleted
 * afterwards.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LocalSupportFeesRaceTest extends IntegrationTest {

  private static final int ROUNDS = 5;

  @Autowired private JdbcTemplate jdbcTemplate;

  private final List<UUID> clientIds = new ArrayList<>();
  private final List<UUID> contractIds = new ArrayList<>();
  private final List<UUID> planIds = new ArrayList<>();

  /** The 18.00 Postpaid SIM of the round's Contract. */
  private UUID simId;

  @AfterEach
  void deleteWhatThisTestCommitted() {
    deleteRound();
  }

  /** Every row committed so far, so each round starts with the Agent owning no other Contract. */
  private void deleteRound() {
    deleteAgentInvoices();
    for (UUID contractId : contractIds) {
      jdbcTemplate.update(
          "DELETE FROM client_invoice_lines WHERE client_invoice_id IN (SELECT id FROM client_invoices WHERE contract_id = ?)",
          contractId);
      jdbcTemplate.update("DELETE FROM client_invoices WHERE contract_id = ?", contractId);
      jdbcTemplate.update("DELETE FROM sim_cards WHERE contract_id = ?", contractId);
      jdbcTemplate.update("DELETE FROM contracts WHERE id = ?", contractId);
    }
    for (UUID planId : planIds) {
      jdbcTemplate.update("DELETE FROM postpaid_plans WHERE id = ?", planId);
    }
    for (UUID clientId : clientIds) {
      jdbcTemplate.update("DELETE FROM clients WHERE id = ?", clientId);
    }
  }

  private void deleteAgentInvoices() {
    jdbcTemplate.update("DELETE FROM agent_invoices WHERE agent_id = ?", SEEDED_AGENT_ID);
  }

  /** A fresh Contract with one 18.00 Postpaid SIM and its Client Invoice open (never sent). */
  private UUID newRound(String managerToken, String agentToken, int round) throws Exception {
    UUID clientId = createClient(managerToken, "LSF Race " + UUID.randomUUID());
    clientIds.add(clientId);
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    contractIds.add(contractId);
    UUID carrierId = carrierFor(managerToken, contractId);
    UUID planId = createPostpaidPlan(managerToken, carrierId, "Plan " + UUID.randomUUID(), "18.00");
    planIds.add(planId);
    MvcResult sim =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/sim-cards")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"number":"+1-555-08%02d","carrierId":"%s","flavor":"POSTPAID","postpaidPlanId":"%s"}
                        """
                            .formatted(round, carrierId, planId)))
            .andExpect(status().isCreated())
            .andReturn();
    simId = UUID.fromString(objectMapper.readTree(sim.getResponse().getContentAsString()).get("id").asText());
    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());
    return contractId;
  }

  private int edit(UUID contractId, String agentToken) throws Exception {
    return mockMvc
        .perform(
            put("/api/contracts/" + contractId + "/client-invoice/lines")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"kind":"POSTPAID_SIM","sourceId":"%s","amount":31.40}
                    """
                        .formatted(simId)))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private JsonNode agentInvoice(String agentToken) throws Exception {
    return objectMapper.readTree(
        mockMvc
            .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private int sendAgentInvoice(String agentToken) throws Exception {
    return mockMvc
        .perform(post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/send").header("Authorization", "Bearer " + agentToken))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  /** The statuses of two actions run at once, and how many follow audit lines they wrote. */
  private record Outcome(int first, int second, long followLines) {}

  private Outcome race(Callable<Integer> first, Callable<Integer> second) throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch go = new CountDownLatch(1);
      Future<Integer> a =
          pool.submit(
              () -> {
                go.await();
                return first.call();
              });
      Future<Integer> b =
          pool.submit(
              () -> {
                go.await();
                return second.call();
              });
      go.countDown();
      int firstStatus = a.get();
      int secondStatus = b.get();
      long follows =
          appender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .filter(m -> m.contains("action=AGENT_INVOICE_LOCAL_SUPPORT_FEES_FOLLOWED"))
              .count();
      return new Outcome(firstStatus, secondStatus, follows);
    } finally {
      pool.shutdown();
      auditLogger.detachAppender(appender);
    }
  }

  @Test
  void editVersusAgentInvoiceSendCountsEditOnce() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();

    for (int round = 0; round < ROUNDS; round++) {
      UUID contractId = newRound(managerToken, agentToken, round);
      agentInvoice(agentToken);

      Outcome outcome = race(() -> edit(contractId, agentToken), () -> sendAgentInvoice(agentToken));

      assertThat(outcome.first()).isEqualTo(200);
      assertThat(outcome.second()).isEqualTo(200);
      JsonNode invoice = agentInvoice(agentToken);
      assertThat(invoice.get("status").asText()).isEqualTo("SENT");
      assertThat(invoice.get("localSupportFees").decimalValue())
          .as("the edit counted exactly once, whichever won (follow lines: %d)", outcome.followLines())
          .isEqualByComparingTo("31.40");
      deleteRound();
    }
  }

  @Test
  void editVersusAgentInvoiceApproveCountsEditOnceOrNotAtAll() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();

    for (int round = 0; round < ROUNDS; round++) {
      UUID contractId = newRound(managerToken, agentToken, round);
      UUID agentInvoiceId = UUID.fromString(agentInvoice(agentToken).get("id").asText());
      assertThat(sendAgentInvoice(agentToken)).isEqualTo(200);

      Outcome outcome =
          race(
              () -> edit(contractId, agentToken),
              () ->
                  mockMvc
                      .perform(
                          post("/api/agent-invoices/" + agentInvoiceId + "/approve")
                              .header("Authorization", "Bearer " + managerToken))
                      .andReturn()
                      .getResponse()
                      .getStatus());

      assertThat(outcome.first()).isEqualTo(200);
      assertThat(outcome.second()).isEqualTo(200);
      JsonNode invoice = agentInvoice(agentToken);
      assertThat(invoice.get("status").asText()).isEqualTo("APPROVED");
      assertThat(outcome.followLines()).isIn(0L, 1L);
      assertThat(invoice.get("localSupportFees").decimalValue())
          .as("moved by the edit exactly when the edit was applied before the approval")
          .isEqualByComparingTo(outcome.followLines() == 1 ? "31.40" : "18.00");
      deleteRound();
    }
  }
}
