package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
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
 * A Manager's send-back racing a Manager's approval of the same sent Client Invoice
 * (send-a-client-invoice-back spec, "Testing decisions" 2): both take the invoice's row lock, so
 * exactly one wins and the other finds the new status and gets 409. Runs outside the shared test
 * transaction, like {@link ClientInvoiceLineEditRaceTest}, because a race needs real committing
 * transactions; whatever it commits is deleted afterwards.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClientInvoiceSendBackRaceTest extends IntegrationTest {

  private static final int ROUNDS = 8;

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
  void sendBackVersusApproveLeavesOneConsistentFinalState() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();

    for (int round = 0; round < ROUNDS; round++) {
      UUID clientId = createClient(managerToken, "SendBack Race " + UUID.randomUUID());
      clientIds.add(clientId);
      UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
      contractIds.add(contractId);
      UUID carrierId = carrierFor(managerToken, contractId);
      UUID planId = createPostpaidPlan(managerToken, carrierId, "Plan " + UUID.randomUUID(), "18.00");
      planIds.add(planId);
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
          .andExpect(status().isCreated());
      MvcResult sent =
          mockMvc
              .perform(
                  post("/api/contracts/" + contractId + "/client-invoice/send")
                      .header("Authorization", "Bearer " + agentToken))
              .andExpect(status().isOk())
              .andReturn();
      UUID invoiceId =
          UUID.fromString(objectMapper.readTree(sent.getResponse().getContentAsString()).get("id").asText());

      CountDownLatch go = new CountDownLatch(1);
      ExecutorService pool = Executors.newFixedThreadPool(2);
      Callable<Integer> sendBack =
          () -> {
            go.await();
            return mockMvc
                .perform(
                    post("/api/client-invoices/" + invoiceId + "/send-back")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Not right"))))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      Callable<Integer> approve =
          () -> {
            go.await();
            return mockMvc
                .perform(
                    post("/api/client-invoices/" + invoiceId + "/approve")
                        .header("Authorization", "Bearer " + managerToken))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      Future<Integer> sendBackStatus = pool.submit(sendBack);
      Future<Integer> approveStatus = pool.submit(approve);
      go.countDown();
      int sendBackResult = sendBackStatus.get();
      int approveResult = approveStatus.get();
      pool.shutdown();

      assertThat(List.of(sendBackResult, approveResult)).as("exactly one winner").containsExactlyInAnyOrder(200, 409);
      Map<String, Object> row =
          jdbcTemplate.queryForMap(
              "SELECT status, sent_back_at, approved_at, sent_at FROM client_invoices WHERE id = ?", invoiceId);
      if (approveResult == 200) {
        assertThat(row.get("status")).isEqualTo("APPROVED");
        assertThat(row.get("sent_back_at")).isNull();
        assertThat(row.get("approved_at")).isNotNull();
      } else {
        assertThat(row.get("status")).isEqualTo("DRAFT");
        assertThat(row.get("sent_back_at")).isNotNull();
        assertThat(row.get("approved_at")).isNull();
      }
      List<Map<String, Object>> stored =
          jdbcTemplate.queryForList(
              "SELECT amount, computed_amount FROM client_invoice_lines WHERE client_invoice_id = ?", invoiceId);
      assertThat(stored).hasSize(1);
      assertThat((BigDecimal) stored.get(0).get("amount")).isEqualByComparingTo("18.00");
      mockMvc
          .perform(get("/api/client-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
          .andExpect(status().isOk());
    }
  }
}
