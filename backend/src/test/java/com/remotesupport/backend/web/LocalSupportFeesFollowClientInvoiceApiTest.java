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
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceLine;
import com.remotesupport.backend.domain.ClientInvoiceLineKind;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Fee;
import com.remotesupport.backend.repository.ClientInvoiceLineRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The Agent's Local Support Fees follow what the Client Invoices bill until the Agent Invoice is
 * approved (edit-client-invoice-lines spec, "Local Support Fees: the new rule"; ADR 0004), each
 * case read through the Agent Invoice's own routes. Nothing can send a Client Invoice back yet, so
 * the draft-with-stored-lines shape is reached by a fixture, as {@link ClientInvoiceLineEditApiTest}
 * does.
 */
class LocalSupportFeesFollowClientInvoiceApiTest extends IntegrationTest {

  @Autowired private ClientInvoiceRepository clientInvoiceRepository;
  @Autowired private ClientInvoiceLineRepository clientInvoiceLineRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private FeeRepository feeRepository;

  private String managerToken;
  private String agentToken;

  /** One of the seeded Agent's Contracts and the Tester who can ask for work on it. */
  private record Cx(UUID clientId, UUID contractId, String testerToken) {}

  private Cx contract(String name) throws Exception {
    if (managerToken == null) {
      managerToken = managerToken();
      agentToken = agentToken();
    }
    UUID clientId = createClient(managerToken, name);
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String tester = createTesterAndLogin(managerToken, clientId, "tester-" + UUID.randomUUID() + "@example.com");
    return new Cx(clientId, contractId, tester);
  }

