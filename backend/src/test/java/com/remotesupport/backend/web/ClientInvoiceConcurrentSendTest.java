package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two sends of one draft Client Invoice racing (serve-client-invoices-from-stored-lines ticket):
 * the row lock in {@link ClientInvoiceService#send} makes the loser wait and then find the invoice
 * {@code SENT}. Runs outside the shared test transaction, like {@link AgentCreationAtomicityTest},
 * because a race needs two real committing transactions; whatever it commits is deleted afterwards.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClientInvoiceConcurrentSendTest extends IntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID clientId;
  private UUID contractId;
  private UUID planId;

  @AfterEach
  void deleteWhatThisTestCommitted() {
    if (contractId != null) {
      jdbcTemplate.update(
          "DELETE FROM client_invoice_lines WHERE client_invoice_id IN (SELECT id FROM client_invoices WHERE contract_id = ?)",
          contractId);
      jdbcTemplate.update("DELETE FROM client_invoices WHERE contract_id = ?", contractId);
      jdbcTemplate.update("DELETE FROM sim_cards WHERE contract_id = ?", contractId);
      jdbcTemplate.update("DELETE FROM contracts WHERE id = ?", contractId);
    }
    if (planId != null) {
      jdbcTemplate.update("DELETE FROM postpaid_plans WHERE id = ?", planId);
    }
    if (clientId != null) {
      jdbcTemplate.update("DELETE FROM clients WHERE id = ?", clientId);
    }
  }

  @Test
  void concurrentSendsStoreLinesOnceAndOneGets409() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    clientId = createClient(managerToken, "Concurrent Send " + UUID.randomUUID());
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID carrierId = carrierFor(managerToken, contractId);
    planId = createPostpaidPlan(managerToken, carrierId, "Plan " + UUID.randomUUID(), "18.00");
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/sim-cards")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"number":"+1-555-0399","carrierId":"%s","flavor":"POSTPAID","postpaidPlanId":"%s"}
                    """
                        .formatted(carrierId, planId)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());

    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<Future<Integer>> sends = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      Callable<Integer> send =
          () -> {
            go.await();
            MvcResult result =
                mockMvc
                    .perform(
                        post("/api/contracts/" + contractId + "/client-invoice/send")
                            .header("Authorization", "Bearer " + agentToken))
                    .andReturn();
            return result.getResponse().getStatus();
          };
      sends.add(pool.submit(send));
    }
    go.countDown();
    List<Integer> statuses = new ArrayList<>();
    for (Future<Integer> send : sends) {
      statuses.add(send.get());
    }
    pool.shutdown();

    assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM client_invoices WHERE contract_id = ?", String.class, contractId))
        .isEqualTo("SENT");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM client_invoice_lines l JOIN client_invoices ci ON ci.id = l.client_invoice_id"
                    + " WHERE ci.contract_id = ?",
                Long.class,
                contractId))
        .isEqualTo(1L);
  }
}
