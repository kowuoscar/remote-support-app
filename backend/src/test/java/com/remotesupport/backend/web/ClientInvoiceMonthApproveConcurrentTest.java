package com.remotesupport.backend.web;

import static com.remotesupport.backend.support.ClientInvoiceMonthTestSupport.approveTheMonth;
import static com.remotesupport.backend.support.ClientInvoiceMonthTestSupport.currentMonth;
import static com.remotesupport.backend.support.ClientInvoiceMonthTestSupport.sendTheMonth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
 * The month approval under concurrency (manager-reviews-by-agent spec, Testing decisions 2): two
 * month approvals of one Agent's month race, and a month approval races a send-back of one of its
 * invoices. Runs outside the shared test transaction, like {@link
 * ClientInvoiceMonthSendConcurrentTest}, because a race needs real committing transactions;
 * whatever it commits is deleted afterwards. The seeded Agent holds no Contract in the test
 * database, so each case gives him its own, with no Postpaid SIM so the send needs no file.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ClientInvoiceMonthApproveConcurrentTest extends IntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  private final List<UUID> clientIds = new ArrayList<>();
  private final List<UUID> contractIds = new ArrayList<>();

  @AfterEach
  void deleteWhatThisTestCommitted() {
    for (UUID contractId : contractIds) {
      jdbcTemplate.update(
          "DELETE FROM client_invoice_lines WHERE client_invoice_id IN (SELECT id FROM client_invoices WHERE contract_id = ?)",
          contractId);
      jdbcTemplate.update("DELETE FROM client_invoices WHERE contract_id = ?", contractId);
      jdbcTemplate.update("DELETE FROM contracts WHERE id = ?", contractId);
    }
    for (UUID clientId : clientIds) {
      jdbcTemplate.update("DELETE FROM clients WHERE id = ?", clientId);
    }
    contractIds.clear();
    clientIds.clear();
  }

  /** The seeded Agent's sent month of {@code contracts} Contracts: the ids of the sent invoices. */
  private List<UUID> sentMonth(String managerToken, String agentToken, int contracts) throws Exception {
    for (int i = 0; i < contracts; i++) {
      UUID clientId = createClient(managerToken, "Approve Race " + UUID.randomUUID());
      clientIds.add(clientId);
      contractIds.add(createContract(managerToken, clientId, SEEDED_AGENT_ID));
    }
    sendTheMonth(mockMvc, agentToken);
    return jdbcTemplate.queryForList(
        "SELECT ci.id FROM client_invoices ci JOIN contracts c ON c.id = ci.contract_id"
            + " WHERE c.agent_id = ? ORDER BY ci.id",
        UUID.class,
        SEEDED_AGENT_ID);
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

  private static String body(List<UUID> ids) {
    return "{\"invoiceIds\":[" + String.join(",", ids.stream().map(id -> "\"" + id + "\"").toList()) + "]}";
  }

  private Callable<MockHttpServletResponse> monthApproval(String managerToken, List<UUID> ids) {
    return () ->
        approveTheMonth(mockMvc, managerToken, SEEDED_AGENT_ID, currentMonth(), body(ids))
            .andReturn()
            .getResponse();
  }

  @Test
  void twoMonthApprovalsOfOneAgentGiveOne200AndOneNothingToApproveAndEachInvoiceApprovedOnce() throws Exception {
    String managerToken = managerToken();
    List<UUID> ids = sentMonth(managerToken, agentToken(), 3);

    List<MockHttpServletResponse> responses =
        race(List.of(monthApproval(managerToken, ids), monthApproval(managerToken, ids)));

    assertThat(responses.stream().map(MockHttpServletResponse::getStatus)).containsExactlyInAnyOrder(200, 409);
    MockHttpServletResponse refused = responses.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
    assertThat(objectMapper.readTree(refused.getContentAsString()).get("code").asText())
        .isEqualTo("NOTHING_TO_APPROVE");
    String inIds = "id = ANY (?::uuid[])";
    assertThat(count("SELECT COUNT(*) FROM client_invoices WHERE " + inIds + " AND status = 'APPROVED'", uuidArray(ids)))
        .isEqualTo(3L);
    assertThat(count("SELECT COUNT(DISTINCT approved_at) FROM client_invoices WHERE " + inIds, uuidArray(ids)))
        .as("one instant for the whole month")
        .isEqualTo(1L);
  }

  @Test
  void aMonthApprovalRacingASendBackEitherApprovesEveryInvoiceOrNone() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    for (int round = 0; round < 5; round++) {
      List<UUID> ids = sentMonth(managerToken, agentToken, 3);
      UUID raced = ids.get(1);
      Callable<MockHttpServletResponse> sendBack =
          () ->
              mockMvc
                  .perform(
                      post("/api/client-invoices/" + raced + "/send-back")
                          .header("Authorization", "Bearer " + managerToken)
                          .contentType(APPLICATION_JSON)
                          .content("{\"reason\":\"Please check the second line\"}"))
                  .andReturn()
                  .getResponse();

      List<MockHttpServletResponse> responses = race(List.of(monthApproval(managerToken, ids), sendBack));

      MockHttpServletResponse approval = responses.get(0);
      MockHttpServletResponse sentBack = responses.get(1);
      long approved =
          count(
              "SELECT COUNT(*) FROM client_invoices WHERE id = ANY (?::uuid[]) AND status = 'APPROVED'",
              uuidArray(ids));
      if (approval.getStatus() == 200) {
        assertThat(sentBack.getStatus()).as("the send-back lost the race").isEqualTo(409);
        assertThat(approved).isEqualTo(3L);
      } else {
        assertThat(approval.getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(approval.getContentAsString()).get("code").asText())
            .isEqualTo("MONTH_CHANGED");
        assertThat(sentBack.getStatus()).as("the send-back won the race").isEqualTo(200);
        assertThat(approved).as("nothing of the month is approved").isZero();
        assertThat(count("SELECT COUNT(*) FROM client_invoices WHERE id = ? AND status = 'DRAFT'", raced))
            .isEqualTo(1L);
      }
      deleteWhatThisTestCommitted();
    }
  }

  private String uuidArray(List<UUID> ids) {
    return "{" + String.join(",", ids.stream().map(UUID::toString).toList()) + "}";
  }
}
