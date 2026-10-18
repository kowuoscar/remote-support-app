package com.remotesupport.backend.web;

import static com.remotesupport.backend.support.ClientInvoiceMonthTestSupport.approveTheMonth;
import static com.remotesupport.backend.support.ClientInvoiceMonthTestSupport.currentMonth;
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
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A Manager approves the invoices left to review in an Agent's month in one call
 * (manager-reviews-by-agent spec, "Month approval"), through the real HTTP seam with real
 * Postgres. Every case builds a fresh Agent whose Contracts have no Postpaid SIM, so the month
 * send needs no Carrier Invoice File.
 */
@Import(OtherTenantFixture.class)
class ClientInvoiceMonthApproveApiTest extends IntegrationTest {

  @Autowired private ClientInvoiceRepository clientInvoiceRepository;
  @Autowired private OtherTenantFixture otherTenantFixture;

  private String managerToken;

  private record NewAgent(UUID id, String token) {}

  private NewAgent newAgent() throws Exception {
    managerToken = managerToken();
    String email = "approve-agent-" + UUID.randomUUID() + "@agents.example";
    MvcResult created =
        mockMvc
            .perform(
                post("/api/agents")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Approve Agent","country":"UNITED_STATES","salaryAmount":2000.00,"username":"%s"}
                        """
                            .formatted(email)))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
    return new NewAgent(UUID.fromString(body.get("id").asText()), loginAs(email, body.get("password").asText()));
  }

  private void contract(NewAgent agent) throws Exception {
    UUID clientId = createClient(managerToken, "Approve Client " + UUID.randomUUID());
    createContract(managerToken, clientId, agent.id());
  }

  /** An Agent with {@code contracts} Contracts, the month sent: the ids of the sent invoices. */
  private List<UUID> sentMonth(NewAgent agent, int contracts) throws Exception {
    for (int i = 0; i < contracts; i++) {
      contract(agent);
    }
    JsonNode sent = json(sendTheMonth(mockMvc, agent.token()));
    List<UUID> ids = new ArrayList<>();
    sent.get("invoiceIds").forEach(id -> ids.add(UUID.fromString(id.asText())));
    return ids;
  }

  private JsonNode json(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private static String body(List<UUID> ids) {
    return "{\"invoiceIds\":[" + String.join(",", ids.stream().map(id -> "\"" + id + "\"").toList()) + "]}";
  }

  private ResultActions approve(NewAgent agent, List<UUID> ids) throws Exception {
    return approveTheMonth(mockMvc, managerToken, agent.id(), currentMonth(), body(ids));
  }

  private ResultActions approveOne(UUID invoiceId) throws Exception {
    return mockMvc.perform(
        post("/api/client-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken));
  }

  private ClientInvoice reload(UUID invoiceId) {
    return clientInvoiceRepository.findById(invoiceId).orElseThrow();
  }

  private ResultActions sendBack(UUID invoiceId) throws Exception {
    return mockMvc.perform(
        post("/api/client-invoices/" + invoiceId + "/send-back")
            .header("Authorization", "Bearer " + managerToken)
            .contentType(APPLICATION_JSON)
            .content("{\"reason\":\"Please check the second line\"}"));
  }

  private void assertStillSent(List<UUID> ids) {
    for (UUID id : ids) {
      ClientInvoice invoice = reload(id);
      assertThat(invoice.getStatus()).isEqualTo(ClientInvoiceStatus.SENT);
      assertThat(invoice.getApprovedAt()).isNull();
    }
  }

  @Test
  void approvesEveryInvoiceTheCallerNamesAtOneInstantAndTheAgentReadsApproved() throws Exception {
    NewAgent agent = newAgent();
    List<UUID> ids = sentMonth(agent, 3);

    MvcResult result = approve(agent, ids).andExpect(status().isOk()).andReturn();

    JsonNode body = json(result);
    assertThat(body.get("billingMonth").asText()).isEqualTo(BillingMonth.current().toString());
    Set<UUID> answered = new HashSet<>();
    body.get("invoiceIds").forEach(id -> answered.add(UUID.fromString(id.asText())));
    assertThat(answered).isEqualTo(new HashSet<>(ids));
    Instant approvedAt = Instant.parse(body.get("approvedAt").asText());
    for (UUID id : ids) {
      ClientInvoice invoice = reload(id);
      assertThat(invoice.getStatus()).isEqualTo(ClientInvoiceStatus.APPROVED);
      assertThat(invoice.getApprovedAt()).isEqualTo(approvedAt);
      mockMvc
          .perform(get("/api/client-invoices/" + id).header("Authorization", "Bearer " + agent.token()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("APPROVED"));
    }
  }

  @Test
  void anApprovedOrSentBackInvoiceOfTheMonthIsLeftAsItIs() throws Exception {
    NewAgent agent = newAgent();
    List<UUID> ids = sentMonth(agent, 3);
    UUID alreadyApproved = ids.get(0);
    UUID sentBack = ids.get(1);
    UUID left = ids.get(2);
    approveOne(alreadyApproved).andExpect(status().isOk());
    sendBack(sentBack).andExpect(status().isOk());
    Instant firstApprovedAt = reload(alreadyApproved).getApprovedAt();

    approve(agent, List.of(left)).andExpect(status().isOk());

    assertThat(reload(left).getStatus()).isEqualTo(ClientInvoiceStatus.APPROVED);
    assertThat(reload(alreadyApproved).getApprovedAt()).isEqualTo(firstApprovedAt);
    assertThat(reload(sentBack).getStatus()).isEqualTo(ClientInvoiceStatus.DRAFT);
    assertThat(reload(sentBack).getApprovedAt()).isNull();
  }

  @Test
  void aPastMonthApproves() throws Exception {
    NewAgent agent = newAgent();
    List<UUID> ids = sentMonth(agent, 2);
    for (UUID id : ids) {
      ClientInvoice invoice = reload(id);
      invoice.setBillingMonth(BillingMonth.current().minusMonths(1));
      clientInvoiceRepository.saveAndFlush(invoice);
    }
    String past = YearMonth.from(BillingMonth.current().minusMonths(1)).toString();

    approveTheMonth(mockMvc, managerToken, agent.id(), past, body(ids))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billingMonth").value(BillingMonth.current().minusMonths(1).toString()));
  }

  @Test
  void idsShortByOneOrWithAnExtraAreMonthChangedAndNothingIsApproved() throws Exception {
    NewAgent agent = newAgent();
    List<UUID> ids = sentMonth(agent, 3);

    approve(agent, ids.subList(0, 2))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MONTH_CHANGED"));
    List<UUID> withExtra = new ArrayList<>(ids);
    withExtra.add(UUID.randomUUID());
    approve(agent, withExtra)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MONTH_CHANGED"));

    assertStillSent(ids);
  }

  @Test
  void noInvoiceSentIsNothingToApproveWithACodeAndMessage() throws Exception {
    NewAgent agent = newAgent();
    List<UUID> ids = sentMonth(agent, 2);
    for (UUID id : ids) {
      approveOne(id).andExpect(status().isOk());
    }

    approve(agent, ids)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NOTHING_TO_APPROVE"))
        .andExpect(jsonPath("$.message").isNotEmpty());
    approve(agent, List.of(UUID.randomUUID()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("NOTHING_TO_APPROVE"));
  }

  @Test
  void aMissingBodyAnEmptyListAndAMalformedMonthAreBadRequests() throws Exception {
    NewAgent agent = newAgent();
    List<UUID> ids = sentMonth(agent, 1);

    approveTheMonth(mockMvc, managerToken, agent.id(), currentMonth(), null).andExpect(status().isBadRequest());
    approveTheMonth(mockMvc, managerToken, agent.id(), currentMonth(), "{}").andExpect(status().isBadRequest());
    approveTheMonth(mockMvc, managerToken, agent.id(), currentMonth(), "{\"invoiceIds\":[]}")
        .andExpect(status().isBadRequest());
    approveTheMonth(mockMvc, managerToken, agent.id(), "2026-13", body(ids)).andExpect(status().isBadRequest());

    assertStillSent(ids);
  }

  @Test
  void anAgentAndATesterAreForbidden() throws Exception {
    NewAgent agent = newAgent();
    List<UUID> ids = sentMonth(agent, 1);

    approveTheMonth(mockMvc, agent.token(), agent.id(), currentMonth(), body(ids)).andExpect(status().isForbidden());
    approveTheMonth(mockMvc, testerToken(), agent.id(), currentMonth(), body(ids)).andExpect(status().isForbidden());

    assertStillSent(ids);
  }

  @Test
  void anotherTenantsAgentAndAnUnknownAgentAreNotFound() throws Exception {
    NewAgent agent = newAgent();
    List<UUID> ids = sentMonth(agent, 1);
    OtherTenantFixture.OtherTenantAgentLogin other =
        otherTenantFixture.agentLoginInAnotherTenant("approve-other-" + UUID.randomUUID(), "OtherAgent123!");

    approveTheMonth(mockMvc, managerToken, other.agentId(), currentMonth(), body(ids))
        .andExpect(status().isNotFound());
    approveTheMonth(mockMvc, managerToken, UUID.randomUUID(), currentMonth(), body(ids))
        .andExpect(status().isNotFound());

    assertStillSent(ids);
  }

  @Test
  void afterApprovalASendBackOfOneIsAConflict() throws Exception {
    NewAgent agent = newAgent();
    List<UUID> ids = sentMonth(agent, 2);
    approve(agent, ids).andExpect(status().isOk());

    sendBack(ids.get(0)).andExpect(status().isConflict());

    assertThat(reload(ids.get(0)).getStatus()).isEqualTo(ClientInvoiceStatus.APPROVED);
  }

  @Test
  void logsOneStatusLinePerInvoiceThenOneLineNamingTheAgentTheMonthAndTheCount() throws Exception {
    NewAgent agent = newAgent();
    List<UUID> ids = sentMonth(agent, 3);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    auditLogger.addAppender(appender);
    root.addAppender(appender);
    try {
      approve(agent, ids).andExpect(status().isOk());

      // The appender sits on both the AUDIT logger and the root, so each line arrives twice.
      List<String> lines = new ArrayList<>();
      appender.list.stream().map(ILoggingEvent::getFormattedMessage).distinct().forEach(lines::add);
      List<String> monthLines =
          lines.stream().filter(l -> l.contains("action=CLIENT_INVOICE_MONTH_APPROVED")).toList();
      assertThat(monthLines).hasSize(1);
      assertThat(monthLines.get(0))
          .contains("entityId=" + agent.id())
          .contains("billingMonth=" + BillingMonth.current())
          .contains("count=3")
          .contains("tenantId=" + SEEDED_TENANT_ID);
      List<String> statusChanges =
          lines.stream()
              .filter(l -> l.contains("action=STATUS_CHANGE") && l.contains("newStatus=APPROVED"))
              .toList();
      assertThat(statusChanges).hasSize(3);
      assertThat(lines.indexOf(monthLines.get(0))).isGreaterThan(lines.lastIndexOf(statusChanges.get(2)));
    } finally {
      auditLogger.detachAppender(appender);
      root.detachAppender(appender);
    }
  }
}
