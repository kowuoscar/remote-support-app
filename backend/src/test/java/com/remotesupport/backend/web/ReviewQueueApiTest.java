package com.remotesupport.backend.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.domain.AgentInvoice;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.repository.AgentInvoiceRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The Manager's Review Queue (manager-invoice-review-queue spec, client-invoice-review-page
 * ticket): every invoice waiting on a Manager action, across Contracts and billing months, longest
 * waiting first. Driven through the real HTTP seam, like {@link ClientInvoiceApiTest}.
 */
@Import(OtherTenantFixture.class)
class ReviewQueueApiTest extends IntegrationTest {

  @Autowired private ClientInvoiceRepository clientInvoiceRepository;
  @Autowired private AgentInvoiceRepository agentInvoiceRepository;
  @Autowired private OtherTenantFixture otherTenantFixture;

  private UUID sendClientInvoice(String agentToken, UUID contractId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/client-invoice/send")
                    .header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID submitRequest(String testerToken, UUID contractId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/requests")
                    .header("Authorization", "Bearer " + testerToken)
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"type":"TOPUP"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private void logTopupFee(String agentToken, UUID contractId, UUID requestId, String amount) throws Exception {
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"requestId":"%s","feeType":"TOPUP","amount":%s}
                    """.formatted(requestId, amount)))
        .andExpect(status().isCreated());
  }

  /** Stands in for "sent a while ago, possibly in an earlier month" — no real time passes in a test. */
  private void backdate(UUID invoiceId, Instant sentAt, int monthsBack) {
    ClientInvoice invoice = clientInvoiceRepository.findById(invoiceId).orElseThrow();
    invoice.setSentAt(sentAt);
    invoice.setBillingMonth(invoice.getBillingMonth().minusMonths(monthsBack));
    clientInvoiceRepository.saveAndFlush(invoice);
  }

  /** The seeded Agent sends their current-month Agent Invoice. */
  private UUID sendAgentInvoice(String agentToken) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/send").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  /**
   * Moves an Agent Invoice {@code monthsBack} billing months into the past, which also frees the
   * seeded Agent's current month so another invoice can be sent in the same test.
   */
  private void backdateAgentInvoice(UUID invoiceId, Instant sentAt, Instant approvedAt, int monthsBack) {
    AgentInvoice invoice = agentInvoiceRepository.findById(invoiceId).orElseThrow();
    invoice.setSentAt(sentAt);
    if (approvedAt != null) {
      invoice.setApprovedAt(approvedAt);
    }
    invoice.setBillingMonth(invoice.getBillingMonth().minusMonths(monthsBack));
    agentInvoiceRepository.saveAndFlush(invoice);
  }

  private void managerPosts(String managerToken, String path) throws Exception {
    mockMvc.perform(post(path).header("Authorization", "Bearer " + managerToken)).andExpect(status().isOk());
  }

  // --- Membership -----------------------------------------------------------------------------

  @Test
  void aSentClientInvoiceIsInTheQueueWhileDraftAndApprovedOnesAreNot() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();

    UUID sentContract = createContract(managerToken, createClient(managerToken, "Aurora Retail Group"), SEEDED_AGENT_ID);
    UUID sentInvoiceId = sendClientInvoice(agentToken, sentContract);

    UUID draftContract = createContract(managerToken, createClient(managerToken, "Meridian Logistics"), SEEDED_AGENT_ID);
    mockMvc.perform(
        get("/api/contracts/" + draftContract + "/client-invoice").header("Authorization", "Bearer " + agentToken));

    UUID approvedContract =
        createContract(managerToken, createClient(managerToken, "Bright Path Clinics"), SEEDED_AGENT_ID);
    UUID approvedInvoiceId = sendClientInvoice(agentToken, approvedContract);
    mockMvc
        .perform(
            post("/api/client-invoices/" + approvedInvoiceId + "/approve")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].kind").value("CLIENT_INVOICE"))
        .andExpect(jsonPath("$[0].id").value(sentInvoiceId.toString()))
        .andExpect(jsonPath("$[0].status").value("SENT"))
        .andExpect(jsonPath("$[0].contractId").value(sentContract.toString()))
        .andExpect(jsonPath("$[0].clientName").value("Aurora Retail Group"))
        .andExpect(jsonPath("$[0].agentName").value("Jordan Ellis"))
        .andExpect(jsonPath("$[0].currency").value("USD"))
        .andExpect(jsonPath("$[0].waitingSince").isNotEmpty());
  }

  @Test
  void aSentClientInvoiceFromAPastBillingMonthIsStillInTheQueue() throws Exception {
    String managerToken = managerToken();
    UUID contractId = createContract(managerToken, createClient(managerToken, "Kessler & Vance LLP"), SEEDED_AGENT_ID);
    UUID invoiceId = sendClientInvoice(agentToken(), contractId);
    backdate(invoiceId, Instant.now().minus(40, ChronoUnit.DAYS), 2);

    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(invoiceId.toString()))
        .andExpect(
            jsonPath("$[0].billingMonth")
                .value(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(2).toString()));
  }

  @Test
  void sentAndApprovedAgentInvoicesAreInTheQueueWhileDraftAndPaidOnesAreNot() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    Instant now = Instant.now();

    UUID paid = sendAgentInvoice(agentToken);
    managerPosts(managerToken, "/api/agent-invoices/" + paid + "/approve");
    managerPosts(managerToken, "/api/agent-invoices/" + paid + "/paid");
    backdateAgentInvoice(paid, now.minus(90, ChronoUnit.DAYS), now.minus(80, ChronoUnit.DAYS), 3);

    UUID approved = sendAgentInvoice(agentToken);
    managerPosts(managerToken, "/api/agent-invoices/" + approved + "/approve");
    backdateAgentInvoice(approved, now.minus(60, ChronoUnit.DAYS), now.minus(50, ChronoUnit.DAYS), 2);

    UUID sent = sendAgentInvoice(agentToken);
    backdateAgentInvoice(sent, now.minus(30, ChronoUnit.DAYS), null, 1);

    // This month's invoice, opened but never sent.
    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].kind").value("AGENT_INVOICE"))
        .andExpect(jsonPath("$[0].id").value(approved.toString()))
        .andExpect(jsonPath("$[0].status").value("APPROVED"))
        .andExpect(jsonPath("$[0].billingMonth")
            .value(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(2).toString()))
        .andExpect(jsonPath("$[0].agentId").value(SEEDED_AGENT_ID.toString()))
        .andExpect(jsonPath("$[0].agentName").value("Jordan Ellis"))
        .andExpect(jsonPath("$[0].contractId").isEmpty())
        .andExpect(jsonPath("$[0].clientName").isEmpty())
        .andExpect(jsonPath("$[0].currency").value("USD"))
        .andExpect(jsonPath("$[0].totalAmount").value(2500.00))
        .andExpect(jsonPath("$[1].kind").value("AGENT_INVOICE"))
        .andExpect(jsonPath("$[1].id").value(sent.toString()))
        .andExpect(jsonPath("$[1].status").value("SENT"));
  }

  @Test
  void anAgentInvoiceLeavesTheQueueOnlyOnceItIsPaid() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = sendAgentInvoice(agentToken());

    managerPosts(managerToken, "/api/agent-invoices/" + invoiceId + "/approve");
    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(invoiceId.toString()))
        .andExpect(jsonPath("$[0].status").value("APPROVED"));

    managerPosts(managerToken, "/api/agent-invoices/" + invoiceId + "/paid");
    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.length()").value(0));
  }

  // --- Order ----------------------------------------------------------------------------------

  @Test
  void theQueueIsOrderedLongestWaitingFirstWithTheInvoiceIdBreakingTies() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    UUID sentTwoDaysAgo =
        sendClientInvoice(agentToken, createContract(managerToken, createClient(managerToken, "A Co"), SEEDED_AGENT_ID));
    UUID sentFiveDaysAgo =
        sendClientInvoice(agentToken, createContract(managerToken, createClient(managerToken, "B Co"), SEEDED_AGENT_ID));
    UUID tieOne =
        sendClientInvoice(agentToken, createContract(managerToken, createClient(managerToken, "C Co"), SEEDED_AGENT_ID));
    UUID tieTwo =
        sendClientInvoice(agentToken, createContract(managerToken, createClient(managerToken, "D Co"), SEEDED_AGENT_ID));
    backdate(sentTwoDaysAgo, now.minus(2, ChronoUnit.DAYS), 0);
    backdate(sentFiveDaysAgo, now.minus(5, ChronoUnit.DAYS), 0);
    backdate(tieOne, now.minus(1, ChronoUnit.DAYS), 0);
    backdate(tieTwo, now.minus(1, ChronoUnit.DAYS), 0);

    String firstTie = tieOne.toString().compareTo(tieTwo.toString()) < 0 ? tieOne.toString() : tieTwo.toString();
    String secondTie = firstTie.equals(tieOne.toString()) ? tieTwo.toString() : tieOne.toString();

    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[0].id").value(sentFiveDaysAgo.toString()))
        .andExpect(jsonPath("$[1].id").value(sentTwoDaysAgo.toString()))
        .andExpect(jsonPath("$[2].id").value(firstTie))
        .andExpect(jsonPath("$[3].id").value(secondTie));
  }

  @Test
  void clientAndAgentInvoicesInterleaveByWaitingSinceWithAnApprovedAgentInvoiceWaitingSinceApproval()
      throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    // Sent 20 days ago but approved 2 days ago: it has been waiting (to be paid) for 2 days.
    UUID approvedAgentInvoice = sendAgentInvoice(agentToken);
    managerPosts(managerToken, "/api/agent-invoices/" + approvedAgentInvoice + "/approve");
    backdateAgentInvoice(approvedAgentInvoice, now.minus(20, ChronoUnit.DAYS), now.minus(2, ChronoUnit.DAYS), 1);

    UUID sentAgentInvoice = sendAgentInvoice(agentToken);
    backdateAgentInvoice(sentAgentInvoice, now.minus(4, ChronoUnit.DAYS), null, 0);

    UUID clientSentFiveDaysAgo =
        sendClientInvoice(agentToken, createContract(managerToken, createClient(managerToken, "A Co"), SEEDED_AGENT_ID));
    UUID clientSentThreeDaysAgo =
        sendClientInvoice(agentToken, createContract(managerToken, createClient(managerToken, "B Co"), SEEDED_AGENT_ID));
    UUID clientSentOneDayAgo =
        sendClientInvoice(agentToken, createContract(managerToken, createClient(managerToken, "C Co"), SEEDED_AGENT_ID));
    backdate(clientSentFiveDaysAgo, now.minus(5, ChronoUnit.DAYS), 0);
    backdate(clientSentThreeDaysAgo, now.minus(3, ChronoUnit.DAYS), 0);
    backdate(clientSentOneDayAgo, now.minus(1, ChronoUnit.DAYS), 0);

    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[0].id").value(clientSentFiveDaysAgo.toString()))
        .andExpect(jsonPath("$[1].id").value(sentAgentInvoice.toString()))
        .andExpect(jsonPath("$[2].id").value(clientSentThreeDaysAgo.toString()))
        .andExpect(jsonPath("$[3].id").value(approvedAgentInvoice.toString()))
        .andExpect(jsonPath("$[3].waitingSince").value(now.minus(2, ChronoUnit.DAYS).toString()))
        .andExpect(jsonPath("$[4].id").value(clientSentOneDayAgo.toString()));
  }

  // --- Totals ---------------------------------------------------------------------------------

  @Test
  void aQueueItemsTotalIsTheSentSnapshotNotALaterRecomputation() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID clientId = createClient(managerToken, "Solene Cosmetics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken = createTesterAndLogin(managerToken, clientId, "ines.moreau@solene.example", "Passw0rd!23");

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/sim-cards")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"number":"+1-555-0142","flavor":"POSTPAID","monthlyFeeAmount":25.00}
                    """))
        .andExpect(status().isCreated());
    logTopupFee(agentToken, contractId, submitRequest(testerToken, contractId), "45.00");
    logTopupFee(agentToken, contractId, submitRequest(testerToken, contractId), "5.50");
    sendClientInvoice(agentToken, contractId);

