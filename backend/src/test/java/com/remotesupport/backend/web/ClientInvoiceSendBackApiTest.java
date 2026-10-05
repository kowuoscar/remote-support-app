package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.remotesupport.backend.repository.ClientInvoiceLineRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A Manager sends a sent Client Invoice back to draft with a reason (send-a-client-invoice-back
 * spec, "Backend: send back"). Nothing is recomputed and nothing is cleared: the draft carries the
 * stored lines it was sent with. Driven through the real HTTP seam.
 */
@Import(OtherTenantFixture.class)
class ClientInvoiceSendBackApiTest extends IntegrationTest {

  private static final String REASON = "Line two does not match the carrier bill - zebra-quartz-77";

  @Autowired private ClientInvoiceRepository clientInvoiceRepository;
  @Autowired private ClientInvoiceLineRepository clientInvoiceLineRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private OtherTenantFixture otherTenantFixture;

  private String managerToken;
  private String agentToken;
  private UUID clientId;
  private UUID contractId;

  private void fixture(String clientName) throws Exception {
    managerToken = managerToken();
    agentToken = agentToken();
    clientId = createClient(managerToken, clientName);
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
  }

  private void addPostpaidSim(String number, String fee) throws Exception {
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/sim-cards")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(postpaidSimCardJson(managerToken, contractId, number, fee)))
        .andExpect(status().isCreated());
  }

