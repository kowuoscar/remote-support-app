package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.support.IntegrationTest;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * An edit of a draft Client Invoice's line racing the invoice's send
 * (agent-edits-a-client-invoice-line ticket; spec "Testing decisions" 4): both take the invoice's
 * row lock, so whichever wins, the sent lines are exactly what the send stored: the edit either
 * landed before the send (200, and the send stored the edited amount) or found the invoice sent
 * (409, and the line is as computed). Runs outside the shared test transaction, like {@link
 * ClientInvoiceConcurrentSendTest}, because a race needs real committing transactions; whatever it
 * commits is deleted afterwards.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClientInvoiceLineEditRaceTest extends IntegrationTest {

  private static final int ROUNDS = 5;

  @Autowired private JdbcTemplate jdbcTemplate;

  private final List<UUID> clientIds = new ArrayList<>();
  private final List<UUID> contractIds = new ArrayList<>();
  private final List<UUID> planIds = new ArrayList<>();

  @AfterEach
  void deleteWhatThisTestCommitted() {
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

  @Test
  void editVersusClientInvoiceSendLeavesSentLinesAsStored() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();

    for (int round = 0; round < ROUNDS; round++) {
      UUID clientId = createClient(managerToken, "Edit Race " + UUID.randomUUID());
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
                          {"number":"+1-555-07%02d","carrierId":"%s","flavor":"POSTPAID","postpaidPlanId":"%s"}
                          """
                              .formatted(round, carrierId, planId)))
              .andExpect(status().isCreated())
              .andReturn();
      UUID simId = UUID.fromString(objectMapper.readTree(sim.getResponse().getContentAsString()).get("id").asText());
      mockMvc
          .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
          .andExpect(status().isOk());

      CountDownLatch go = new CountDownLatch(1);
      ExecutorService pool = Executors.newFixedThreadPool(2);
      Callable<Integer> edit =
          () -> {
            go.await();
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
          };
      Callable<Integer> send =
          () -> {
            go.await();
            return mockMvc
                .perform(
                    post("/api/contracts/" + contractId + "/client-invoice/send")
                        .header("Authorization", "Bearer " + agentToken))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      Future<Integer> editStatus = pool.submit(edit);
      Future<Integer> sendStatus = pool.submit(send);
      go.countDown();
      int editResult = editStatus.get();
      int sendResult = sendStatus.get();
      pool.shutdown();

      assertThat(sendResult).isEqualTo(200);
      assertThat(editResult).isIn(200, 409);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT status FROM client_invoices WHERE contract_id = ?", String.class, contractId))
          .isEqualTo("SENT");
      List<Map<String, Object>> stored =
          jdbcTemplate.queryForList(
              "SELECT l.amount, l.computed_amount FROM client_invoice_lines l JOIN client_invoices ci"
                  + " ON ci.id = l.client_invoice_id WHERE ci.contract_id = ?",
              contractId);
      assertThat(stored).hasSize(1);
      assertThat(((BigDecimal) stored.get(0).get("computed_amount"))).isEqualByComparingTo("18.00");
      assertThat(((BigDecimal) stored.get(0).get("amount")))
          .as("the sent line is the edit's amount if the edit landed first, the computed amount if it got 409")
          .isEqualByComparingTo(editResult == 200 ? "31.40" : "18.00");
      JsonNode read =
          objectMapper.readTree(
              mockMvc
                  .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
                  .andReturn()
                  .getResponse()
                  .getContentAsString());
      assertThat(read.get("totalAmount").decimalValue()).isEqualByComparingTo(editResult == 200 ? "31.40" : "18.00");
    }
  }
}
