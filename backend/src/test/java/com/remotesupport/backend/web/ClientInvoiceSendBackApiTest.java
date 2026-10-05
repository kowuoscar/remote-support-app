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
import com.remotesupport.backend.domain.Country;
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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

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

  private ResultActions sentBackListAs(String token) throws Exception {
    return mockMvc.perform(get("/api/client-invoices/sent-back").header("Authorization", "Bearer " + token));
  }

  private JsonNode sentBackList(String token) throws Exception {
    return objectMapper.readTree(
        sentBackListAs(token).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }

  private static JsonNode listed(JsonNode list, UUID invoiceId) {
    for (JsonNode row : list) {
      if (row.get("id").asText().equals(invoiceId.toString())) {
        return row;
      }
    }
    return null;
  }

  private static int indexOf(JsonNode list, UUID invoiceId) {
    for (int i = 0; i < list.size(); i++) {
      if (list.get(i).get("id").asText().equals(invoiceId.toString())) {
        return i;
      }
    }
    return -1;
  }

  @Test
  void agentListsOwnSentBackInvoicesOldestFirstWithFieldsAndNoAmounts() throws Exception {
    UUID first = sentInvoice();
    UUID firstContractId = contractId;
    UUID second = sentInvoice();
    moveToPastMonth(second);
    UUID neverSentBack = sentInvoice();
    sendBack(second);
    Thread.sleep(5);
    sendBack(first);

    JsonNode list = sentBackList(agentToken);

    assertThat(indexOf(list, second)).isGreaterThanOrEqualTo(0);
    assertThat(indexOf(list, first)).isGreaterThan(indexOf(list, second));
    assertThat(listed(list, neverSentBack)).isNull();
    JsonNode row = listed(list, first);
    assertThat(row.get("contractId").asText()).isEqualTo(firstContractId.toString());
    assertThat(row.get("clientName").asText()).startsWith("Send Back ");
    assertThat(row.get("country").asText()).isNotBlank();
    assertThat(row.get("billingMonth").asText())
        .isEqualTo(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).toString());
    assertThat(row.get("currency").asText()).isNotBlank();
    assertThat(row.get("sentBackReason").asText()).isEqualTo(REASON);
    assertThat(Instant.parse(row.get("sentBackAt").asText())).isNotNull();
    assertThat(row.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "id", "contractId", "clientName", "country", "billingMonth", "currency", "sentBackAt", "sentBackReason");
    for (int i = 1; i < list.size(); i++) {
      assertThat(Instant.parse(list.get(i - 1).get("sentBackAt").asText()))
          .isBeforeOrEqualTo(Instant.parse(list.get(i).get("sentBackAt").asText()));
    }
  }

  @Test
  void otherAgentsInvoicesAndOtherTenantsAreAbsent() throws Exception {
    UUID mine = sentInvoice();
    sendBack(mine);

    UUID otherAgentId = createAgent(managerToken, "Other Agent", Country.SPAIN);
    UUID otherContractId = createContract(managerToken, clientId, otherAgentId);
    Contract otherContract = contractRepository.findById(otherContractId).orElseThrow();
    UUID otherAgentsInvoice = sentBackInvoiceFor(otherContract);

    UUID otherTenant = otherTenantFixture.sentClientInvoiceInAnotherTenant();
    ClientInvoice foreign = clientInvoiceRepository.findById(otherTenant).orElseThrow();
    foreign.setStatus(ClientInvoiceStatus.DRAFT);
    foreign.setSentAt(null);
    foreign.setSentBackAt(Instant.now());
    foreign.setSentBackReason("foreign");
    clientInvoiceRepository.saveAndFlush(foreign);

    JsonNode list = sentBackList(agentToken);

    assertThat(listed(list, mine)).isNotNull();
    assertThat(listed(list, otherAgentsInvoice)).isNull();
    assertThat(listed(list, otherTenant)).isNull();
  }

  private UUID sentBackInvoiceFor(Contract contract) {
    ClientInvoice invoice = new ClientInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setTenant(contract.getTenant());
    invoice.setContract(contract);
    invoice.setBillingMonth(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1));
    invoice.setStatus(ClientInvoiceStatus.DRAFT);
    invoice.setCurrency(contract.getCurrency());
    invoice.setCreatedAt(Instant.now());
    invoice.setSentBackAt(Instant.now());
    invoice.setSentBackReason("not yours");
    return clientInvoiceRepository.saveAndFlush(invoice).getId();
  }

  @Test
  void resentInvoiceLeavesTheListAndSecondSendBackReentersIt() throws Exception {
    UUID invoiceId = sentInvoice();
    sendBack(invoiceId);
    JsonNode firstRow = listed(sentBackList(agentToken), invoiceId);
    assertThat(firstRow).isNotNull();

    send();
    assertThat(listed(sentBackList(agentToken), invoiceId)).isNull();

    Thread.sleep(5);
    sendBackAs(managerToken, invoiceId, "second reason").andExpect(status().isOk());
    JsonNode secondRow = listed(sentBackList(agentToken), invoiceId);
    assertThat(secondRow).isNotNull();
    assertThat(secondRow.get("sentBackReason").asText()).isEqualTo("second reason");
    assertThat(Instant.parse(secondRow.get("sentBackAt").asText()))
        .isAfter(Instant.parse(firstRow.get("sentBackAt").asText()));
  }

  @Test
  void managerAndTesterGet403OnTheList() throws Exception {
    UUID invoiceId = sentInvoice();
    sendBack(invoiceId);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "tester-" + UUID.randomUUID() + "@example.com");

    sentBackListAs(managerToken).andExpect(status().isForbidden());
    sentBackListAs(testerToken).andExpect(status().isForbidden());
  }
}