    // Logged after sending: must not move the total the Manager is shown.
    logTopupFee(agentToken, contractId, submitRequest(testerToken, contractId), "999.00");

    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].totalAmount").value(75.50));
  }

  @Test
  void aSentInvoiceWithNoFeeLinesTotalsItsBaseAmount() throws Exception {
    String managerToken = managerToken();
    sendClientInvoice(agentToken(), createContract(managerToken, createClient(managerToken, "Empty Co"), SEEDED_AGENT_ID));

    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].totalAmount").value(0));
  }

  @Test
  void anAgentInvoiceQueueTotalIsItsSnapshotIncludingAManagerOverride() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = sendAgentInvoice(agentToken());

    // A standing-amount change after sending never moves the frozen total...
    mockMvc
        .perform(
            post("/api/agents/" + SEEDED_AGENT_ID + "/standing-amounts")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"amountType":"SALARY","amount":9000.00}
                    """))
        .andExpect(status().isCreated());
    // ...but the Manager's override on this invoice does.
    mockMvc
        .perform(
            post("/api/agent-invoices/" + invoiceId + "/override")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"rolloutAdvanceNewAdvance":150.00}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$[0].id").value(invoiceId.toString()))
        .andExpect(jsonPath("$[0].totalAmount").value(2650.00));
  }

  // --- Access and tenancy ---------------------------------------------------------------------

  @Test
  void anotherTenantsSentInvoicesAreNeverInTheQueue() throws Exception {
    otherTenantFixture.sentClientInvoiceInAnotherTenant();
    otherTenantFixture.sentAgentInvoiceInAnotherTenant();

    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void anAgentCannotReadTheReviewQueue() throws Exception {
    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void aTesterCannotReadTheReviewQueue() throws Exception {
    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + testerToken()))
        .andExpect(status().isForbidden());
  }
}
