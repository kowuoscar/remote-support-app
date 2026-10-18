package com.remotesupport.backend.web;

import static com.remotesupport.backend.support.ClientInvoiceMonthTestSupport.currentMonth;
import static com.remotesupport.backend.support.ClientInvoiceMonthTestSupport.postTheMonth;
import static com.remotesupport.backend.support.ClientInvoiceMonthTestSupport.sendTheMonth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.domain.BillingMonth;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.repository.ClientInvoiceLineRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.support.CarrierInvoiceTestSupport;
import com.remotesupport.backend.support.IntegrationTest;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The Agent sends a whole month of Client Invoices in one call (agent-sends-the-month spec, "The
 * month send"), through the real HTTP seam with real Postgres and SeaweedFS. Every case builds a
 * fresh Agent, because the seeded Agent holds no Contract in the test database and several cases
 * need several.
 */
class ClientInvoiceMonthSendApiTest extends IntegrationTest {

  @Autowired private ClientInvoiceRepository clientInvoiceRepository;
  @Autowired private ClientInvoiceLineRepository clientInvoiceLineRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String managerToken;

  /** A fresh Agent with a login: its id and bearer token. */
  private record NewAgent(UUID id, String token) {}

  private NewAgent newAgent() throws Exception {
    managerToken = managerToken();
    String email = "month-agent-" + UUID.randomUUID() + "@agents.example";
    MvcResult created =
        mockMvc
            .perform(
                post("/api/agents")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Month Agent","country":"UNITED_STATES","salaryAmount":2000.00,"username":"%s"}
                        """
                            .formatted(email)))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
    return new NewAgent(UUID.fromString(body.get("id").asText()), loginAs(email, body.get("password").asText()));
  }

  /** A Contract for the Agent with a new Client; {@code postpaidSims} Postpaid SIMs on it. */
  private UUID contract(NewAgent agent, int postpaidSims) throws Exception {
    UUID clientId = createClient(managerToken, "Month Client " + UUID.randomUUID());
    UUID contractId = createContract(managerToken, clientId, agent.id());
    for (int i = 0; i < postpaidSims; i++) {
      addUniquePostpaidSim(managerToken, contractId);
    }
    return contractId;
  }

  /** The Agent opens the Contract's draft (as a page view does) and returns it. */
  private JsonNode open(NewAgent agent, UUID contractId) throws Exception {
    return json(
        mockMvc
            .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agent.token()))
            .andExpect(status().isOk())
            .andReturn());
  }

  private void attachFiles(NewAgent agent, UUID contractId) throws Exception {
    CarrierInvoiceTestSupport.attachCarrierInvoicesForEveryPostpaidLineOfCurrentMonth(
        mockMvc, objectMapper, contractId, agent.token());
  }

  private ClientInvoice invoiceOf(UUID contractId) {
    return clientInvoiceRepository
        .findByContractIdAndBillingMonth(contractId, BillingMonth.current())
        .orElseThrow();
  }

  private boolean hasInvoice(UUID contractId) {
    return clientInvoiceRepository.findByContractIdAndBillingMonth(contractId, BillingMonth.current()).isPresent();
  }

  private JsonNode json(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private Set<UUID> ids(JsonNode array) {
    Set<UUID> ids = new HashSet<>();
    array.forEach(id -> ids.add(UUID.fromString(id.asText())));
    return ids;
  }

  private long lineRows(UUID invoiceId) {
    return clientInvoiceLineRepository.findByClientInvoiceId(invoiceId).size();
  }

  private JsonNode invoiceBy(String token, UUID invoiceId) throws Exception {
    return json(
        mockMvc
            .perform(get("/api/client-invoices/" + invoiceId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn());
  }

  private ResultActions sendBack(UUID invoiceId) throws Exception {
    return mockMvc.perform(
        post("/api/client-invoices/" + invoiceId + "/send-back")
            .header("Authorization", "Bearer " + managerToken)
            .contentType(APPLICATION_JSON)
            .content("{\"reason\":\"Please check the second line\"}"));
  }

  private void assertNothingSent(List<UUID> contractIds) {
    for (UUID contractId : contractIds) {
      if (!hasInvoice(contractId)) {
        continue;
      }
      ClientInvoice invoice = invoiceOf(contractId);
      assertThat(invoice.getStatus()).isEqualTo(ClientInvoiceStatus.DRAFT);
      assertThat(invoice.isLinesStored()).isFalse();
      assertThat(invoice.getSentAt()).isNull();
      assertThat(invoice.getFirstSentAt()).isNull();
      assertThat(lineRows(invoice.getId())).isZero();
    }
  }

  @Test
  void sendsThreeContractsTogetherAtOneInstantStoringLinesAndTheEmptyOneAtZero() throws Exception {
    NewAgent agent = newAgent();
    UUID withFileOne = contract(agent, 1);
    UUID withFileTwo = contract(agent, 1);
    UUID nothing = contract(agent, 0);
    attachFiles(agent, withFileOne);
    attachFiles(agent, withFileTwo);
    assertThat(hasInvoice(nothing)).as("the nothing-to-bill Contract was never viewed").isFalse();

    MvcResult sent = sendTheMonth(mockMvc, agent.token());

    JsonNode body = json(sent);
    assertThat(body.get("billingMonth").asText()).isEqualTo(BillingMonth.current().toString());
    assertThat(ids(body.get("invoiceIds")))
        .containsExactlyInAnyOrder(
            invoiceOf(withFileOne).getId(), invoiceOf(withFileTwo).getId(), invoiceOf(nothing).getId());
    Instant sentAt = Instant.parse(body.get("sentAt").asText());
    for (UUID contractId : List.of(withFileOne, withFileTwo, nothing)) {
      ClientInvoice invoice = invoiceOf(contractId);
      assertThat(invoice.getStatus()).isEqualTo(ClientInvoiceStatus.SENT);
      assertThat(invoice.getSentAt()).isEqualTo(sentAt);
      assertThat(invoice.getFirstSentAt()).isEqualTo(sentAt);
      assertThat(invoice.isLinesStored()).isTrue();
    }
    assertThat(lineRows(invoiceOf(withFileOne).getId())).isEqualTo(1);
    assertThat(lineRows(invoiceOf(withFileTwo).getId())).isEqualTo(1);
    assertThat(lineRows(invoiceOf(nothing).getId())).isZero();
    assertThat(invoiceBy(agent.token(), invoiceOf(nothing).getId()).get("totalAmount").decimalValue())
        .isEqualByComparingTo("0");
  }

  @Test
  void aContractNeverViewedGetsItsDraftCreatedAndSentByTheSend() throws Exception {
    NewAgent agent = newAgent();
    UUID never = contract(agent, 0);
    assertThat(hasInvoice(never)).isFalse();

    JsonNode body = json(sendTheMonth(mockMvc, agent.token()));

    assertThat(hasInvoice(never)).isTrue();
    assertThat(ids(body.get("invoiceIds"))).containsExactly(invoiceOf(never).getId());
    assertThat(invoiceOf(never).getStatus()).isEqualTo(ClientInvoiceStatus.SENT);
  }

  @Test
  void anAbsentBodyAndAnEmptyObjectBothMeanWhateverTheMonthHolds() throws Exception {
    NewAgent first = newAgent();
    contract(first, 0);
    postTheMonth(mockMvc, first.token(), currentMonth(), null).andExpect(status().isOk());

    NewAgent second = newAgent();
    contract(second, 0);
    postTheMonth(mockMvc, second.token(), currentMonth(), "{}").andExpect(status().isOk());
  }

  @Test
  void aMatchingInvoiceIdsBodyInAnyOrderIsAccepted() throws Exception {
    NewAgent agent = newAgent();
    UUID one = contract(agent, 0);
    UUID two = contract(agent, 0);
    UUID idOne = UUID.fromString(open(agent, one).get("id").asText());
    UUID idTwo = UUID.fromString(open(agent, two).get("id").asText());

    postTheMonth(mockMvc, agent.token(), currentMonth(), "{\"invoiceIds\":[\"%s\",\"%s\"]}".formatted(idTwo, idOne))
        .andExpect(status().isOk());
  }

  @Test
  void aMissingFileRefusesTheWholeMonthNamingExactlyTheNotReadyInvoicesAndWritesNothing() throws Exception {
    NewAgent agent = newAgent();
    UUID ready = contract(agent, 1);
    UUID missingOne = contract(agent, 1);
    UUID missingTwo = contract(agent, 2);
    UUID nothing = contract(agent, 0);
    attachFiles(agent, ready);
    open(agent, missingOne);
    open(agent, missingTwo);

    MvcResult refused =
        postTheMonth(mockMvc, agent.token(), currentMonth(), null).andExpect(status().isConflict()).andReturn();

    JsonNode body = json(refused);
    assertThat(body.get("code").asText()).isEqualTo("CARRIER_INVOICES_MISSING");
    assertThat(body.has("message")).isTrue();
    assertThat(ids(body.get("invoiceIds")))
        .containsExactlyInAnyOrder(invoiceOf(missingOne).getId(), invoiceOf(missingTwo).getId());
    assertThat(body.size()).isEqualTo(3);
    assertNothingSent(List.of(ready, missingOne, missingTwo, nothing));
  }

  @Test
  void invoiceIdsDifferingFromTheMonthIsMonthChangedAndNothingIsSent() throws Exception {
    NewAgent agent = newAgent();
    UUID one = contract(agent, 0);
    UUID two = contract(agent, 0);
    UUID idOne = UUID.fromString(open(agent, one).get("id").asText());
    open(agent, two);

    for (String body :
        List.of(
            "{\"invoiceIds\":[\"%s\"]}".formatted(idOne),
            "{\"invoiceIds\":[\"%s\",\"%s\"]}".formatted(idOne, UUID.randomUUID()),
            "{\"invoiceIds\":[]}")) {
      MvcResult refused =
          postTheMonth(mockMvc, agent.token(), currentMonth(), body).andExpect(status().isConflict()).andReturn();
      JsonNode answer = json(refused);
      assertThat(answer.get("code").asText()).isEqualTo("MONTH_CHANGED");
      assertThat(answer.size()).as("a coded body other than the carrier gate's is {code, message}").isEqualTo(2);
    }
    assertNothingSent(List.of(one, two));
  }

  @Test
  void aSecondSendIsMonthAlreadySentAndChangesNothing() throws Exception {
    NewAgent agent = newAgent();
    UUID contractId = contract(agent, 0);
    sendTheMonth(mockMvc, agent.token());
    Instant firstSentAt = invoiceOf(contractId).getFirstSentAt();

    postTheMonth(mockMvc, agent.token(), currentMonth(), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MONTH_ALREADY_SENT"))
        .andExpect(jsonPath("$.invoiceIds").doesNotExist());

    assertThat(invoiceOf(contractId).getFirstSentAt()).isEqualTo(firstSentAt);
  }

  @Test
  void lastMonthAndNextMonthAreNotTheCurrentMonthAndWriteNothing() throws Exception {
    NewAgent agent = newAgent();
    UUID contractId = contract(agent, 0);
    String last = YearMonth.from(BillingMonth.current()).minusMonths(1).toString();
    String next = YearMonth.from(BillingMonth.current()).plusMonths(1).toString();

    for (String month : List.of(last, next)) {
      postTheMonth(mockMvc, agent.token(), month, null)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("NOT_THE_CURRENT_MONTH"));
    }

    assertThat(hasInvoice(contractId)).as("a refused month creates no draft").isFalse();
  }

  @Test
  void aMalformedMonthIsBadRequest() throws Exception {
    NewAgent agent = newAgent();
    contract(agent, 0);

    for (String month : List.of("2026-13", "2026-1", "october", "2026-10-01")) {
      postTheMonth(mockMvc, agent.token(), month, null).andExpect(status().isBadRequest());
    }
  }

  @Test
  void anAgentHoldingNoContractIsNothingToSend() throws Exception {
    NewAgent agent = newAgent();

    postTheMonth(mockMvc, agent.token(), currentMonth(), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NOTHING_TO_SEND"));
  }

  @Test
  void aManagerAndATesterAreForbidden() throws Exception {
    newAgent();

    postTheMonth(mockMvc, managerToken, currentMonth(), null).andExpect(status().isForbidden());
    postTheMonth(mockMvc, testerToken(), currentMonth(), null).andExpect(status().isForbidden());
    postTheMonth(mockMvc, linkedTesterToken(), currentMonth(), null).andExpect(status().isForbidden());
  }

  @Test
  void anAgentLoginWithoutAnAgentIsForbidden() throws Exception {
    UUID userId = UUID.randomUUID();
    String username = "unlinked-agent-" + userId + "@example.com";
    jdbcTemplate.update(
        "INSERT INTO users (id, tenant_id, username, password_hash, role) VALUES (?, ?, ?, 'x', 'AGENT')",
        userId,
        SEEDED_TENANT_ID,
        username);
    String token = jwtService.issueToken(userId, username, SEEDED_TENANT_ID, "AGENT");

    postTheMonth(mockMvc, token, currentMonth(), null).andExpect(status().isForbidden());
  }

  @Test
  void anotherAgentsInvoicesAreNeverInTheBatchAndStayDraft() throws Exception {
    NewAgent sending = newAgent();
    NewAgent other = newAgent();
    UUID mine = contract(sending, 0);
    UUID theirs = contract(other, 0);
    open(other, theirs);

    JsonNode body = json(sendTheMonth(mockMvc, sending.token()));

    assertThat(ids(body.get("invoiceIds"))).containsExactly(invoiceOf(mine).getId());
    assertThat(invoiceOf(theirs).getStatus()).isEqualTo(ClientInvoiceStatus.DRAFT);
    assertThat(invoiceOf(theirs).getFirstSentAt()).isNull();
    postTheMonth(mockMvc, other.token(), currentMonth(), null).andExpect(status().isOk());
  }

  @Test
  void writesOneAuditLineNamingTheAgentTheMonthAndTheCountAfterTheStatusChanges() throws Exception {
    NewAgent agent = newAgent();
    contract(agent, 0);
    contract(agent, 0);
    contract(agent, 0);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    auditLogger.addAppender(appender);
    root.addAppender(appender);
    try {
      sendTheMonth(mockMvc, agent.token());

      List<String> lines = new ArrayList<>();
      // The appender sits on both the AUDIT logger and the root, so each line arrives twice.
      appender.list.stream().map(ILoggingEvent::getFormattedMessage).distinct().forEach(lines::add);
      List<String> monthLines = lines.stream().filter(l -> l.contains("action=CLIENT_INVOICE_MONTH_SENT")).toList();
      assertThat(monthLines).hasSize(1);
      assertThat(monthLines.get(0))
          .contains("entityId=" + agent.id())
          .contains("billingMonth=" + BillingMonth.current())
          .contains("count=3")
          .contains("tenantId=" + SEEDED_TENANT_ID);
      List<String> statusChanges =
          lines.stream().filter(l -> l.contains("action=STATUS_CHANGE") && l.contains("newStatus=SENT")).toList();
      assertThat(statusChanges).hasSize(3);
      assertThat(lines.indexOf(monthLines.get(0))).isGreaterThan(lines.lastIndexOf(statusChanges.get(2)));
    } finally {
      auditLogger.detachAppender(appender);
      root.detachAppender(appender);
    }
  }

  @Test
  void monthSentAtAndSendableOnItsOwnFollowTheInvoiceThroughEveryState() throws Exception {
    NewAgent agent = newAgent();
    UUID sentBackContract = contract(agent, 1);
    UUID other = contract(agent, 0);
    UUID sentBackInvoice = UUID.fromString(open(agent, sentBackContract).get("id").asText());
    attachFiles(agent, sentBackContract);

    // A draft before the month is sent: not sendable on its own, no month date, for both readers.
    for (String token : List.of(agent.token(), managerToken)) {
      JsonNode draft = invoiceBy(token, sentBackInvoice);
      assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
      assertThat(draft.get("monthSentAt").isNull()).isTrue();
      assertThat(draft.get("sendableOnItsOwn").asBoolean()).isFalse();
    }

    JsonNode month = json(sendTheMonth(mockMvc, agent.token()));
    String monthSentAt = month.get("sentAt").asText();

    // Sent: the month date is served; nothing to send on its own.
    for (String token : List.of(agent.token(), managerToken)) {
      JsonNode sent = invoiceBy(token, sentBackInvoice);
      assertThat(sent.get("status").asText()).isEqualTo("SENT");
      assertThat(Instant.parse(sent.get("monthSentAt").asText())).isEqualTo(Instant.parse(monthSentAt));
      assertThat(sent.get("sendableOnItsOwn").asBoolean()).isFalse();
    }

    // A Tester sees the sent invoice with neither field.
    String tester =
        createTesterAndLogin(
            managerToken,
            contractRepository.findById(sentBackContract).orElseThrow().getClient().getId(),
            "month-tester-" + UUID.randomUUID() + "@example.com");
    JsonNode testerView =
        json(
            mockMvc
                .perform(
                    get("/api/contracts/" + sentBackContract + "/client-invoice")
                        .header("Authorization", "Bearer " + tester))
                .andExpect(status().isOk())
                .andReturn());
    assertThat(testerView.get("status").asText()).isEqualTo("SENT");
    assertThat(testerView.get("monthSentAt").isNull()).isTrue();
    assertThat(testerView.get("sendableOnItsOwn").asBoolean()).isFalse();

    // Sent back: sendable on its own, the month date unchanged; resent alone through the
    // per-invoice route, the date is still the month's.
    sendBack(sentBackInvoice).andExpect(status().isOk());
    JsonNode back = invoiceBy(agent.token(), sentBackInvoice);
    assertThat(back.get("status").asText()).isEqualTo("DRAFT");
    assertThat(Instant.parse(back.get("monthSentAt").asText())).isEqualTo(Instant.parse(monthSentAt));
    assertThat(back.get("sendableOnItsOwn").asBoolean()).isTrue();
    mockMvc
        .perform(post("/api/client-invoices/" + sentBackInvoice + "/send").header("Authorization", "Bearer " + agent.token()))
        .andExpect(status().isOk());
    JsonNode resent = invoiceBy(agent.token(), sentBackInvoice);
    assertThat(resent.get("status").asText()).isEqualTo("SENT");
    assertThat(Instant.parse(resent.get("monthSentAt").asText())).isEqualTo(Instant.parse(monthSentAt));
    assertThat(invoiceOf(other).getStatus()).as("the rest of the month stays where it was").isEqualTo(ClientInvoiceStatus.SENT);
  }

  @Test
  void aContractAddedAfterTheMonthWasSentIsSendableOnItsOwnAndSendsAlone() throws Exception {
    NewAgent agent = newAgent();
    contract(agent, 0);
    sendTheMonth(mockMvc, agent.token());
    UUID late = contract(agent, 0);

    JsonNode draft = open(agent, late);

    assertThat(draft.get("monthSentAt").isNull()).isFalse();
    assertThat(draft.get("sendableOnItsOwn").asBoolean()).isTrue();
    mockMvc
        .perform(post("/api/contracts/" + late + "/client-invoice/send").header("Authorization", "Bearer " + agent.token()))
        .andExpect(status().isOk());
    postTheMonth(mockMvc, agent.token(), currentMonth(), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MONTH_ALREADY_SENT"));
  }

  @Test
  void aMonthHoldingAnInvoiceSentBeforeThisFeatureReadsSentAndItsDraftsAreSendableOnTheirOwn() throws Exception {
    NewAgent agent = newAgent();
    UUID earlier = contract(agent, 0);
    UUID remaining = contract(agent, 0);
    ClientInvoice sentOnItsOwn = invoiceOfOpened(agent, earlier);
    Instant firstSentAt = Instant.now().minusSeconds(3600);
    sentOnItsOwn.setStatus(ClientInvoiceStatus.SENT);
    sentOnItsOwn.setSentAt(firstSentAt);
    sentOnItsOwn.setFirstSentAt(firstSentAt);
    sentOnItsOwn.setLinesStored(true);
    clientInvoiceRepository.saveAndFlush(sentOnItsOwn);

    JsonNode draft = open(agent, remaining);

    assertThat(Instant.parse(draft.get("monthSentAt").asText())).isEqualTo(firstSentAt);
    assertThat(draft.get("sendableOnItsOwn").asBoolean()).isTrue();
    postTheMonth(mockMvc, agent.token(), currentMonth(), null)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MONTH_ALREADY_SENT"));
  }

  private ClientInvoice invoiceOfOpened(NewAgent agent, UUID contractId) throws Exception {
    open(agent, contractId);
    return invoiceOf(contractId);
  }

  @Test
  void aMonthNobodyHasSentCarriesNoMonthDateOnAnyInvoice() throws Exception {
    NewAgent agent = newAgent();
    UUID contractId = contract(agent, 0);

    JsonNode draft = open(agent, contractId);

    assertThat(draft.get("monthSentAt").isNull()).isTrue();
    assertThat(draft.get("sendableOnItsOwn").asBoolean()).isFalse();
  }
}
