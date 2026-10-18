package com.remotesupport.backend.web;

import static com.remotesupport.backend.support.ClientInvoiceMonthTestSupport.sendTheMonth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.AgentInvoice;
import com.remotesupport.backend.domain.AgentInvoiceStatus;
import com.remotesupport.backend.domain.BillingMonth;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceLine;
import com.remotesupport.backend.domain.ClientInvoiceLineKind;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.domain.Currency;
import com.remotesupport.backend.repository.AgentInvoiceRepository;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.ClientInvoiceLineRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.support.CarrierInvoiceTestSupport;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The Manager's Tenant-wide money totals (real-manager-dashboard spec, tenant-totals-read ticket):
 * this month's billed (Client Invoices SENT or APPROVED) and payout (Agent Invoices APPROVED or
 * PAID) per currency, each invoice at the frozen total its own page shows. Driven through the real
 * HTTP seam.
 */
@Import(OtherTenantFixture.class)
class TenantTotalsApiTest extends IntegrationTest {

  @Autowired private ClientInvoiceRepository clientInvoiceRepository;
  @Autowired private ClientInvoiceLineRepository clientInvoiceLineRepository;
  @Autowired private AgentInvoiceRepository agentInvoiceRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private OtherTenantFixture otherTenantFixture;

  private void expectEmptyTotals() throws Exception {
    mockMvc
        .perform(get("/api/tenant-totals").header("Authorization", "Bearer " + managerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billed.length()").value(0))
        .andExpect(jsonPath("$.payout.length()").value(0));
  }

  /**
   * Sends the seeded Agent's month (a first send goes through the month send), after covering each
   * given Contract's Postpaid lines; returns each Contract's invoice id, in order. The month can be
   * sent once, so a test with several invoices creates all their Contracts first.
   */
  private List<UUID> sendClientInvoices(String agentToken, UUID... contractIds) throws Exception {
    List<UUID> invoiceIds = new ArrayList<>();
    for (UUID contractId : contractIds) {
      CarrierInvoiceTestSupport.attachCarrierInvoicesForEveryPostpaidLineOfCurrentMonth(
          mockMvc, objectMapper, contractId, agentToken);
      MvcResult draft =
          mockMvc
              .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
              .andExpect(status().isOk())
              .andReturn();
      invoiceIds.add(UUID.fromString(objectMapper.readTree(draft.getResponse().getContentAsString()).get("id").asText()));
    }
    sendTheMonth(mockMvc, agentToken);
    return invoiceIds;
  }

  private UUID sendClientInvoice(String agentToken, UUID contractId) throws Exception {
    return sendClientInvoices(agentToken, contractId).get(0);
  }

  private UUID sendAgentInvoice(String agentToken) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/send").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private void managerPosts(String managerToken, String path) throws Exception {
    mockMvc.perform(post(path).header("Authorization", "Bearer " + managerToken)).andExpect(status().isOk());
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

  private UUID submitTopupRequest(String testerToken, UUID contractId) throws Exception {
    UUID targetSimCardId = createTopupTargetSimCard(managerToken(), contractId);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/requests")
                    .header("Authorization", "Bearer " + testerToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"type":"TOPUP","targetSimCardId":"%s","description":"Top-up needed"}
                        """
                            .formatted(targetSimCardId)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  /** A Contract with a Tester, one postpaid SIM Card at 25.00 and fees of 45.00 and 5.50: totals 75.50. */
  private UUID contractWithTotal(String managerToken, String agentToken, String clientName, String testerName)
      throws Exception {
    UUID clientId = createClient(managerToken, clientName);
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken = createTesterAndLogin(managerToken, clientId, testerName);
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/sim-cards")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(postpaidSimCardJson(managerToken, contractId, "+1-555-0142", "25.00")))
        .andExpect(status().isCreated());
    logTopupFee(agentToken, contractId, submitTopupRequest(testerToken, contractId), "45.00");
    logTopupFee(agentToken, contractId, submitTopupRequest(testerToken, contractId), "5.50");
    return contractId;
  }

  /** Writes a Client Invoice straight to the repository, with one stored line of {@code amount}. */
  private ClientInvoice storedClientInvoice(
      Contract contract, ClientInvoiceStatus status, int monthsBack, String amount) {
    Instant now = Instant.now();
    ClientInvoice invoice = new ClientInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setTenant(contract.getTenant());
    invoice.setContract(contract);
    invoice.setBillingMonth(BillingMonth.current().minusMonths(monthsBack));
    invoice.setStatus(status);
    invoice.setCurrency(contract.getCurrency());
    invoice.setCreatedAt(now);
    invoice.setSentAt(now);
    invoice.setFirstSentAt(now);
    clientInvoiceRepository.saveAndFlush(invoice);
    storedLine(invoice, amount);
    return invoice;
  }

  private void storedLine(ClientInvoice invoice, String amount) {
    ClientInvoiceLine line = new ClientInvoiceLine();
    line.setId(UUID.randomUUID());
    line.setTenant(invoice.getTenant());
    line.setClientInvoice(invoice);
    line.setKind(ClientInvoiceLineKind.BASE_AMOUNT);
    line.setComputedAmount(new BigDecimal(amount));
    line.setAmount(new BigDecimal(amount));
    line.setCreatedAt(Instant.now());
    clientInvoiceLineRepository.saveAndFlush(line);
  }

  // --- Shape ----------------------------------------------------------------------------------

  @Test
  void anEmptyTenantGetsTheCurrentBillingMonthAndTwoEmptyLists() throws Exception {
    mockMvc
        .perform(get("/api/tenant-totals").header("Authorization", "Bearer " + managerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billingMonth").value(BillingMonth.current().toString()))
        .andExpect(jsonPath("$.billed.length()").value(0))
        .andExpect(jsonPath("$.payout.length()").value(0));
  }

  // --- Billed ---------------------------------------------------------------------------------

  @Test
  void aSentAndAnApprovedClientInvoiceBothCountAtTheTotalTheInvoiceEndpointReturns() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID sentContract = contractWithTotal(managerToken, agentToken, "Solene Cosmetics", "ines@solene.example");
    UUID approvedContract = createContract(managerToken, createClient(managerToken, "Bright Path"), SEEDED_AGENT_ID);
    List<UUID> sent = sendClientInvoices(agentToken, sentContract, approvedContract);
    UUID sentId = sent.get(0);
    UUID approvedId = sent.get(1);
    managerPosts(managerToken, "/api/client-invoices/" + approvedId + "/approve");

    mockMvc
        .perform(get("/api/client-invoices/" + sentId).header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.totalAmount").value(75.50));

    mockMvc
        .perform(get("/api/tenant-totals").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billed.length()").value(1))
        .andExpect(jsonPath("$.billed[0].currency").value("USD"))
        .andExpect(jsonPath("$.billed[0].amount").value(75.50))
        .andExpect(jsonPath("$.payout.length()").value(0));
  }

  @Test
  void aDraftASentBackDraftAndLastMonthsSentClientInvoiceDoNotCount() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();

    UUID sentBackContract = contractWithTotal(managerToken, agentToken, "Kessler", "k@kessler.example");
    UUID sentBackId = sendClientInvoice(agentToken, sentBackContract);

    // Added after the month was sent: its invoice stays a draft.
    UUID draftContract = createContract(managerToken, createClient(managerToken, "Meridian"), SEEDED_AGENT_ID);
    mockMvc.perform(
        get("/api/contracts/" + draftContract + "/client-invoice").header("Authorization", "Bearer " + agentToken));

    mockMvc
        .perform(
            post("/api/client-invoices/" + sentBackId + "/send-back")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"Please recheck\"}"))
        .andExpect(status().isOk());

    Contract lastMonthContract =
        contractRepository
            .findById(createContract(managerToken, createClient(managerToken, "Old Co"), SEEDED_AGENT_ID))
            .orElseThrow();
    storedClientInvoice(lastMonthContract, ClientInvoiceStatus.SENT, 1, "40.00");

    expectEmptyTotals();
  }

  @Test
  void aFeeLoggedAfterTheSendDoesNotMoveBilled() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID clientId = createClient(managerToken, "Solene Cosmetics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken = createTesterAndLogin(managerToken, clientId, "ines@solene.example");
    logTopupFee(agentToken, contractId, submitTopupRequest(testerToken, contractId), "45.00");
    sendClientInvoice(agentToken, contractId);

    logTopupFee(agentToken, contractId, submitTopupRequest(testerToken, contractId), "999.00");

    mockMvc
        .perform(get("/api/tenant-totals").header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.billed[0].amount").value(45.00));
  }

  // --- Payout ---------------------------------------------------------------------------------

  @Test
  void anApprovedAgentInvoiceCountsAndStaysCountedWhenPaidAtItsTotalIncludingAnOverride() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = sendAgentInvoice(agentToken());
    mockMvc
        .perform(
            post("/api/agent-invoices/" + invoiceId + "/override")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"rolloutAdvanceNewAdvance":150.00}
                    """))
        .andExpect(status().isOk());
    managerPosts(managerToken, "/api/agent-invoices/" + invoiceId + "/approve");

    mockMvc
        .perform(get("/api/tenant-totals").header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.payout.length()").value(1))
        .andExpect(jsonPath("$.payout[0].currency").value("USD"))
        .andExpect(jsonPath("$.payout[0].amount").value(2650.00))
        .andExpect(jsonPath("$.billed.length()").value(0));

    managerPosts(managerToken, "/api/agent-invoices/" + invoiceId + "/paid");

    mockMvc
        .perform(get("/api/tenant-totals").header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.payout[0].amount").value(2650.00));
  }

  @Test
  void aDraftAndASentAgentInvoiceDoNotCountInPayout() throws Exception {
    String agentToken = agentToken();
    mockMvc
        .perform(get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());
    expectEmptyTotals();

    sendAgentInvoice(agentToken);
    expectEmptyTotals();
  }

  @Test
  void lastMonthsApprovedAgentInvoiceDoesNotCountInPayout() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = sendAgentInvoice(agentToken());
    managerPosts(managerToken, "/api/agent-invoices/" + invoiceId + "/approve");
    AgentInvoice invoice = agentInvoiceRepository.findById(invoiceId).orElseThrow();
    invoice.setBillingMonth(invoice.getBillingMonth().minusMonths(1));
    agentInvoiceRepository.saveAndFlush(invoice);

    expectEmptyTotals();
  }

  // --- Currencies and tenancy -----------------------------------------------------------------

  @Test
  void aUsdAndAGbpContractGiveTwoEntriesOrderedByCurrencyCodeNeverOneSum() throws Exception {
    String managerToken = managerToken();
    UUID ukAgentId = createAgent(managerToken, "Priya Shah", Country.UNITED_KINGDOM);
    Contract gbpContract =
        contractRepository
            .findById(createContract(managerToken, createClient(managerToken, "London Co"), ukAgentId))
            .orElseThrow();
    Contract usdContract =
        contractRepository
            .findById(createContract(managerToken, createClient(managerToken, "NYC Co"), SEEDED_AGENT_ID))
            .orElseThrow();
    storedClientInvoice(usdContract, ClientInvoiceStatus.SENT, 0, "115.99");
    storedClientInvoice(gbpContract, ClientInvoiceStatus.APPROVED, 0, "20.00");

    Agent ukAgent = agentRepository.findById(ukAgentId).orElseThrow();
    AgentInvoice ukPayout = new AgentInvoice();
    ukPayout.setId(UUID.randomUUID());
    ukPayout.setTenant(ukAgent.getTenant());
    ukPayout.setAgent(ukAgent);
    ukPayout.setBillingMonth(BillingMonth.current());
    ukPayout.setStatus(AgentInvoiceStatus.PAID);
    ukPayout.setCurrency(Currency.GBP);
    ukPayout.setCreatedAt(Instant.now());
    ukPayout.setSnapshotLocalSupportFees(new BigDecimal("10.00"));
    ukPayout.setSnapshotSalary(new BigDecimal("2000.00"));
    ukPayout.setSnapshotRolloutAdvanceRepayment(BigDecimal.ZERO);
    ukPayout.setSnapshotRolloutAdvanceNewAdvance(new BigDecimal("5.25"));
    agentInvoiceRepository.saveAndFlush(ukPayout);

    mockMvc
        .perform(get("/api/tenant-totals").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billed.length()").value(2))
        .andExpect(jsonPath("$.billed[0].currency").value("GBP"))
        .andExpect(jsonPath("$.billed[0].amount").value(20.00))
        .andExpect(jsonPath("$.billed[1].currency").value("USD"))
        .andExpect(jsonPath("$.billed[1].amount").value(115.99))
        .andExpect(jsonPath("$.payout.length()").value(1))
        .andExpect(jsonPath("$.payout[0].currency").value("GBP"))
        .andExpect(jsonPath("$.payout[0].amount").value(2015.25));
  }

  @Test
  void anotherTenantsInvoicesAreNeverCounted() throws Exception {
    // Both fixture invoices are SENT; lift them to the counted statuses so only tenancy excludes them.
    UUID otherClientInvoiceId = otherTenantFixture.sentClientInvoiceInAnotherTenant();
    UUID otherAgentInvoiceId = otherTenantFixture.sentAgentInvoiceInAnotherTenant();
    ClientInvoice otherClientInvoice = clientInvoiceRepository.findById(otherClientInvoiceId).orElseThrow();
    otherClientInvoice.setStatus(ClientInvoiceStatus.APPROVED);
    clientInvoiceRepository.saveAndFlush(otherClientInvoice);
    storedLine(otherClientInvoice, "10.00");
    AgentInvoice otherAgentInvoice = agentInvoiceRepository.findById(otherAgentInvoiceId).orElseThrow();
    otherAgentInvoice.setStatus(AgentInvoiceStatus.APPROVED);
    agentInvoiceRepository.saveAndFlush(otherAgentInvoice);

    expectEmptyTotals();
  }

  // --- Access and side effects ----------------------------------------------------------------

  @Test
  void anAgentAndATesterAreForbiddenAndNoTokenIsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/tenant-totals").header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/tenant-totals").header("Authorization", "Bearer " + testerToken()))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/tenant-totals")).andExpect(status().isUnauthorized());
  }

  @Test
  void readingTheTotalsCreatesNoInvoiceRow() throws Exception {
    long clientInvoices = clientInvoiceRepository.count();
    long agentInvoices = agentInvoiceRepository.count();

    expectEmptyTotals();

    assertThat(clientInvoiceRepository.count()).isEqualTo(clientInvoices);
    assertThat(agentInvoiceRepository.count()).isEqualTo(agentInvoices);
  }
}