  private UUID logFee(String amount) throws Exception {
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "tester-" + UUID.randomUUID() + "@example.com");
    MvcResult request =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/requests")
                    .header("Authorization", "Bearer " + testerToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"type":"OTHER","description":"Screen replacement"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(request.getResponse().getContentAsString()).get("id").asText());
    MvcResult fee =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/fees")
                    .header("Authorization", "Bearer " + agentToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"requestId":"%s","feeType":"OTHER","amount":%s}
                        """
                            .formatted(requestId, amount)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(fee.getResponse().getContentAsString()).get("id").asText());
  }

  private JsonNode agentRead() throws Exception {
    return objectMapper.readTree(
        mockMvc
            .perform(
                get("/api/contracts/" + contractId + "/client-invoice")
                    .header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode send() throws Exception {
    return objectMapper.readTree(
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/client-invoice/send")
                    .header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private void editSim(UUID simId, String amount) throws Exception {
    mockMvc
        .perform(
            put("/api/contracts/" + contractId + "/client-invoice/lines")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"kind":"POSTPAID_SIM","sourceId":"%s","amount":%s}
                    """
                        .formatted(simId, amount)))
        .andExpect(status().isOk());
  }

  private ResultActions sendBackAs(String token, UUID invoiceId, String reason) throws Exception {
    return mockMvc.perform(
        post("/api/client-invoices/" + invoiceId + "/send-back")
            .header("Authorization", "Bearer " + token)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("reason", reason))));
  }

  private JsonNode sendBack(UUID invoiceId) throws Exception {
    return objectMapper.readTree(
        sendBackAs(managerToken, invoiceId, REASON)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode byId(UUID invoiceId) throws Exception {
    return objectMapper.readTree(
        mockMvc
            .perform(get("/api/client-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode queueRow(UUID invoiceId) throws Exception {
    JsonNode queue =
        objectMapper.readTree(
            mockMvc
                .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode row : queue) {
      if (row.get("id").asText().equals(invoiceId.toString())) {
        return row;
      }
    }
    return null;
  }

  private void moveToPastMonth(UUID invoiceId) {
    ClientInvoice invoice = clientInvoiceRepository.findById(invoiceId).orElseThrow();
    invoice.setBillingMonth(invoice.getBillingMonth().minusMonths(2));
    clientInvoiceRepository.saveAndFlush(invoice);
  }

  /** The lines, their amounts and computed amounts, as one string to compare before and after. */
  private static String lineShape(JsonNode invoice) {
    StringBuilder out = new StringBuilder();
    for (JsonNode sim : invoice.path("basePostpaidSims")) {
      out.append("SIM ")
          .append(sim.get("simCardId").asText())
          .append(' ')
          .append(sim.get("amount").decimalValue())
          .append('/')
          .append(sim.get("computedAmount"))
          .append('\n');
    }
    for (JsonNode fee : invoice.get("feeLines")) {
      out.append("FEE ")
          .append(fee.get("id").asText())
          .append(' ')
          .append(fee.get("amount").decimalValue())
          .append('/')
          .append(fee.get("computedAmount"))
          .append('\n');
    }
    out.append("TOTAL ").append(invoice.get("totalAmount").decimalValue());
    return out.toString();
  }

  /** A current-month invoice with an edited SIM line (18 billed as 20) and a 5 Fee line, sent. */
  private UUID sentInvoice() throws Exception {
    fixture("Send Back " + UUID.randomUUID());
    addPostpaidSim("+1-555-0400", "18.00");
    logFee("5.00");
    UUID simId = UUID.fromString(agentRead().get("basePostpaidSims").get(0).get("simCardId").asText());
    editSim(simId, "20.00");
    return UUID.fromString(send().get("id").asText());
  }

  @Test
  void sendBackCurrentMonthInvoiceReturnsDraftWithLinesAmountsAndComputedAsSent() throws Exception {
    UUID invoiceId = sentInvoice();
    JsonNode before = byId(invoiceId);

    JsonNode back = sendBack(invoiceId);

    assertThat(back.get("status").asText()).isEqualTo("DRAFT");
    assertThat(back.get("sentBackReason").asText()).isEqualTo(REASON);
    assertThat(back.get("sentBackAt").isNull()).isFalse();
    assertThat(back.get("sentAt").isNull()).isTrue();
    assertThat(lineShape(back)).isEqualTo(lineShape(before));
    assertThat(back.get("basePostpaidSims").get(0).get("amount").decimalValue()).isEqualByComparingTo("20.00");
    assertThat(back.get("basePostpaidSims").get(0).get("computedAmount").decimalValue())
        .isEqualByComparingTo("18.00");
    assertThat(clientInvoiceRepository.findById(invoiceId).orElseThrow().isLinesStored()).isTrue();
  }

  @Test
  void newPostpaidSimAfterSendBackAddsNoLine() throws Exception {
    UUID invoiceId = sentInvoice();
    JsonNode back = sendBack(invoiceId);

    addPostpaidSim("+1-555-0401", "9.00");

    JsonNode after = byId(invoiceId);
    assertThat(after.get("basePostpaidSims")).hasSize(1);
    assertThat(lineShape(after)).isEqualTo(lineShape(back));
  }

  @Test
  void sendBackPastMonthFixtureInvoiceReturnsLinesAsSent() throws Exception {
    UUID invoiceId = sentInvoice();
    JsonNode before = byId(invoiceId);
    moveToPastMonth(invoiceId);

    JsonNode back = sendBack(invoiceId);

    assertThat(back.get("status").asText()).isEqualTo("DRAFT");
    assertThat(lineShape(back)).isEqualTo(lineShape(before));
  }

  @Test
  void sendBackBackfilledInvoiceReturnsSingleBaseLineAndFeeLines() throws Exception {
    fixture("Send Back Backfilled " + UUID.randomUUID());
    Contract contract = contractRepository.findById(contractId).orElseThrow();
    ClientInvoice invoice = new ClientInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setTenant(contract.getTenant());
    invoice.setContract(contract);
    invoice.setBillingMonth(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(1));
    invoice.setStatus(ClientInvoiceStatus.SENT);
    invoice.setCurrency(contract.getCurrency());
    invoice.setCreatedAt(Instant.now());
    invoice.setSentAt(Instant.now());
    invoice.setSnapshotBaseAmount(new BigDecimal("42.00"));
    invoice.setLinesStored(true);
    clientInvoiceRepository.saveAndFlush(invoice);
    ClientInvoiceLine line = new ClientInvoiceLine();
    line.setId(UUID.randomUUID());
    line.setTenant(invoice.getTenant());
    line.setClientInvoice(invoice);
    line.setKind(ClientInvoiceLineKind.BASE_AMOUNT);
    line.setAmount(new BigDecimal("42.00"));
    line.setComputedAmount(new BigDecimal("42.00"));
    line.setCreatedAt(Instant.now());
    clientInvoiceLineRepository.saveAndFlush(line);

    JsonNode back = sendBack(invoice.getId());

    assertThat(back.get("status").asText()).isEqualTo("DRAFT");
    assertThat(back.has("basePostpaidSims")).isFalse();
    assertThat(back.get("baseAmount").decimalValue()).isEqualByComparingTo("42.00");
    assertThat(back.get("totalAmount").decimalValue()).isEqualByComparingTo("42.00");
  }

  @Test
  void reviewQueueExcludesAfterSendBackAndIncludesAfterResendOrderedByNewSentAtWithResentTotal()
      throws Exception {
    UUID invoiceId = sentInvoice();
    assertThat(queueRow(invoiceId)).isNotNull();

    sendBack(invoiceId);
    assertThat(queueRow(invoiceId)).isNull();

    UUID simId = UUID.fromString(agentRead().get("basePostpaidSims").get(0).get("simCardId").asText());
    editSim(simId, "30.00");
    JsonNode resent = send();

    JsonNode row = queueRow(invoiceId);
    assertThat(row).isNotNull();
    assertThat(Instant.parse(row.get("waitingSince").asText()))
        .isEqualTo(Instant.parse(resent.get("sentAt").asText()));
    assertThat(resent.get("totalAmount").decimalValue()).isEqualByComparingTo("35.00");
  }

  @Test
  void sendBackTwiceWithResendBetweenSucceeds() throws Exception {
    UUID invoiceId = sentInvoice();
    sendBack(invoiceId);
    assertThat(send().get("status").asText()).isEqualTo("SENT");

    JsonNode second = sendBack(invoiceId);

    assertThat(second.get("status").asText()).isEqualTo("DRAFT");
    assertThat(
            clientInvoiceLineRepository.findAll().stream()
                .filter(l -> l.getClientInvoice().getId().equals(invoiceId))
                .count())
        .as("a send-back and a resend store no second row for a SIM or a Fee")
        .isEqualTo(2);
  }

  @Test
  void sendBackOfDraftOrApprovedIsConflict() throws Exception {
    fixture("Send Back Conflict " + UUID.randomUUID());
    addPostpaidSim("+1-555-0402", "18.00");
    UUID invoiceId = UUID.fromString(agentRead().get("id").asText());
    sendBackAs(managerToken, invoiceId, REASON).andExpect(status().isConflict());

    send();
    mockMvc
        .perform(
            post("/api/client-invoices/" + invoiceId + "/approve")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());
    sendBackAs(managerToken, invoiceId, REASON).andExpect(status().isConflict());
  }

  @Test
  void blankAndThousandAndOneCharacterReasonAreBadRequest() throws Exception {
    UUID invoiceId = sentInvoice();

    sendBackAs(managerToken, invoiceId, "   ").andExpect(status().isBadRequest());
    sendBackAs(managerToken, invoiceId, "x".repeat(1001)).andExpect(status().isBadRequest());
    sendBackAs(managerToken, invoiceId, "x".repeat(1000)).andExpect(status().isOk());
  }

  @Test
  void approveAfterSendBackIsConflict() throws Exception {
    UUID invoiceId = sentInvoice();
    sendBack(invoiceId);

    mockMvc
        .perform(
            post("/api/client-invoices/" + invoiceId + "/approve")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isConflict());
  }

  @Test
  void agentAndTesterGet403() throws Exception {
    UUID invoiceId = sentInvoice();
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "tester-" + UUID.randomUUID() + "@example.com");

    sendBackAs(agentToken, invoiceId, REASON).andExpect(status().isForbidden());
    sendBackAs(testerToken, invoiceId, REASON).andExpect(status().isForbidden());
    assertThat(byId(invoiceId).get("status").asText()).isEqualTo("SENT");
  }

  @Test
  void otherTenantInvoiceIs404() throws Exception {
    managerToken = managerToken();
    UUID other = otherTenantFixture.sentClientInvoiceInAnotherTenant();

    sendBackAs(managerToken, other, REASON).andExpect(status().isNotFound());
    sendBackAs(managerToken, UUID.randomUUID(), REASON).andExpect(status().isNotFound());
  }

  @Test
  void testerReadOfSentBackIs403AndAfterResendHasNullReason() throws Exception {
    UUID invoiceId = sentInvoice();
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "tester-" + UUID.randomUUID() + "@example.com");
    sendBack(invoiceId);

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/client-invoice")
                .header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isForbidden());

    send();
    JsonNode read =
        objectMapper.readTree(
            mockMvc
                .perform(
                    get("/api/contracts/" + contractId + "/client-invoice")
                        .header("Authorization", "Bearer " + testerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(read.get("status").asText()).isEqualTo("SENT");
    assertThat(read.get("totalAmount").decimalValue()).isEqualByComparingTo("25.00");
    assertThat(read.path("sentBackReason").isNull()).isTrue();
    assertThat(read.path("sentBackAt").isNull()).isTrue();
  }

  @Test
  void managerReadsResentInvoiceWithSentBackReasonAndPdfCarriesNone() throws Exception {
    UUID invoiceId = sentInvoice();
    sendBack(invoiceId);
    send();

    JsonNode read = byId(invoiceId);
    assertThat(read.get("status").asText()).isEqualTo("SENT");
    assertThat(read.get("sentBackReason").asText()).isEqualTo(REASON);
    assertThat(read.get("sentBackAt").isNull()).isFalse();

    byte[] pdf =
        mockMvc
            .perform(
                get("/api/client-invoices/" + invoiceId + "/pdf")
                    .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).doesNotContain("zebra-quartz-77");
  }

  @Test
  void auditLineIsStatusChangeSentToDraftWithoutReasonText() throws Exception {
    UUID invoiceId = sentInvoice();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    auditLogger.addAppender(appender);
    root.addAppender(appender);
    try {
      sendBack(invoiceId);

      String logged =
          String.join("\n", appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
      assertThat(logged).contains("action=STATUS_CHANGE");
      assertThat(logged).contains("entity=ClientInvoice");
      assertThat(logged).contains("entityId=" + invoiceId);
      assertThat(logged).contains("oldStatus=SENT");
      assertThat(logged).contains("newStatus=DRAFT");
      assertThat(logged).contains("actorUserId=22222222-2222-2222-2222-222222222222");
      assertThat(logged).contains("tenantId=11111111-1111-1111-1111-111111111111");
      assertThat(logged).doesNotContain("zebra-quartz-77");
    } finally {
      auditLogger.detachAppender(appender);
      root.detachAppender(appender);
    }
  }

  // --- The Agent reaches an invoice by its id (agent-reaches-a-client-invoice-by-its-id) -----------

  private static final String FOLLOW_AUDIT = "action=AGENT_INVOICE_LOCAL_SUPPORT_FEES_FOLLOWED";

  /** The Agent whose Agent Invoice the scenario reads; the seeded one unless a test made its own. */
  private UUID scenarioAgentId = SEEDED_AGENT_ID;

  /** A new Agent with a login, signed in: its username and token. */
  private record NewAgent(UUID id, String token) {}

  private NewAgent newAgent(String name) throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/agents")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of(
                                "name",
                                name,
                                "country",
                                "UNITED_STATES",
                                "salaryAmount",
                                "2000.00",
                                "username",
                                "agent-" + UUID.randomUUID() + "@agents.example"))))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
    return new NewAgent(
        UUID.fromString(body.get("id").asText()),
        loginAs(body.get("loginUsername").asText(), body.get("password").asText()));
  }

  /** A fixture whose Contract belongs to a new Agent with a login, so one test can hold several. */
  private void fixtureWithNewAgent(String name) throws Exception {
    managerToken = managerToken();
    NewAgent agent = newAgent(name);
    scenarioAgentId = agent.id();
    agentToken = agent.token();
    clientId = createClient(managerToken, "Client of " + name + " " + UUID.randomUUID());
    contractId = createContract(managerToken, clientId, scenarioAgentId);
  }

  private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(request.header("Authorization", "Bearer " + token));
  }

  private ResultActions getById(String token, UUID invoiceId, String suffix) throws Exception {
    return as(token, get("/api/client-invoices/" + invoiceId + suffix));
  }

  private ResultActions putLineById(String token, UUID invoiceId, String kind, UUID sourceId, String amount)
      throws Exception {
    return as(
        token,
        put("/api/client-invoices/" + invoiceId + "/lines")
            .contentType(APPLICATION_JSON)
            .content(
                "{\"kind\":\"%s\",\"sourceId\":%s,\"amount\":%s}"
                    .formatted(kind, sourceId == null ? "null" : "\"" + sourceId + "\"", amount)));
  }

  private ResultActions postSendById(String token, UUID invoiceId) throws Exception {
    return as(token, post("/api/client-invoices/" + invoiceId + "/send"));
  }

  private ResultActions postFileById(String token, UUID invoiceId, String name, String content) throws Exception {
    return as(
        token,
        multipart("/api/client-invoices/" + invoiceId + "/files")
            .file(new MockMultipartFile("file", name, "application/pdf", content.getBytes())));
  }

  private JsonNode body(ResultActions actions) throws Exception {
    return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
  }

  private UUID simIdOf(JsonNode invoice) {
    return UUID.fromString(invoice.get("basePostpaidSims").get(0).get("simCardId").asText());
  }

  private UUID lateFeeIdOf(JsonNode invoice, UUID sentFeeId) {
    for (JsonNode fee : invoice.get("feeLines")) {
      if (!fee.get("id").asText().equals(sentFeeId.toString())) {
        return UUID.fromString(fee.get("id").asText());
      }
    }
    throw new AssertionError("no late Fee line");
  }

  private JsonNode agentInvoice() throws Exception {
    return body(
        as(agentToken, get("/api/agents/" + scenarioAgentId + "/invoice")).andExpect(status().isOk()));
  }

  private BigDecimal agentInvoiceFees() throws Exception {
    return agentInvoice().get("localSupportFees").decimalValue();
  }

  @FunctionalInterface
  private interface Action {
    void run() throws Exception;
  }

  private List<String> followAuditOf(Action action) throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);
    try {
      action.run();
      return appender.list.stream()
          .map(ILoggingEvent::getFormattedMessage)
          .filter(m -> m.contains(FOLLOW_AUDIT))
          .toList();
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  /** Like {@link #sentInvoice} but with a Carrier Invoice File attached before the send. */
  private UUID sentInvoiceWithFile() throws Exception {
    fixture("By Id " + UUID.randomUUID());
    addPostpaidSim("+1-555-0410", "18.00");
    logFee("5.00");
    mockMvc
        .perform(
            multipart("/api/contracts/" + contractId + "/client-invoice/files")
                .file(new MockMultipartFile("file", "carrier.pdf", "application/pdf", "carrier-bytes".getBytes()))
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isCreated());
    editSim(simIdOf(agentRead()), "20.00");
    return UUID.fromString(send().get("id").asText());
  }

  @Test
  void agentReadsSentBackInvoiceAndFilesById() throws Exception {
    UUID invoiceId = sentInvoiceWithFile();
    sendBack(invoiceId);

    JsonNode read = body(getById(agentToken, invoiceId, "").andExpect(status().isOk()));
    assertThat(read.get("status").asText()).isEqualTo("DRAFT");
    assertThat(read.get("sentBackReason").asText()).isEqualTo(REASON);
    assertThat(read.get("sentBackAt").isNull()).isFalse();

    JsonNode files = body(getById(agentToken, invoiceId, "/files").andExpect(status().isOk()));
    assertThat(files).hasSize(1);
    getById(agentToken, invoiceId, "/files/" + files.get(0).get("id").asText())
        .andExpect(status().isOk())
        .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("carrier-bytes"));
  }

  @Test
  void otherAgentAndTesterGet403OnEveryAgentByIdRoute() throws Exception {
    UUID invoiceId = sentInvoiceWithFile();
    sendBack(invoiceId);
    UUID fileId = UUID.fromString(body(getById(agentToken, invoiceId, "/files")).get(0).get("id").asText());
    UUID simId = simIdOf(byId(invoiceId));
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "tester-" + UUID.randomUUID() + "@example.com");
    // Another Agent of the Tenant, with a Contract of their own, never this one.
    String notOwner = newAgent("Other Agent").token();

    for (String token : List.of(notOwner, testerToken)) {
      getById(token, invoiceId, "").andExpect(status().isForbidden());
      getById(token, invoiceId, "/files").andExpect(status().isForbidden());
      getById(token, invoiceId, "/files/" + fileId).andExpect(status().isForbidden());
      postFileById(token, invoiceId, "x.pdf", "x").andExpect(status().isForbidden());
      putLineById(token, invoiceId, "POSTPAID_SIM", simId, "99.00").andExpect(status().isForbidden());
      postSendById(token, invoiceId).andExpect(status().isForbidden());
    }
    JsonNode after = byId(invoiceId);
    assertThat(after.get("status").asText()).isEqualTo("DRAFT");
    assertThat(after.get("basePostpaidSims").get(0).get("amount").decimalValue()).isEqualByComparingTo("20.00");
    assertThat(after.get("files")).hasSize(1);
  }

  @Test
  void managerGets403OnByIdLinesAndSend() throws Exception {
    UUID invoiceId = sentInvoice();
    sendBack(invoiceId);
    UUID simId = simIdOf(byId(invoiceId));

    putLineById(managerToken, invoiceId, "POSTPAID_SIM", simId, "99.00").andExpect(status().isForbidden());
    postSendById(managerToken, invoiceId).andExpect(status().isForbidden());

    JsonNode after = byId(invoiceId);
    assertThat(after.get("status").asText()).isEqualTo("DRAFT");
    assertThat(after.get("basePostpaidSims").get(0).get("amount").decimalValue()).isEqualByComparingTo("20.00");
  }

  @Test
  void agentGets403OnByIdPdfApproveAndSendBack() throws Exception {
    UUID invoiceId = sentInvoice();

    getById(agentToken, invoiceId, "/pdf").andExpect(status().isForbidden());
    as(agentToken, post("/api/client-invoices/" + invoiceId + "/approve")).andExpect(status().isForbidden());
    sendBackAs(agentToken, invoiceId, REASON).andExpect(status().isForbidden());

    assertThat(byId(invoiceId).get("status").asText()).isEqualTo("SENT");
  }

  @Test
  void otherTenantInvoiceGives404OnEveryByIdRoute() throws Exception {
    fixture("Tenant Wall " + UUID.randomUUID());
    UUID other = otherTenantFixture.sentClientInvoiceInAnotherTenant();
    UUID anyFile = UUID.randomUUID();
    long invoicesBefore = clientInvoiceRepository.count();

    for (String token : List.of(agentToken, managerToken)) {
      getById(token, other, "").andExpect(status().isNotFound());
      getById(token, other, "/files").andExpect(status().isNotFound());
      getById(token, other, "/files/" + anyFile).andExpect(status().isNotFound());
      postFileById(token, other, "x.pdf", "x").andExpect(status().isNotFound());
    }
    putLineById(agentToken, other, "BASE_AMOUNT", null, "1.00").andExpect(status().isNotFound());
    postSendById(agentToken, other).andExpect(status().isNotFound());
    // An id that exists nowhere is the same 404, and nothing here ever creates an invoice.
    postSendById(agentToken, UUID.randomUUID()).andExpect(status().isNotFound());
    getById(agentToken, UUID.randomUUID(), "").andExpect(status().isNotFound());

    assertThat(clientInvoiceRepository.count()).isEqualTo(invoicesBefore);
    assertThat(clientInvoiceRepository.findById(other).orElseThrow().getStatus())
        .isEqualTo(ClientInvoiceStatus.SENT);
  }

  @Test
  void agentEditsSentLineAndLateFeeLineById() throws Exception {
    UUID invoiceId = sentInvoice();
    UUID sentFee = UUID.fromString(byId(invoiceId).get("feeLines").get(0).get("id").asText());
    sendBack(invoiceId);
    logFee("7.00");

    JsonNode shown = body(getById(agentToken, invoiceId, "").andExpect(status().isOk()));
    UUID lateFee = lateFeeIdOf(shown, sentFee);
    JsonNode late = shown.get("feeLines").get(1);
    assertThat(late.get("amount").decimalValue()).isEqualByComparingTo("7.00");
    assertThat(late.get("edited").asBoolean()).isFalse();

    JsonNode simEdited =
        body(putLineById(agentToken, invoiceId, "POSTPAID_SIM", simIdOf(shown), "21.00").andExpect(status().isOk()));
    assertThat(simEdited.get("basePostpaidSims").get(0).get("amount").decimalValue()).isEqualByComparingTo("21.00");
    assertThat(simEdited.get("basePostpaidSims").get(0).get("computedAmount").decimalValue())
        .isEqualByComparingTo("18.00");

    JsonNode lateEdited =
        body(putLineById(agentToken, invoiceId, "FEE", lateFee, "8.00").andExpect(status().isOk()));
    assertThat(lateEdited.get("feeLines").get(1).get("amount").decimalValue()).isEqualByComparingTo("8.00");
    assertThat(lateEdited.get("feeLines").get(1).get("computedAmount").decimalValue()).isEqualByComparingTo("7.00");
    assertThat(lateEdited.get("feeLines").get(1).get("edited").asBoolean()).isTrue();
    assertThat(lateEdited.get("totalAmount").decimalValue()).isEqualByComparingTo("34.00");

    // edit-client-invoice-lines' validation and responses, unchanged on this route.
    putLineById(agentToken, invoiceId, "FEE", lateFee, "-1.00")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("amount must not be negative"));
    putLineById(agentToken, invoiceId, "FEE", UUID.randomUUID(), "1.00").andExpect(status().isNotFound());
  }

  @Test
  void agentAttachesFileById() throws Exception {
    UUID invoiceId = sentInvoice();
    sendBack(invoiceId);

    postFileById(agentToken, invoiceId, "extra.pdf", "more-bytes")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.filename").value("extra.pdf"));

    assertThat(body(getById(agentToken, invoiceId, "/files").andExpect(status().isOk()))).hasSize(1);
    // A sent invoice takes no file: the plain 409 it is today.
    send();
    postFileById(agentToken, invoiceId, "late.pdf", "x").andExpect(status().isConflict());
  }

  @Test
  void resendByIdFreezesLinesShownIncludingLateFees() throws Exception {
    UUID invoiceId = sentInvoice();
    sendBack(invoiceId);
    logFee("7.00");
    logFee("3.00");
    JsonNode shown = body(getById(agentToken, invoiceId, "").andExpect(status().isOk()));
    assertThat(shown.get("feeLines")).hasSize(3);
    UUID firstLate = UUID.fromString(shown.get("feeLines").get(1).get("id").asText());
    putLineById(agentToken, invoiceId, "FEE", firstLate, "9.00").andExpect(status().isOk());
    putLineById(agentToken, invoiceId, "POSTPAID_SIM", simIdOf(shown), "22.00").andExpect(status().isOk());
    JsonNode justBefore = body(getById(agentToken, invoiceId, ""));

    JsonNode resent = body(postSendById(agentToken, invoiceId).andExpect(status().isOk()));

    assertThat(resent.get("status").asText()).isEqualTo("SENT");
    assertThat(lineShape(resent)).isEqualTo(lineShape(justBefore));
    assertThat(resent.get("totalAmount").decimalValue()).isEqualByComparingTo("39.00");
    assertThat(lineShape(byId(invoiceId))).isEqualTo(lineShape(justBefore));
    assertThat(
            clientInvoiceLineRepository.findAll().stream()
                .filter(l -> l.getClientInvoice().getId().equals(invoiceId))
                .count())
        .as("one row per line shown: the SIM, the sent Fee and both late Fees, none twice")
        .isEqualTo(4);
  }

  @Test
  void feeAfterResendDoesNotAppear() throws Exception {
    UUID invoiceId = sentInvoice();
    sendBack(invoiceId);
    JsonNode resent = body(postSendById(agentToken, invoiceId).andExpect(status().isOk()));

    logFee("50.00");

    JsonNode after = byId(invoiceId);
    assertThat(lineShape(after)).isEqualTo(lineShape(resent));
    assertThat(after.get("feeLines")).hasSize(1);
  }

  @Test
  void pastMonthSentBackInvoiceReadEditAttachAndResendById() throws Exception {
    UUID invoiceId = sentInvoice();
    moveToPastMonth(invoiceId);
    sendBack(invoiceId);

    JsonNode read = body(getById(agentToken, invoiceId, "").andExpect(status().isOk()));
    assertThat(read.get("sentBackReason").asText()).isEqualTo(REASON);
    putLineById(agentToken, invoiceId, "POSTPAID_SIM", simIdOf(read), "22.00").andExpect(status().isOk());
    postFileById(agentToken, invoiceId, "past.pdf", "past-bytes").andExpect(status().isCreated());

    JsonNode resent = body(postSendById(agentToken, invoiceId).andExpect(status().isOk()));

    assertThat(resent.get("status").asText()).isEqualTo("SENT");
    assertThat(resent.get("basePostpaidSims").get(0).get("amount").decimalValue()).isEqualByComparingTo("22.00");
    assertThat(byId(invoiceId).get("basePostpaidSims").get(0).get("amount").decimalValue())
        .isEqualByComparingTo("22.00");
    JsonNode files = body(getById(managerToken, invoiceId, "/files").andExpect(status().isOk()));
    assertThat(files).hasSize(1);
    getById(managerToken, invoiceId, "/files/" + files.get(0).get("id").asText())
        .andExpect(status().isOk())
        .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("past-bytes"));
  }

  @Test
  void pastMonthNeverSentDraftIs409WithCodeAndUnchanged() throws Exception {
    fixture("Past Draft " + UUID.randomUUID());
    addPostpaidSim("+1-555-0420", "18.00");
    JsonNode draft = agentRead();
    UUID invoiceId = UUID.fromString(draft.get("id").asText());
    UUID simId = simIdOf(draft);
    moveToPastMonth(invoiceId);

    // Reading it is fine; writing it is not.
    getById(agentToken, invoiceId, "").andExpect(status().isOk());
    List<ResultActions> refusals =
        List.of(
            postSendById(agentToken, invoiceId),
            putLineById(agentToken, invoiceId, "POSTPAID_SIM", simId, "20.00"),
            postFileById(agentToken, invoiceId, "x.pdf", "x"),
            postFileById(managerToken, invoiceId, "x.pdf", "x"));
    for (ResultActions refused : refusals) {
      refused
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("PAST_MONTH_DRAFT_NOT_SENDABLE"))
          .andExpect(jsonPath("$.message").isNotEmpty());
    }

    ClientInvoice after = clientInvoiceRepository.findById(invoiceId).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(ClientInvoiceStatus.DRAFT);
    assertThat(after.isLinesStored()).isFalse();
    assertThat(after.getSentAt()).isNull();
    assertThat(
            clientInvoiceLineRepository.findAll().stream()
                .filter(l -> l.getClientInvoice().getId().equals(invoiceId))
                .count())
        .isZero();
    assertThat(body(getById(managerToken, invoiceId, "/files"))).isEmpty();
  }

  @Test
  void currentMonthNeverSentDraftIsAccepted() throws Exception {
    fixture("Current Draft " + UUID.randomUUID());
    addPostpaidSim("+1-555-0430", "18.00");
    JsonNode draft = agentRead();
    UUID invoiceId = UUID.fromString(draft.get("id").asText());

    putLineById(agentToken, invoiceId, "POSTPAID_SIM", simIdOf(draft), "19.00").andExpect(status().isOk());
    postFileById(agentToken, invoiceId, "ok.pdf", "ok").andExpect(status().isCreated());
    JsonNode sent = body(postSendById(agentToken, invoiceId).andExpect(status().isOk()));

    assertThat(sent.get("status").asText()).isEqualTo("SENT");
    assertThat(sent.get("basePostpaidSims").get(0).get("amount").decimalValue()).isEqualByComparingTo("19.00");
    assertThat(sent.get("files")).hasSize(1);
  }

  /** A sent Client Invoice of a new Agent whose Agent Invoice is in {@code state} (draft, sent, approved). */
  private UUID sentInvoiceWithAgentInvoice(String state) throws Exception {
    fixtureWithNewAgent("Agent " + state);
    addPostpaidSim("+1-555-04" + (state.length() + 40), "18.00");
    logFee("5.00");
    editSim(simIdOf(agentRead()), "20.00");
    UUID invoiceId = UUID.fromString(send().get("id").asText());
    if (!state.equals("draft")) {
      UUID agentInvoiceId =
          UUID.fromString(
              body(as(agentToken, post("/api/agents/" + scenarioAgentId + "/invoice/send")).andExpect(status().isOk()))
                  .get("id")
                  .asText());
      if (state.equals("approved")) {
        as(managerToken, post("/api/agent-invoices/" + agentInvoiceId + "/approve").contentType(APPLICATION_JSON))
            .andExpect(status().isOk());
      }
    }
    return invoiceId;
  }

  @Test
  void sendBackAndResendLeaveAgentInvoiceLocalSupportFeesUnchangedInDraftSentAndApproved() throws Exception {
    for (String state : List.of("draft", "sent", "approved")) {
      UUID invoiceId = sentInvoiceWithAgentInvoice(state);
      logFee("7.00");
      BigDecimal before = agentInvoiceFees();

      sendBack(invoiceId);
      assertThat(agentInvoiceFees()).as(state + ": after the send-back").isEqualByComparingTo(before);
      assertThat(body(getById(agentToken, invoiceId, "")).get("feeLines")).hasSize(2);

      postSendById(agentToken, invoiceId).andExpect(status().isOk());
      assertThat(agentInvoiceFees()).as(state + ": after the resend").isEqualByComparingTo(before);
    }
  }

  @Test
  void byIdEditMovesSentAgentInvoiceByTheDifferenceAndNotApprovedOne() throws Exception {
    UUID sentInvoice = sentInvoiceWithAgentInvoice("sent");
    UUID sentFee = UUID.fromString(byId(sentInvoice).get("feeLines").get(0).get("id").asText());
    logFee("7.00");
    sendBack(sentInvoice);
    JsonNode shown = body(getById(agentToken, sentInvoice, ""));
    UUID lateFee = lateFeeIdOf(shown, sentFee);
    BigDecimal base = agentInvoiceFees();

    List<String> simAudit =
        followAuditOf(
            () -> putLineById(agentToken, sentInvoice, "POSTPAID_SIM", simIdOf(shown), "23.00").andExpect(status().isOk()));
    assertThat(simAudit).hasSize(1);
    assertThat(simAudit.get(0)).contains("oldAmount=" + base).contains("newAmount=" + base.add(new BigDecimal("3.00")));
    assertThat(agentInvoiceFees()).isEqualByComparingTo(base.add(new BigDecimal("3.00")));

    List<String> feeAudit =
        followAuditOf(() -> putLineById(agentToken, sentInvoice, "FEE", lateFee, "9.00").andExpect(status().isOk()));
    assertThat(feeAudit).hasSize(1);
    assertThat(feeAudit.get(0)).contains("oldAmount=" + base.add(new BigDecimal("3.00"))).contains("newAmount=" + base.add(new BigDecimal("5.00")));
    assertThat(agentInvoiceFees()).isEqualByComparingTo(base.add(new BigDecimal("5.00")));
    assertThat(agentInvoice().get("status").asText()).isEqualTo("SENT");

    UUID approvedInvoice = sentInvoiceWithAgentInvoice("approved");
    sendBack(approvedInvoice);
    JsonNode approvedShown = body(getById(agentToken, approvedInvoice, ""));
    BigDecimal approvedFees = agentInvoiceFees();
    List<String> none =
        followAuditOf(
            () ->
                putLineById(agentToken, approvedInvoice, "POSTPAID_SIM", simIdOf(approvedShown), "23.00")
                    .andExpect(status().isOk()));
    assertThat(none).isEmpty();
    assertThat(agentInvoiceFees()).isEqualByComparingTo(approvedFees);
    assertThat(agentInvoice().get("status").asText()).isEqualTo("APPROVED");
  }
}
