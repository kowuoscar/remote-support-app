package com.remotesupport.backend.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.domain.ClientInvoice;
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
    sendClientInvoice(agentToken, approvedContract);
    mockMvc
        .perform(
            post("/api/contracts/" + approvedContract + "/client-invoice/approve")
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

  // --- Access and tenancy ---------------------------------------------------------------------

  @Test
  void anotherTenantsSentInvoicesAreNeverInTheQueue() throws Exception {
    otherTenantFixture.sentClientInvoiceInAnotherTenant();

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
