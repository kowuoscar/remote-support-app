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
import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.AgentStandingAmount;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.domain.StandingAmountType;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.AgentStandingAmountRepository;
import com.remotesupport.backend.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Standing-amount versioning and the Agent Invoice's four line items (spec.md Solution's Agent
 * Invoice entity; agent-standing-amounts-and-invoice-generation ticket, user stories 5-6, 25-26).
 * Mirrors {@link ClientInvoiceApiTest}'s pattern: driven through the real HTTP seam, with direct
 * repository manipulation (here, of {@link AgentStandingAmount} rows) standing in for "time has
 * passed" the same way that test backdates a Fee's {@code billingMonth} — there is no real
 * month-crossing to wait for in a test run.
 */
class AgentInvoiceApiTest extends IntegrationTest {

  @Autowired private AgentStandingAmountRepository agentStandingAmountRepository;
  @Autowired private AgentRepository agentRepository;

  private static LocalDate currentMonth() {
    return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
  }

  private UUID addSimCard(
      String managerToken, UUID contractId, String number, String flavor, String monthlyFeeAmount) throws Exception {
    String body =
        monthlyFeeAmount == null
            ? """
                {"number":"%s","carrierId":"%s","flavor":"%s"}
                """.formatted(number, carrierFor(managerToken, contractId), flavor)
            : """
                {"number":"%s","carrierId":"%s","flavor":"%s","monthlyFeeAmount":%s}
                """.formatted(number, carrierFor(managerToken, contractId), flavor, monthlyFeeAmount);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/sim-cards")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID submitRequest(String testerToken, UUID contractId, String type) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/requests")
                    .header("Authorization", "Bearer " + testerToken)
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"type":"%s"}
                        """.formatted(type)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID logFee(String agentToken, UUID contractId, UUID requestId, String feeType, String amount)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/fees")
                    .header("Authorization", "Bearer " + agentToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"requestId":"%s","feeType":"%s","amount":%s}
                        """
                            .formatted(requestId, feeType, amount)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  /**
   * Directly inserts a standing-amount history row, bypassing the next-month-effective rule the
   * HTTP endpoint enforces — the same "manipulate the row directly to simulate time passing"
   * shape {@link ClientInvoiceApiTest} uses on {@code Fee.billingMonth}.
   */
  private void insertStandingAmountRow(
      UUID agentId, StandingAmountType type, String amount, LocalDate effectiveMonth) {
    Agent agent = agentRepository.findById(agentId).orElseThrow();
    AgentStandingAmount row = new AgentStandingAmount();
    row.setId(UUID.randomUUID());
    row.setTenant(agent.getTenant());
    row.setAgent(agent);
    row.setAmountType(type);
    row.setAmount(new java.math.BigDecimal(amount));
    row.setEffectiveMonth(effectiveMonth);
    row.setSetByUserId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    row.setSetAt(Instant.now());
    agentStandingAmountRepository.saveAndFlush(row);
  }

  // --- AC: get-or-create on first access, Salary auto-populates -----------------------------

  @Test
  void openingAnAgentsInvoiceForTheFirstTimeThisMonthCreatesItInDraftWithStandingSalary() throws Exception {
    String agentToken = agentToken();

    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.agentId").value(SEEDED_AGENT_ID.toString()))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.billingMonth").value(currentMonth().toString()))
        .andExpect(jsonPath("$.salary").value(2500.00))
        .andExpect(jsonPath("$.localSupportFees").value(0))
        .andExpect(jsonPath("$.rolloutAdvanceRepayment").value(0))
        .andExpect(jsonPath("$.rolloutAdvanceNewAdvance").value(0))
        .andExpect(jsonPath("$.totalAmount").value(2500.00));
  }

  @Test
  void reopeningTheSameAgentsInvoiceTheSameMonthReturnsTheSameDraft() throws Exception {
    String agentToken = agentToken();

    MvcResult first =
        mockMvc
            .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    UUID firstId = UUID.fromString(objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText());

    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(firstId.toString()));
  }

  // --- AC: Local Support Fees sums across every Contract, any Client Invoice status ---------

  @Test
  void localSupportFeesSumsAcrossContractsRegardlessOfClientInvoiceStatus() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();

    // Contract 1: Client Invoice left in draft.
    UUID client1 = createClient(managerToken, "Aurora Retail Group");
    UUID contract1 = createContract(managerToken, client1, SEEDED_AGENT_ID);
    addSimCard(managerToken, contract1, "+1-555-0100", "POSTPAID", "25.00");
    String tester1 = createTesterAndLogin(managerToken, client1, "priya.raman@aurora.example", "Passw0rd!23");
    UUID topup1 = submitRequest(tester1, contract1, "TOPUP");
    logFee(agentToken, contract1, topup1, "TOPUP", "10.00");
    mockMvc.perform(get("/api/contracts/" + contract1 + "/client-invoice").header("Authorization", "Bearer " + agentToken));
    // Contract 1's Client Invoice stays DRAFT — total so far: 25 + 10 = 35.

    // Contract 2: Client Invoice sent (but not approved).
    UUID client2 = createClient(managerToken, "Meridian Logistics");
    UUID contract2 = createContract(managerToken, client2, SEEDED_AGENT_ID);
    addSimCard(managerToken, contract2, "+1-555-0200", "POSTPAID", "40.00");
    String tester2 = createTesterAndLogin(managerToken, client2, "charlotte.finch@meridian.example", "Passw0rd!23");
    UUID repair2 = submitRequest(tester2, contract2, "REPAIR");
    logFee(agentToken, contract2, repair2, "REPAIR", "60.00");
    mockMvc.perform(get("/api/contracts/" + contract2 + "/client-invoice").header("Authorization", "Bearer " + agentToken));
    mockMvc
        .perform(post("/api/contracts/" + contract2 + "/client-invoice/send").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());
    // Contract 2's Client Invoice is SENT — total: 40 + 60 = 100.

    // Contract 3: Client Invoice sent AND approved.
    UUID client3 = createClient(managerToken, "Solene Cosmetics");
    UUID contract3 = createContract(managerToken, client3, SEEDED_AGENT_ID);
    addSimCard(managerToken, contract3, "+1-555-0300", "POSTPAID", "15.00");
    mockMvc.perform(get("/api/contracts/" + contract3 + "/client-invoice").header("Authorization", "Bearer " + agentToken));
    MvcResult sent3 =
        mockMvc
            .perform(
                post("/api/contracts/" + contract3 + "/client-invoice/send")
                    .header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    UUID invoice3 = UUID.fromString(objectMapper.readTree(sent3.getResponse().getContentAsString()).get("id").asText());
    mockMvc
        .perform(post("/api/client-invoices/" + invoice3 + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());
    // Contract 3's Client Invoice is APPROVED — total: 15 + 0 = 15.

    // A Fee logged against Contract 2 *after* it was already sent must still count here (it's
    // real money the Agent fronted), even though it never made it into that Client Invoice's
    // frozen snapshot.
    UUID topup2 = submitRequest(tester2, contract2, "TOPUP");
    logFee(agentToken, contract2, topup2, "TOPUP", "5.00");
    // Contract 2's live total is now 40 + 60 + 5 = 105.

    // Expected Local Support Fees = 35 (draft) + 105 (sent, plus the post-send Fee) + 15 (approved) = 155.
    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.localSupportFees").value(155.00));
  }

  // --- AC: Rollout Advance lines --------------------------------------------------------------

  @Test
  void rolloutAdvanceLinesAreZeroWhenNoAdvanceHasEverBeenSet() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Bright Path Clinics");
    UUID agentId = createAgent(managerToken, "Priya Nair", Country.PHILIPPINES);
    createContract(managerToken, clientId, agentId);

    mockMvc
        .perform(get("/api/agents/" + agentId + "/invoice").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rolloutAdvanceRepayment").value(0))
        .andExpect(jsonPath("$.rolloutAdvanceNewAdvance").value(0));
  }

  @Test
  void rolloutAdvanceNetsToZeroInASteadyMonth() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
    UUID agentId = createAgent(managerToken, "Owen Whitfield", Country.UNITED_KINGDOM);
    createContract(managerToken, clientId, agentId);

    // No standing-amount change since well before last month — the same rate resolves for both
    // last month (repayment) and this month (new advance), so they net to zero.
    insertStandingAmountRow(agentId, StandingAmountType.ROLLOUT_ADVANCE, "300.00", currentMonth().minusMonths(3));

    mockMvc
        .perform(get("/api/agents/" + agentId + "/invoice").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rolloutAdvanceRepayment").value(-300.00))
        .andExpect(jsonPath("$.rolloutAdvanceNewAdvance").value(300.00));
  }

  @Test
  void rolloutAdvanceShowsTheOneMonthDeltaRightAfterAChangeTookEffect() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Kessler & Vance LLP");
    UUID agentId = createAgent(managerToken, "Camille Duforet", Country.FRANCE);
    createContract(managerToken, clientId, agentId);

    // Old rate (200) was in effect through last month; a change to 350 took effect starting this
    // month — the exact "cycle right after a standing-amount change" spec.md describes.
    insertStandingAmountRow(agentId, StandingAmountType.ROLLOUT_ADVANCE, "200.00", currentMonth().minusMonths(1));
    insertStandingAmountRow(agentId, StandingAmountType.ROLLOUT_ADVANCE, "350.00", currentMonth());

    mockMvc
        .perform(get("/api/agents/" + agentId + "/invoice").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rolloutAdvanceRepayment").value(-200.00))
        .andExpect(jsonPath("$.rolloutAdvanceNewAdvance").value(350.00))
        .andExpect(jsonPath("$.totalAmount").value(150.00 + 2000.00)); // delta + salary, no Fees/Contracts total
  }

  // --- Access control --------------------------------------------------------------------------

  @Test
  void onlyTheInvoicesOwnAgentAndTheManagerCanViewIt() throws Exception {
    String managerToken = managerToken();
    UUID otherClient = createClient(managerToken, "Aurora Retail Group");
    UUID otherAgentId = createAgent(managerToken, "Priya Nair", Country.PHILIPPINES);
    createContract(managerToken, otherClient, otherAgentId);

    // A different Agent (the seeded one) cannot view this other Agent's invoice.
    mockMvc
        .perform(get("/api/agents/" + otherAgentId + "/invoice").header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isForbidden());

    // The Manager always can.
    mockMvc
        .perform(get("/api/agents/" + otherAgentId + "/invoice").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());

    // A Tester can never view any Agent Invoice.
    mockMvc
        .perform(get("/api/agents/" + otherAgentId + "/invoice").header("Authorization", "Bearer " + testerToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void theOwningAgentCanViewTheirOwnInvoice() throws Exception {
    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isOk());
  }

  // --- AC: standing-amount update is next-month-effective only -------------------------------

  @Test
  void managerUpdatesStandingSalaryAndItDoesNotAffectTheCurrentMonthsInvoice() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();

    // Baseline: this month's invoice reflects the original seeded salary (2500.00).
    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salary").value(2500.00));

    mockMvc
        .perform(
            post("/api/agents/" + SEEDED_AGENT_ID + "/standing-amounts")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"amountType":"SALARY","amount":3000.00}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amountType").value("SALARY"))
        .andExpect(jsonPath("$.amount").value(3000.00))
        .andExpect(jsonPath("$.effectiveMonth").value(currentMonth().plusMonths(1).toString()));

    // The change is mid-month, made after the invoice was already opened above — it must still
    // never touch the invoice already in progress.
    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salary").value(2500.00))
        .andExpect(jsonPath("$.totalAmount").value(2500.00));
  }

  @Test
  void aSalaryAndAdvanceChangeInTheSameMonthNeitherAffectsThatMonthsInvoice() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Solene Cosmetics");
    UUID agentId = createAgent(managerToken, "Luis Bautista", Country.MEXICO);
    createContract(managerToken, clientId, agentId);

    mockMvc
        .perform(
            post("/api/agents/" + agentId + "/standing-amounts")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"amountType":"SALARY","amount":2200.00}
                    """))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/agents/" + agentId + "/standing-amounts")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"amountType":"ROLLOUT_ADVANCE","amount":500.00}
                    """))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/agents/" + agentId + "/invoice").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salary").value(2000.00)) // seeded initial salary — createAgent's fixture default
        .andExpect(jsonPath("$.rolloutAdvanceRepayment").value(0))
        .andExpect(jsonPath("$.rolloutAdvanceNewAdvance").value(0));
  }

  @Test
  void onlyAManagerCanUpdateStandingAmounts() throws Exception {
    for (String token : new String[] {agentToken(), testerToken()}) {
      mockMvc
          .perform(
              post("/api/agents/" + SEEDED_AGENT_ID + "/standing-amounts")
                  .header("Authorization", "Bearer " + token)
                  .contentType(APPLICATION_JSON)
                  .content("""
                      {"amountType":"SALARY","amount":9999.00}
                      """))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void updatingStandingAmountsRejectsANegativeAmount() throws Exception {
    mockMvc
        .perform(
            post("/api/agents/" + SEEDED_AGENT_ID + "/standing-amounts")
                .header("Authorization", "Bearer " + managerToken())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"amountType":"SALARY","amount":-50.00}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void standingAmountsEndpointReportsWhatIsCurrentlyInEffect() throws Exception {
    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/standing-amounts").header("Authorization", "Bearer " + managerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salaryAmount").value(2500.00))
        .andExpect(jsonPath("$.rolloutAdvanceAmount").value(0));
  }

  // --- Observability -----------------------------------------------------------------------

  @Test
  void updatingAStandingAmountLogsAnAuditEntryWithOldNewEffectiveMonthAndActor() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      mockMvc
          .perform(
              post("/api/agents/" + SEEDED_AGENT_ID + "/standing-amounts")
                  .header("Authorization", "Bearer " + managerToken())
                  .contentType(APPLICATION_JSON)
                  .content("""
                      {"amountType":"SALARY","amount":2750.00}
                      """))
          .andExpect(status().isCreated());

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertThat(logged).contains("action=STANDING_AMOUNT_CHANGED");
      assertThat(logged).contains("entityId=" + SEEDED_AGENT_ID);
      assertThat(logged).contains("amountType=SALARY");
      assertThat(logged).contains("oldAmount=2500.00");
      assertThat(logged).contains("newAmount=2750.00");
      assertThat(logged).contains("effectiveMonth=" + currentMonth().plusMonths(1));
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  // ===========================================================================================
  // agent-invoice-submission-and-approval ticket: send / override / approve / paid
  // ===========================================================================================

  // --- AC: send transition, snapshot correctness, editability lock ---------------------------

  @Test
  void agentSendsADraftInvoiceMovingItToSentAndFreezesTheNumbersAgainstLaterChanges() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();

    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    addSimCard(managerToken, contractId, "+1-555-0400", "POSTPAID", "20.00");
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");
    UUID topup = submitRequest(testerToken, contractId, "TOPUP");
    logFee(agentToken, contractId, topup, "TOPUP", "30.00");

    // Baseline draft: Local Support Fees = 20 + 30 = 50, Salary = 2500 (seeded).
    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.localSupportFees").value(50.00))
        .andExpect(jsonPath("$.salary").value(2500.00));

    mockMvc
        .perform(
            post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/send").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.sentAt").isNotEmpty())
        .andExpect(jsonPath("$.localSupportFees").value(50.00))
        .andExpect(jsonPath("$.salary").value(2500.00))
        .andExpect(jsonPath("$.totalAmount").value(2550.00));

    // A Fee logged after sending must never change the sent invoice's already-frozen numbers —
    // the whole point of the snapshot-on-send decision (AgentInvoice's Javadoc / CONTEXT.md).
    UUID repair = submitRequest(testerToken, contractId, "REPAIR");
    logFee(agentToken, contractId, repair, "REPAIR", "999.00");

    // A mid-cycle standing-amount change (inserted directly, bypassing the next-month-effective
    // rule, the same "simulate a change already in effect" shape used elsewhere in this class)
    // must also never move the frozen Salary line — the freeze is unconditional, not merely a
    // side effect of the next-month-effective rule.
    insertStandingAmountRow(SEEDED_AGENT_ID, StandingAmountType.SALARY, "9999.00", currentMonth());

    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.localSupportFees").value(50.00))
        .andExpect(jsonPath("$.salary").value(2500.00))
        .andExpect(jsonPath("$.totalAmount").value(2550.00));

    // Manager can review the same sent invoice, with all its lines.
    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.localSupportFees").value(50.00))
        .andExpect(jsonPath("$.rolloutAdvanceRepayment").value(0))
        .andExpect(jsonPath("$.rolloutAdvanceNewAdvance").value(0));
  }

  @Test
  void sendingAnAlreadySentAgentInvoiceIsRejected() throws Exception {
    String agentToken = agentToken();
    mockMvc.perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken));
    mockMvc
        .perform(
            post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/send").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/send").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isConflict());
  }

  @Test
  void onlyTheOwningAgentCanSendTheirInvoiceNotAManagerOrTester() throws Exception {
    String managerToken = managerToken();
    mockMvc.perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + managerToken));

    mockMvc
        .perform(
            post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/send").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/send").header("Authorization", "Bearer " + testerToken()))
        .andExpect(status().isForbidden());
  }

  // --- Contract step: the Manager's current-month action routes are gone ----------------------

  /**
   * remove-current-month-manager-invoice-actions ticket: a Manager overrides, approves and marks
   * paid by the invoice's own id and nothing else, so the three current-month routes addressed by
   * Agent id are deleted rather than merely left uncalled. The rules they used to carry — the
   * {@code SENT -> APPROVED -> PAID} transitions and their preconditions, the override's isolation
   * from standing amounts and from every other invoice, and Manager-only access — are asserted
   * against the by-id routes in {@link AgentInvoiceByIdApiTest}.
   */
  @Test
  void theCurrentMonthOverrideApproveAndPaidRoutesAreGone() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    mockMvc.perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken));
    mockMvc
        .perform(
            post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/send").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/override")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"salary":3200.00}
                    """))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/approve")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/paid").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isNotFound());

    // None of them half-happened: the invoice is exactly as the Agent sent it.
    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.salary").value(2500.00));
  }

  // --- AC: a Client/Tester can never view any Agent Invoice, at any status --------------------

  @Test
  void aTesterCannotReachAnyAgentInvoiceEndpointAtAnyStatus() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    String testerToken = testerToken();

    // DRAFT
    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/send").header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isForbidden());

    mockMvc.perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken));
    MvcResult sent =
        mockMvc
            .perform(
                post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/send")
                    .header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    UUID invoiceId = UUID.fromString(objectMapper.readTree(sent.getResponse().getContentAsString()).get("id").asText());

    // SENT. The Manager's own actions live on the by-id routes now (a Tester's 403 on those is
    // AgentInvoiceByIdApiTest's business); what this test still owns is that the current-month
    // view stays shut to a Tester at every status the invoice passes through.
    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(post("/api/agent-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());

    // APPROVED
    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(post("/api/agent-invoices/" + invoiceId + "/paid").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());

    // PAID
    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isForbidden());
  }

  // --- Observability ---------------------------------------------------------------------------

  /**
   * The send transition's audit entry. Override/approve/paid are audited on the by-id routes —
   * see {@link AgentInvoiceByIdApiTest}.
   */
  @Test
  void theSendTransitionLogsAnAuditEntry() throws Exception {
    String agentToken = agentToken();

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      mockMvc.perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken));

      mockMvc
          .perform(
              post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/send")
                  .header("Authorization", "Bearer " + agentToken))
          .andExpect(status().isOk());

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);

      assertThat(logged).contains("action=STATUS_CHANGE entity=AgentInvoice");
      assertThat(logged).contains("oldStatus=DRAFT newStatus=SENT");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }
}