  private UUID addSim(Cx c, String number, String fee) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + c.contractId() + "/sim-cards")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(postpaidSimCardJson(managerToken, c.contractId(), number, fee)))
            .andExpect(status().isCreated())
            .andReturn();
    return idOf(result);
  }

  private UUID logFee(Cx c, String amount) throws Exception {
    MvcResult request =
        mockMvc
            .perform(
                post("/api/contracts/" + c.contractId() + "/requests")
                    .header("Authorization", "Bearer " + c.testerToken())
                    .contentType(APPLICATION_JSON)
                    .content("{\"type\":\"OTHER\",\"description\":\"Screen replacement\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    MvcResult fee =
        mockMvc
            .perform(
                post("/api/contracts/" + c.contractId() + "/fees")
                    .header("Authorization", "Bearer " + agentToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        "{\"requestId\":\"%s\",\"feeType\":\"OTHER\",\"amount\":%s}".formatted(idOf(request), amount)))
            .andExpect(status().isCreated())
            .andReturn();
    return idOf(fee);
  }

  private UUID idOf(MvcResult result) throws Exception {
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private JsonNode json(ResultActions actions) throws Exception {
    return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
  }

  private void openClientInvoice(Cx c) throws Exception {
    mockMvc
        .perform(get("/api/contracts/" + c.contractId() + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());
  }

  private UUID sendClientInvoice(Cx c) throws Exception {
    return UUID.fromString(
        json(
                mockMvc
                    .perform(
                        post("/api/contracts/" + c.contractId() + "/client-invoice/send")
                            .header("Authorization", "Bearer " + agentToken))
                    .andExpect(status().isOk()))
            .get("id")
            .asText());
  }

  private ResultActions edit(Cx c, String kind, UUID sourceId, String amount) throws Exception {
    return mockMvc.perform(
        put("/api/contracts/" + c.contractId() + "/client-invoice/lines")
            .header("Authorization", "Bearer " + agentToken)
            .contentType(APPLICATION_JSON)
            .content(
                "{\"kind\":\"%s\",\"sourceId\":%s,\"amount\":%s}"
                    .formatted(kind, sourceId == null ? "null" : "\"" + sourceId + "\"", amount)));
  }

  private void setBackToDraft(UUID invoiceId) {
    ClientInvoice invoice = clientInvoiceRepository.findById(invoiceId).orElseThrow();
    invoice.setStatus(ClientInvoiceStatus.DRAFT);
    invoice.setSentAt(null);
    clientInvoiceRepository.saveAndFlush(invoice);
  }

  private JsonNode agentInvoice() throws Exception {
    return json(
        mockMvc
            .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk()));
  }

  private UUID sendAgentInvoice() throws Exception {
    return UUID.fromString(
        json(
                mockMvc
                    .perform(
                        post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/send")
                            .header("Authorization", "Bearer " + agentToken))
                    .andExpect(status().isOk()))
            .get("id")
            .asText());
  }

  private void managerPost(UUID agentInvoiceId, String action, String body) throws Exception {
    mockMvc
        .perform(
            post("/api/agent-invoices/" + agentInvoiceId + "/" + action)
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  private void approveClientInvoice(UUID invoiceId) throws Exception {
    mockMvc
        .perform(post("/api/client-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());
  }

  private void assertFees(String expected) throws Exception {
    assertThat(agentInvoice().get("localSupportFees").decimalValue()).isEqualByComparingTo(expected);
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  /** The follow audit lines {@code action} writes. */
  private List<String> followAuditOf(ThrowingRunnable action) throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);
    try {
      action.run();
      return appender.list.stream()
          .map(ILoggingEvent::getFormattedMessage)
          .filter(m -> m.contains("action=AGENT_INVOICE_LOCAL_SUPPORT_FEES_FOLLOWED"))
          .toList();
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  // --- No edits: today's figure ---------------------------------------------------------------

  @Test
  void noEditsEqualsTodaysFigureForNoInvoiceDraftAndSent() throws Exception {
    Cx none = contract("LSF No Invoice");
    addSim(none, "+1-555-0500", "10.00");
    Cx draft = contract("LSF Draft");
    addSim(draft, "+1-555-0501", "20.00");
    logFee(draft, "5.00");
    openClientInvoice(draft);
    Cx sent = contract("LSF Sent");
    addSim(sent, "+1-555-0502", "30.00");
    logFee(sent, "2.00");
    openClientInvoice(sent);
    sendClientInvoice(sent);

    // 10 (no invoice) + 25 (never-sent draft) + 32 (sent) — the computation, as before.
    assertFees("67.00");
  }

  // --- Edits on a never-sent draft --------------------------------------------------------------

  @Test
  void editOnNeverSentDraftMovesDraftAgentInvoiceAndResetRestores() throws Exception {
    Cx c = contract("LSF Draft Edit");
    UUID sim = addSim(c, "+1-555-0510", "18.00");
    UUID fee = logFee(c, "5.00");
    openClientInvoice(c);
    assertFees("23.00");

    edit(c, "POSTPAID_SIM", sim, "20.00").andExpect(status().isOk());
    edit(c, "FEE", fee, "4.00").andExpect(status().isOk());
    assertFees("24.00");

    edit(c, "POSTPAID_SIM", sim, "18.00").andExpect(status().isOk());
    edit(c, "FEE", fee, "5.00").andExpect(status().isOk());
    assertFees("23.00");
  }

  @Test
  void agentInvoiceSentAfterAnEditFreezesIt() throws Exception {
    Cx c = contract("LSF Sent After Edit");
    UUID sim = addSim(c, "+1-555-0511", "18.00");
    edit(c, "POSTPAID_SIM", sim, "20.00").andExpect(status().isOk());

    sendAgentInvoice();

    assertFees("20.00");
    // The frozen figure is the snapshot: the Fleet changing afterwards does not touch it.
    addSim(c, "+1-555-0512", "7.00");
    assertFees("20.00");
  }

  // --- Edit after the Agent Invoice was sent ----------------------------------------------------

  @Test
  void editAfterAgentInvoiceSentMovesByExactDifferenceWithOneAuditLineAndLateFeeDoesNotSlipIn() throws Exception {
    Cx c = contract("LSF Sent Then Edit");
    UUID sim = addSim(c, "+1-555-0520", "18.00");
    openClientInvoice(c);
    UUID agentInvoiceId = sendAgentInvoice();
    assertFees("18.00");

    UUID lateFee = logFee(c, "5.00");
    assertFees("18.00");

    List<String> audit = followAuditOf(() -> edit(c, "POSTPAID_SIM", sim, "20.00").andExpect(status().isOk()));
    assertFees("20.00");
    assertThat(audit).hasSize(1);
    assertThat(audit.get(0))
        .contains("entityId=" + agentInvoiceId)
        .contains("clientInvoiceId=")
        .contains("oldAmount=18.00")
        .contains("newAmount=20.00")
        .contains("actorUserId=")
        .contains("tenantId=" + SEEDED_TENANT_ID);

    // The line of a Fee logged after the Agent Invoice's send: only the edit's difference moves it.
    edit(c, "FEE", lateFee, "7.00").andExpect(status().isOk());
    assertFees("22.00");
    edit(c, "FEE", lateFee, "5.00").andExpect(status().isOk());
    assertFees("20.00");
    assertThat(agentInvoice().get("status").asText()).isEqualTo("SENT");
  }

  @Test
  void editAfterApproveOrPaidMovesNothing() throws Exception {
    Cx c = contract("LSF Approved Then Edit");
    UUID sim = addSim(c, "+1-555-0530", "18.00");
    UUID agentInvoiceId = sendAgentInvoice();
    managerPost(agentInvoiceId, "approve", "");

    List<String> approved = followAuditOf(() -> edit(c, "POSTPAID_SIM", sim, "20.00").andExpect(status().isOk()));
    assertFees("18.00");
    assertThat(approved).isEmpty();

    managerPost(agentInvoiceId, "paid", "");
    List<String> paid = followAuditOf(() -> edit(c, "POSTPAID_SIM", sim, "22.00").andExpect(status().isOk()));
    assertFees("18.00");
    assertThat(paid).isEmpty();
    assertThat(agentInvoice().get("status").asText()).isEqualTo("PAID");
  }

  // --- A Fee logged after the Client Invoice was sent -------------------------------------------

  @Test
  void lateFeeAfterClientInvoiceSentCountsAtLoggedAmountAndUnchangedWhenPrefilledThenMovesWhenEdited()
      throws Exception {
    Cx c = contract("LSF Late Fee");
    addSim(c, "+1-555-0540", "18.00");
    logFee(c, "5.00");
    UUID invoiceId = sendClientInvoice(c);
    assertFees("23.00");

    UUID lateFee = logFee(c, "4.00");
    assertFees("27.00");

    setBackToDraft(invoiceId);
    assertFees("27.00");

    edit(c, "FEE", lateFee, "6.00").andExpect(status().isOk());
    assertFees("29.00");
  }

  @Test
  void approvedClientInvoicePaysBilledTotalPlusUnbilled() throws Exception {
    Cx c = contract("LSF Approved Client Invoice");
    UUID sim = addSim(c, "+1-555-0550", "18.00");
    logFee(c, "5.00");
    edit(c, "POSTPAID_SIM", sim, "20.00").andExpect(status().isOk());
    approveClientInvoice(sendClientInvoice(c));
    assertFees("25.00");

    logFee(c, "3.00");
    assertFees("28.00");
    addSim(c, "+1-555-0551", "7.00");
    assertFees("35.00");
  }

  @Test
  void legacyBaseAmountLineAddsNoPerSimLines() throws Exception {
    Cx c = contract("LSF Legacy");
    UUID sentFee = logFee(c, "5.00");
    addSim(c, "+1-555-0560", "18.00");
    Contract contract = contractRepository.findById(c.contractId()).orElseThrow();
    ClientInvoice invoice = new ClientInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setTenant(contract.getTenant());
    invoice.setContract(contract);
    invoice.setBillingMonth(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1));
    invoice.setStatus(ClientInvoiceStatus.SENT);
    invoice.setCurrency(contract.getCurrency());
    invoice.setCreatedAt(Instant.now());
    invoice.setSentAt(Instant.now());
    invoice.setSnapshotBaseAmount(new BigDecimal("30.00"));
    invoice.setLinesStored(true);
    clientInvoiceRepository.saveAndFlush(invoice);
    storeLine(invoice, ClientInvoiceLineKind.BASE_AMOUNT, null, "30.00");
    storeLine(invoice, ClientInvoiceLineKind.FEE, feeRepository.findById(sentFee).orElseThrow(), "5.00");

    // The base amount as sent (30) and its Fee (5); the 18.00 SIM is not added on top.
    assertFees("35.00");
    logFee(c, "4.00");
    assertFees("39.00");
  }

  private void storeLine(ClientInvoice invoice, ClientInvoiceLineKind kind, Fee fee, String amount) {
    ClientInvoiceLine line = new ClientInvoiceLine();
    line.setId(UUID.randomUUID());
    line.setTenant(invoice.getTenant());
    line.setClientInvoice(invoice);
    line.setKind(kind);
    line.setFee(fee);
    line.setAmount(new BigDecimal(amount));
    line.setComputedAmount(new BigDecimal(amount));
    line.setCreatedAt(Instant.now());
    clientInvoiceLineRepository.saveAndFlush(line);
  }

  // --- The Manager's override -------------------------------------------------------------------

  @Test
  void managerOverrideChangesOnlySalaryAndNewAdvanceAndSurvivesAnEdit() throws Exception {
    Cx c = contract("LSF Override");
    UUID sim = addSim(c, "+1-555-0570", "18.00");
    UUID agentInvoiceId = sendAgentInvoice();

    managerPost(agentInvoiceId, "override", "{\"salary\":3000.00,\"rolloutAdvanceNewAdvance\":100.00}");
    JsonNode overridden = agentInvoice();
    assertThat(overridden.get("localSupportFees").decimalValue()).isEqualByComparingTo("18.00");
    assertThat(overridden.get("salary").decimalValue()).isEqualByComparingTo("3000.00");

    edit(c, "POSTPAID_SIM", sim, "20.00").andExpect(status().isOk());

    JsonNode after = agentInvoice();
    assertThat(after.get("localSupportFees").decimalValue()).isEqualByComparingTo("20.00");
    assertThat(after.get("salary").decimalValue()).isEqualByComparingTo("3000.00");
    assertThat(after.get("rolloutAdvanceNewAdvance").decimalValue()).isEqualByComparingTo("100.00");
    assertThat(after.get("totalAmount").decimalValue()).isEqualByComparingTo("3120.00");
  }
}
