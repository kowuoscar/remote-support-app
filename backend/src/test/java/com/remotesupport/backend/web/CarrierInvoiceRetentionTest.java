package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.support.CarrierInvoiceTestSupport;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.ObjectStoreTestSupport;
import com.remotesupport.backend.web.CarrierInvoiceRetention.RetentionRun;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@link CarrierInvoiceRetention#runOnce()} on the real bean against the shared SeaweedFS: the
 * data is built through the API, {@code approvedAt} is backdated through the repository, and the
 * result is read back through the API and the bucket. The 500 cap is a unit case.
 */
class CarrierInvoiceRetentionTest extends IntegrationTest {

  private static final Duration THIRTY_DAYS = Duration.ofDays(30);

  @Autowired private CarrierInvoiceRetention retention;
  @Autowired private ClientInvoiceRepository clientInvoiceRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** A Contract with an open current-month draft carrying one file per Postpaid line (two lines). */
  private UUID draftWithFiles() throws Exception {
    String managerToken = managerToken();
    UUID contractId = createContract(managerToken, createClient(managerToken, "Retention Co " + UUID.randomUUID()), SEEDED_AGENT_ID);
    addUniquePostpaidSim(managerToken, contractId);
    addUniquePostpaidSim(managerToken, contractId);
    CarrierInvoiceTestSupport.attachCarrierInvoicesForEveryPostpaidLineOfCurrentMonth(
        mockMvc, objectMapper, contractId, agentToken());
    MvcResult result =
        mockMvc
            .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken()))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private void send(UUID invoiceId) throws Exception {
    mockMvc
        .perform(post("/api/client-invoices/" + invoiceId + "/send").header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isOk());
  }

  private UUID approvedInvoiceWithFiles() throws Exception {
    UUID invoiceId = draftWithFiles();
    send(invoiceId);
    mockMvc
        .perform(post("/api/client-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken()))
        .andExpect(status().isOk());
    return invoiceId;
  }

  private void backdateApproval(UUID invoiceId, Duration age) {
    ClientInvoice invoice = clientInvoiceRepository.findById(invoiceId).orElseThrow();
    invoice.setApprovedAt(Instant.now().minus(age));
    clientInvoiceRepository.saveAndFlush(invoice);
  }

  private JsonNode invoice(UUID invoiceId) throws Exception {
    return objectMapper.readTree(
        mockMvc
            .perform(get("/api/client-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private String prefix(UUID invoiceId) {
    return "carrier-invoices/" + SEEDED_TENANT_ID + "/" + invoiceId + "/";
  }

  private List<String> keysOf(UUID invoiceId) {
    return ObjectStoreTestSupport.keysUnder(prefix(invoiceId));
  }

  /** Moves the invoice's month and its files' upload time months back, as a rollover would. */
  private void ageUploadsAndMonth(UUID invoiceId) {
    ClientInvoice invoice = clientInvoiceRepository.findById(invoiceId).orElseThrow();
    invoice.setBillingMonth(invoice.getBillingMonth().minusMonths(4));
    clientInvoiceRepository.saveAndFlush(invoice);
    jdbcTemplate.update(
        "update carrier_invoice_files set uploaded_at = ? where client_invoice_id = ?",
        java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(120))),
        invoiceId);
  }

  private int downloadStatus(UUID invoiceId, JsonNode file) throws Exception {
    return mockMvc
        .perform(
            get("/api/client-invoices/" + invoiceId + "/files/" + file.get("id").asText())
                .header("Authorization", "Bearer " + managerToken()))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  @Test
  void aFileOnAnInvoiceApproved30DaysAndAMinuteAgoIsDeletedAndTheRowStays() throws Exception {
    UUID invoiceId = approvedInvoiceWithFiles();
    JsonNode before = invoice(invoiceId).get("files");
    assertThat(before).hasSize(2);
    assertThat(keysOf(invoiceId)).hasSize(2);
    backdateApproval(invoiceId, THIRTY_DAYS.plusMinutes(1));

    RetentionRun run = retention.runOnce();

    assertThat(run.deleted()).isGreaterThanOrEqualTo(2);
    assertThat(run.stoppedByStore()).isFalse();
    assertThat(keysOf(invoiceId)).isEmpty();
    JsonNode after = invoice(invoiceId).get("files");
    assertThat(after).hasSize(2);
    for (int i = 0; i < 2; i++) {
      assertThat(after.get(i).get("deletedAt").isNull()).isFalse();
      assertThat(after.get(i).get("filename")).isEqualTo(before.get(i).get("filename"));
      assertThat(after.get(i).get("contentType")).isEqualTo(before.get(i).get("contentType"));
      assertThat(after.get(i).get("sizeBytes")).isEqualTo(before.get(i).get("sizeBytes"));
      mockMvc
          .perform(
              get("/api/client-invoices/" + invoiceId + "/files/" + after.get(i).get("id").asText())
                  .header("Authorization", "Bearer " + managerToken()))
          .andExpect(status().isGone())
          .andExpect(jsonPath("$.code").value("CARRIER_INVOICE_FILE_DELETED"));
    }
  }

  @Test
  void aSecondRunChangesNothingAndReportsZero() throws Exception {
    UUID invoiceId = approvedInvoiceWithFiles();
    backdateApproval(invoiceId, THIRTY_DAYS.plusMinutes(1));
    retention.runOnce();
    JsonNode afterFirst = invoice(invoiceId).get("files");

    RetentionRun second = retention.runOnce();

    assertThat(second).isEqualTo(new RetentionRun(0, 0, false));
    assertThat(invoice(invoiceId).get("files")).isEqualTo(afterFirst);
  }

  @Test
  void aFileOnAnInvoiceApprovedOneMinuteShortOf30DaysIsKeptAndDownloads() throws Exception {
    UUID invoiceId = approvedInvoiceWithFiles();
    backdateApproval(invoiceId, THIRTY_DAYS.minusMinutes(1));

    retention.runOnce();

    assertThat(keysOf(invoiceId)).hasSize(2);
    JsonNode files = invoice(invoiceId).get("files");
    for (JsonNode file : files) {
      assertThat(file.get("deletedAt").isNull()).isTrue();
      assertThat(downloadStatus(invoiceId, file)).isEqualTo(200);
    }
  }

  @Test
  void filesOnDraftSentAndSentBackInvoicesAreNeverDeletedHoweverOldTheyAre() throws Exception {
    UUID draft = draftWithFiles();
    UUID sent = draftWithFiles();
    send(sent);
    UUID sentBack = draftWithFiles();
    send(sentBack);
    mockMvc
        .perform(
            post("/api/client-invoices/" + sentBack + "/send-back")
                .header("Authorization", "Bearer " + managerToken())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "Please check the lines"))))
        .andExpect(status().isOk());

    for (UUID id : List.of(draft, sent, sentBack)) {
      ageUploadsAndMonth(id);
      backdateApproval(id, Duration.ofDays(200));
    }

    retention.runOnce();

    for (UUID id : List.of(draft, sent, sentBack)) {
      assertThat(keysOf(id)).hasSize(2);
      for (JsonNode file : invoice(id).get("files")) {
        assertThat(file.get("deletedAt").isNull()).isTrue();
      }
    }
  }

  @Test
  void aFileWhoseObjectIsAlreadyMissingIsMarkedDeletedWithoutError() throws Exception {
    UUID invoiceId = approvedInvoiceWithFiles();
    backdateApproval(invoiceId, THIRTY_DAYS.plusDays(1));
    ObjectStoreTestSupport.delete(keysOf(invoiceId).get(0));

    RetentionRun run = retention.runOnce();

    assertThat(run.stoppedByStore()).isFalse();
    assertThat(keysOf(invoiceId)).isEmpty();
    for (JsonNode file : invoice(invoiceId).get("files")) {
      assertThat(file.get("deletedAt").isNull()).isFalse();
    }
    assertThat(Instant.parse(invoice(invoiceId).get("files").get(0).get("deletedAt").asText()))
        .isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
  }
}
