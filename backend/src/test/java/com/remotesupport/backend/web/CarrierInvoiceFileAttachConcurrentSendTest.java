package com.remotesupport.backend.web;

import static com.remotesupport.backend.support.ClientInvoiceMonthTestSupport.sendTheMonth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.support.CarrierInvoiceTestSupport;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.ObjectStoreTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * An attach whose upload is still transferring must not hold the Client Invoice's row lock: a send
 * of the same invoice is answered without waiting for it (ADR 0007, Backend 8). The upload is held
 * on a latch inside the storage's {@code put}; the send must complete while it is held. Runs
 * outside the shared test transaction, like {@link ClientInvoiceConcurrentSendTest}, because the
 * race needs real committing transactions; whatever it commits is deleted afterwards.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CarrierInvoiceFileAttachConcurrentSendTest extends IntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @SpyBean private CarrierInvoiceFileStorage storage;

  private UUID clientId;
  private UUID contractId;
  private UUID anchorClientId;
  private UUID anchorContractId;

  /**
   * Sends the seeded Agent's month first, through the month send, with a Contract that bills
   * nothing; the Contract the race then uses is a late one, sent on its own as the rule allows.
   */
  private void sendTheMonthBeforeTheRaceContractExists(String managerToken, String agentToken) throws Exception {
    anchorClientId = createClient(managerToken, "Month Anchor " + UUID.randomUUID());
    anchorContractId = createContract(managerToken, anchorClientId, SEEDED_AGENT_ID);
    sendTheMonth(mockMvc, agentToken);
  }

  @AfterEach
  void deleteWhatThisTestCommitted() {
    deleteContractAndClient(anchorContractId, anchorClientId);
    deleteContractAndClient(contractId, clientId);
  }

  private void deleteContractAndClient(UUID contractId, UUID clientId) {
    if (contractId != null) {
      jdbcTemplate.update(
          "DELETE FROM carrier_invoice_files WHERE client_invoice_id IN (SELECT id FROM client_invoices WHERE contract_id = ?)",
          contractId);
      jdbcTemplate.update(
          "DELETE FROM client_invoice_lines WHERE client_invoice_id IN (SELECT id FROM client_invoices WHERE contract_id = ?)",
          contractId);
      jdbcTemplate.update("DELETE FROM client_invoices WHERE contract_id = ?", contractId);
      jdbcTemplate.update("DELETE FROM sim_cards WHERE contract_id = ?", contractId);
      jdbcTemplate.update("DELETE FROM contracts WHERE id = ?", contractId);
    }
    if (clientId != null) {
      jdbcTemplate.update("DELETE FROM clients WHERE id = ?", clientId);
    }
  }

  @Test
  void aSendIsAnsweredWhileAnAttachUploadIsStillTransferring() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    sendTheMonthBeforeTheRaceContractExists(managerToken, agentToken);
    clientId = createClient(managerToken, "Attach Race " + UUID.randomUUID());
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID simId = addUniquePostpaidSim(managerToken, contractId);
    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());
    // The send gate needs the SIM covered; the slow attach below is then a second upload for it.
    CarrierInvoiceTestSupport.attachCarrierInvoicesForEveryPostpaidLineOfCurrentMonth(
        mockMvc, objectMapper, contractId, agentToken);
    UUID gatedInvoiceId =
        jdbcTemplate.queryForObject("SELECT id FROM client_invoices WHERE contract_id = ?", UUID.class, contractId);
    List<String> keysBefore =
        ObjectStoreTestSupport.keysUnder("carrier-invoices/" + SEEDED_TENANT_ID + "/" + gatedInvoiceId + "/");

    CountDownLatch uploading = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              uploading.countDown();
              assertThat(release.await(60, TimeUnit.SECONDS)).isTrue();
              return invocation.callRealMethod();
            })
        .when(storage)
        .put(anyString(), any(MultipartFile.class));

    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<Integer> attach =
          pool.submit(
              () ->
                  mockMvc
                      .perform(
                          multipart("/api/contracts/" + contractId + "/client-invoice/files")
                              .file(new MockMultipartFile("file", "slow.pdf", "application/pdf", "slow".getBytes()))
                              .param("simCardId", simId.toString())
                              .header("Authorization", "Bearer " + agentToken))
                      .andReturn()
                      .getResponse()
                      .getStatus());
      assertThat(uploading.await(30, TimeUnit.SECONDS)).as("the attach reached the store").isTrue();

      // The upload is held: the send must be answered anyway, so the row lock is not held across it.
      Future<Integer> send =
          Executors.newSingleThreadExecutor()
              .submit(
                  () ->
                      mockMvc
                          .perform(
                              post("/api/contracts/" + contractId + "/client-invoice/send")
                                  .header("Authorization", "Bearer " + agentToken))
                          .andReturn()
                          .getResponse()
                          .getStatus());
      assertThat(send.get(20, TimeUnit.SECONDS)).isEqualTo(200);
      assertThat(release.getCount()).as("the upload was still held when the send returned").isEqualTo(1);

      release.countDown();
      // The attach then finds the invoice sent: refused, and its object removed again.
      assertThat(attach.get(30, TimeUnit.SECONDS)).isEqualTo(409);
      UUID invoiceId =
          jdbcTemplate.queryForObject("SELECT id FROM client_invoices WHERE contract_id = ?", UUID.class, contractId);
      assertThat(ObjectStoreTestSupport.keysUnder("carrier-invoices/" + SEEDED_TENANT_ID + "/" + invoiceId + "/"))
          .as("the refused attach's object is gone; only the covering file remains")
          .isEqualTo(keysBefore);
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  void twoSimultaneousAttachesForOneSimLeaveOneRowOneObjectAnd409ForTheOther() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    clientId = createClient(managerToken, "Attach Same Sim Race " + UUID.randomUUID());
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID simId = addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId =
        UUID.fromString(
            objectMapper
                .readTree(
                    mockMvc
                        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText());

    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Integer>> attaches = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        String name = "race-" + i + ".pdf";
        attaches.add(
            pool.submit(
                () -> {
                  assertThat(go.await(30, TimeUnit.SECONDS)).isTrue();
                  return mockMvc
                      .perform(
                          multipart("/api/contracts/" + contractId + "/client-invoice/files")
                              .file(new MockMultipartFile("file", name, "application/pdf", name.getBytes()))
                              .param("simCardId", simId.toString())
                              .header("Authorization", "Bearer " + agentToken))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      go.countDown();
      List<Integer> statuses = List.of(attaches.get(0).get(60, TimeUnit.SECONDS), attaches.get(1).get(60, TimeUnit.SECONDS));

      assertThat(statuses).containsExactlyInAnyOrder(201, 409);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT count(*) FROM carrier_invoice_files WHERE client_invoice_id = ?", Integer.class, invoiceId))
          .isEqualTo(1);
      assertThat(ObjectStoreTestSupport.keysUnder("carrier-invoices/" + SEEDED_TENANT_ID + "/" + invoiceId + "/")).hasSize(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void anAttachRacingASendOnTheLastMissingFileNeverLeavesASentInvoiceWithoutIt() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    sendTheMonthBeforeTheRaceContractExists(managerToken, agentToken);
    clientId = createClient(managerToken, "Attach Versus Send " + UUID.randomUUID());
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID simId = addUniquePostpaidSim(managerToken, contractId);
    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());

    CountDownLatch go = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<MvcResult> attach =
          pool.submit(
              () -> {
                assertThat(go.await(30, TimeUnit.SECONDS)).isTrue();
                return mockMvc
                    .perform(
                        multipart("/api/contracts/" + contractId + "/client-invoice/files")
                            .file(new MockMultipartFile("file", "last.pdf", "application/pdf", "last".getBytes()))
                            .param("simCardId", simId.toString())
                            .header("Authorization", "Bearer " + agentToken))
                    .andReturn();
              });
      Future<MvcResult> send =
          pool.submit(
              () -> {
                assertThat(go.await(30, TimeUnit.SECONDS)).isTrue();
                return mockMvc
                    .perform(
                        post("/api/contracts/" + contractId + "/client-invoice/send")
                            .header("Authorization", "Bearer " + agentToken))
                    .andReturn();
              });
      go.countDown();
      MvcResult attached = attach.get(60, TimeUnit.SECONDS);
      MvcResult sent = send.get(60, TimeUnit.SECONDS);

      int sendStatus = sent.getResponse().getStatus();
      assertThat(sendStatus).isIn(200, 409);
      if (sendStatus == 409) {
        assertThat(objectMapper.readTree(sent.getResponse().getContentAsString()).get("code").asText())
            .isEqualTo("CARRIER_INVOICES_MISSING");
        assertThat(attached.getResponse().getStatus()).isEqualTo(201);
      } else {
        // The attach took the lock first, so the file was there when the send looked.
        assertThat(attached.getResponse().getStatus()).isEqualTo(201);
      }
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT count(*) FROM carrier_invoice_files f JOIN client_invoices i ON i.id = f.client_invoice_id"
                      + " WHERE i.contract_id = ? AND f.sim_card_id = ?",
                  Integer.class,
                  contractId,
                  simId))
          .isEqualTo(1);
      String status =
          jdbcTemplate.queryForObject("SELECT status FROM client_invoices WHERE contract_id = ?", String.class, contractId);
      assertThat(status).isEqualTo(sendStatus == 200 ? "SENT" : "DRAFT");
    } finally {
      pool.shutdownNow();
    }
  }
}
