package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.domain.AgentInvoice;
import com.remotesupport.backend.repository.AgentInvoiceRepository;
import com.remotesupport.backend.repository.AgentStandingAmountRepository;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * An Agent Invoice addressed by its own id (manager-invoice-review-queue spec, "invoice by id";
 * agent-invoice-review-page ticket): the Manager's read, override, approve and mark paid for an
 * invoice of any billing month. These routes never get-or-create, and resolve only inside the
 * caller's tenant. Driven through the real HTTP seam, like {@link AgentInvoiceApiTest}.
 */
@Import(OtherTenantFixture.class)
class AgentInvoiceByIdApiTest extends IntegrationTest {

  @Autowired private AgentInvoiceRepository agentInvoiceRepository;
  @Autowired private AgentStandingAmountRepository agentStandingAmountRepository;
  @Autowired private OtherTenantFixture otherTenantFixture;

  private static LocalDate currentMonth() {
    return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
  }

  private UUID idOf(MvcResult result) throws Exception {
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  /** The seeded Agent's current-month invoice, opened (so still a draft). */
  private UUID openDraft(String agentToken) throws Exception {
    return idOf(
        mockMvc
            .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn());
  }

  /** The seeded Agent sends their current-month invoice. */
  private UUID send(String agentToken) throws Exception {
    return idOf(
        mockMvc
            .perform(
                post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/send").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn());
  }

  /** Moves an invoice into an earlier billing month — stands in for a month rollover in a test. */
  private void moveToPastMonth(UUID invoiceId, int monthsBack) {
    AgentInvoice invoice = agentInvoiceRepository.findById(invoiceId).orElseThrow();
    invoice.setBillingMonth(invoice.getBillingMonth().minusMonths(monthsBack));
    agentInvoiceRepository.saveAndFlush(invoice);
  }

  private MockHttpServletRequestBuilder override(UUID invoiceId, String body) {
    return post("/api/agent-invoices/" + invoiceId + "/override").contentType(APPLICATION_JSON).content(body);
  }

  private void perform(MockHttpServletRequestBuilder request, String token, int expectedStatus) throws Exception {
    mockMvc.perform(request.header("Authorization", "Bearer " + token)).andExpect(status().is(expectedStatus));
  }

  // --- Read ---------------------------------------------------------------------------------

  @Test
  void managerReadsASentAgentInvoiceByIdFromItsSnapshot() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = send(agentToken());

    mockMvc
        .perform(get("/api/agent-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(invoiceId.toString()))
        .andExpect(jsonPath("$.agentId").value(SEEDED_AGENT_ID.toString()))
        .andExpect(jsonPath("$.billingMonth").value(currentMonth().toString()))
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.localSupportFees").value(0))
        .andExpect(jsonPath("$.salary").value(2500.00))
        .andExpect(jsonPath("$.rolloutAdvanceRepayment").value(0))
        .andExpect(jsonPath("$.rolloutAdvanceNewAdvance").value(0))
        .andExpect(jsonPath("$.totalAmount").value(2500.00))
        .andExpect(jsonPath("$.sentAt").isNotEmpty())
        .andExpect(jsonPath("$.approvedAt").isEmpty())
        .andExpect(jsonPath("$.paidAt").isEmpty());
  }

  @Test
  void managerReadsAPastMonthsAgentInvoiceById() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = send(agentToken());
    moveToPastMonth(invoiceId, 1);

    mockMvc
        .perform(get("/api/agent-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billingMonth").value(currentMonth().minusMonths(1).toString()))
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.salary").value(2500.00));
  }

  @Test
  void managerReadsADraftAgentInvoiceById() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = openDraft(agentToken());

    mockMvc
        .perform(get("/api/agent-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.totalAmount").value(2500.00));
  }

  // --- Override -----------------------------------------------------------------------------

  @Test
  void managerOverridesASentInvoiceByIdChangingOnlyThatInvoicesSnapshot() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    // Two sent invoices for the same Agent: last month's and this month's.
    UUID lastMonth = send(agentToken);
    moveToPastMonth(lastMonth, 1);
    UUID thisMonth = send(agentToken);
    long standingAmountRowsBefore = agentStandingAmountRepository.count();

    mockMvc
        .perform(
            override(lastMonth, """
                {"salary":3200.00,"rolloutAdvanceNewAdvance":150.00}
                """)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(lastMonth.toString()))
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.salary").value(3200.00))
        .andExpect(jsonPath("$.rolloutAdvanceNewAdvance").value(150.00))
        .andExpect(jsonPath("$.totalAmount").value(3350.00));

    // Persisted on that invoice.
    mockMvc
        .perform(get("/api/agent-invoices/" + lastMonth).header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.salary").value(3200.00))
        .andExpect(jsonPath("$.totalAmount").value(3350.00));
    // The other invoice is untouched.
    mockMvc
        .perform(get("/api/agent-invoices/" + thisMonth).header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.salary").value(2500.00))
        .andExpect(jsonPath("$.rolloutAdvanceNewAdvance").value(0))
        .andExpect(jsonPath("$.totalAmount").value(2500.00));
    // The standing amounts are untouched (ADR 0003).
    assertThat(agentStandingAmountRepository.count()).isEqualTo(standingAmountRowsBefore);
    mockMvc
        .perform(
            get("/api/agents/" + SEEDED_AGENT_ID + "/standing-amounts").header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.salaryAmount").value(2500.00))
        .andExpect(jsonPath("$.rolloutAdvanceAmount").value(0));
  }

  /**
   * A partial override touches only the line it names — ported here from the removed current-month
   * override route (remove-current-month-manager-invoice-actions ticket).
   */
  @Test
  void overridingOnlySalaryByIdLeavesTheRolloutAdvanceLinesUntouched() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = send(agentToken());

    mockMvc
        .perform(
            override(invoiceId, """
                {"salary":2600.00}
                """)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salary").value(2600.00))
        .andExpect(jsonPath("$.rolloutAdvanceRepayment").value(0))
        .andExpect(jsonPath("$.rolloutAdvanceNewAdvance").value(0));
  }

  @Test
  void overridingByIdWithNeitherFieldIsRejected() throws Exception {
    UUID invoiceId = send(agentToken());

    perform(override(invoiceId, "{}"), managerToken(), 400);
  }

  @Test
  void overridingADraftAgentInvoiceByIdIsAConflict() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = openDraft(agentToken());

    perform(override(invoiceId, """
        {"salary":3000.00}
        """), managerToken, 409);
    mockMvc
        .perform(get("/api/agent-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.salary").value(2500.00));
  }

  @Test
  void overridingAnApprovedAgentInvoiceByIdIsAConflictAndChangesNothing() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = send(agentToken());
    perform(post("/api/agent-invoices/" + invoiceId + "/approve"), managerToken, 200);

    perform(override(invoiceId, """
        {"salary":3000.00}
        """), managerToken, 409);
    mockMvc
        .perform(get("/api/agent-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.salary").value(2500.00));
  }

  // --- Approve and mark paid ----------------------------------------------------------------

  @Test
  void managerApprovesThenMarksPaidAPastMonthsAgentInvoiceById() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = send(agentToken());
    moveToPastMonth(invoiceId, 2);

    mockMvc
        .perform(post("/api/agent-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(invoiceId.toString()))
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.approvedAt").isNotEmpty())
        .andExpect(jsonPath("$.billingMonth").value(currentMonth().minusMonths(2).toString()));

    mockMvc
        .perform(post("/api/agent-invoices/" + invoiceId + "/paid").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.paidAt").isNotEmpty());

    mockMvc
        .perform(get("/api/agent-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.status").value("PAID"));
  }

  @Test
  void managerApprovesThenMarksPaidTheCurrentMonthsAgentInvoiceById() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = send(agentToken());

    perform(post("/api/agent-invoices/" + invoiceId + "/approve"), managerToken, 200);
    mockMvc
        .perform(post("/api/agent-invoices/" + invoiceId + "/paid").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PAID"));
  }

  @Test
  void approvingADraftAgentInvoiceByIdIsAConflict() throws Exception {
    UUID invoiceId = openDraft(agentToken());

    perform(post("/api/agent-invoices/" + invoiceId + "/approve"), managerToken(), 409);
  }

  @Test
  void markingPaidBeforeApprovalByIdIsAConflictAndChangesNothing() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = send(agentToken());

    perform(post("/api/agent-invoices/" + invoiceId + "/paid"), managerToken, 409);
    mockMvc
        .perform(get("/api/agent-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.status").value("SENT"));
  }

  @Test
  void aPaidAgentInvoiceAcceptsNoFurtherActionById() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = send(agentToken());
    perform(post("/api/agent-invoices/" + invoiceId + "/approve"), managerToken, 200);
    perform(post("/api/agent-invoices/" + invoiceId + "/paid"), managerToken, 200);

    perform(post("/api/agent-invoices/" + invoiceId + "/paid"), managerToken, 409);
    perform(post("/api/agent-invoices/" + invoiceId + "/approve"), managerToken, 409);
    perform(override(invoiceId, """
        {"salary":1.00}
        """), managerToken, 409);
  }

  @Test
  void overrideApproveAndPaidByIdLogTheSameAuditEventsAsTheCurrentMonthRoutes() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = send(agentToken());

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);
    try {
      perform(override(invoiceId, """
          {"salary":2750.00,"rolloutAdvanceNewAdvance":100.00}
          """), managerToken, 200);
      perform(post("/api/agent-invoices/" + invoiceId + "/approve"), managerToken, 200);
      perform(post("/api/agent-invoices/" + invoiceId + "/paid"), managerToken, 200);

      String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertThat(logged).contains("action=AGENT_INVOICE_OVERRIDDEN entity=AgentInvoice entityId=" + invoiceId);
      assertThat(logged).contains("field=salary oldValue=2500.00 newValue=2750.00");
      assertThat(logged).contains("field=rolloutAdvanceNewAdvance oldValue=0 newValue=100.00");
      assertThat(logged).contains("action=STATUS_CHANGE entity=AgentInvoice entityId=" + invoiceId);
      assertThat(logged).contains("oldStatus=SENT newStatus=APPROVED");
      assertThat(logged).contains("oldStatus=APPROVED newStatus=PAID");
      assertThat(logged).contains("actorUserId=22222222-2222-2222-2222-222222222222");
      assertThat(logged).contains("tenantId=11111111-1111-1111-1111-111111111111");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  // --- Access, tenancy, never creating ------------------------------------------------------

  private List<MockHttpServletRequestBuilder> everyByIdRoute(UUID invoiceId) {
    return List.of(
        get("/api/agent-invoices/" + invoiceId),
        override(invoiceId, """
            {"salary":3000.00}
            """),
        post("/api/agent-invoices/" + invoiceId + "/approve"),
        post("/api/agent-invoices/" + invoiceId + "/paid"));
  }

  @Test
  void agentsAndTestersAreForbiddenFromEveryByIdRouteEvenForTheirOwnInvoice() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID invoiceId = send(agentToken);

    for (String token : List.of(agentToken, testerToken())) {
      for (MockHttpServletRequestBuilder route : everyByIdRoute(invoiceId)) {
        perform(route, token, 403);
      }
    }
    mockMvc
        .perform(get("/api/agent-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.salary").value(2500.00));
  }

  @Test
  void anUnknownInvoiceIdIsNotFoundOnEveryByIdRouteAndCreatesNothing() throws Exception {
    String managerToken = managerToken();
    long invoicesBefore = agentInvoiceRepository.count();

    for (MockHttpServletRequestBuilder route : everyByIdRoute(UUID.randomUUID())) {
      perform(route, managerToken, 404);
    }

    assertThat(agentInvoiceRepository.count()).isEqualTo(invoicesBefore);
  }

  @Test
  void anotherTenantsInvoiceIdIsNotFoundOnEveryByIdRoute() throws Exception {
    String managerToken = managerToken();
    UUID otherTenantInvoice = otherTenantFixture.sentAgentInvoiceInAnotherTenant();

    for (MockHttpServletRequestBuilder route : everyByIdRoute(otherTenantInvoice)) {
      perform(route, managerToken, 404);
    }
    AgentInvoice untouched = agentInvoiceRepository.findById(otherTenantInvoice).orElseThrow();
    assertThat(untouched.getStatus().name()).isEqualTo("SENT");
    assertThat(untouched.getSnapshotSalary()).isEqualByComparingTo("1000.00");
  }
}
