package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * What every unusable object store configuration must do, whatever is wrong with it (ADR 0007): the
 * backend starts and answers its health check, logs one ERROR at startup naming what is wrong and
 * never a credential, and attaching and downloading answer 503. Each concrete subclass boots its
 * own Spring context with one thing wrong and says what the ERROR must name.
 */
@ExtendWith(OutputCaptureExtension.class)
abstract class ObjectStoreUnusableStartupCases extends IntegrationTest {

  static final String WRONG_ACCESS_KEY = "wrong-access-key-123";
  static final String WRONG_SECRET_KEY = "wrong-secret-key-456";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectStoreAvailability availability;

  /** Text the one startup ERROR must contain (the endpoint and bucket, or the bad setting's name). */
  abstract String[] startupErrorMustName();

  @Test
  void theBackendStartsAndTheHealthCheckAnswers() throws Exception {
    mockMvc.perform(get("/api/health")).andExpect(status().isOk());
  }

  @Test
  void startupLogsExactlyOneErrorNamingWhatIsWrongAndNoCredential(CapturedOutput output) {
    var startupErrors =
        output
            .getAll()
            .lines()
            .filter(line -> line.contains(" ERROR ") && line.contains("Object store") && !line.contains("failed for key"))
            .toList();

    assertThat(startupErrors).hasSize(1);
    for (String name : startupErrorMustName()) {
      assertThat(startupErrors.get(0)).contains(name);
    }
    assertThat(output.getAll()).doesNotContain(WRONG_ACCESS_KEY).doesNotContain(WRONG_SECRET_KEY);
  }

  @Test
  void fileStorageIsReportedUnavailable() {
    assertThat(availability.isAvailable()).isFalse();
  }

  @Test
  void attachAnswers503() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "No Store Co " + UUID.randomUUID());
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    mockMvc
        .perform(
            multipart("/api/contracts/" + contractId + "/client-invoice/files")
                .file(new MockMultipartFile("file", "a.pdf", "application/pdf", "abc".getBytes()))
                .header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void downloadAnswers503() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID clientId = createClient(managerToken, "No Store Download Co " + UUID.randomUUID());
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    MvcResult invoice =
        mockMvc
            .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    UUID invoiceId = UUID.fromString(objectMapper.readTree(invoice.getResponse().getContentAsString()).get("id").asText());
    UUID fileId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO carrier_invoice_files (id, tenant_id, client_invoice_id, filename, content_type, size_bytes, storage_path)"
            + " VALUES (?, ?, ?, 'a.pdf', 'application/pdf', 3, ?)",
        fileId,
        SEEDED_TENANT_ID,
        invoiceId,
        "carrier-invoices/" + SEEDED_TENANT_ID + "/" + invoiceId + "/" + fileId);

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice/files/" + fileId).header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isServiceUnavailable());
  }
}
