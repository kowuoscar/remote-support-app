package com.remotesupport.backend.web;

import static com.remotesupport.backend.support.ClientInvoiceMonthTestSupport.currentMonth;
import static com.remotesupport.backend.support.ClientInvoiceMonthTestSupport.sendTheMonth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.domain.AgentInvoice;
import com.remotesupport.backend.domain.BillingMonth;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.repository.AgentInvoiceRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.support.CarrierInvoiceTestSupport;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The Manager's review by Agent (manager-reviews-by-agent spec, "Reads"): the Agents with what
 * waits on them, an Agent's months, and an Agent's month, through the real HTTP seam with real
 * Postgres. Every case builds a fresh Agent, because the seeded Agent holds no Contract in the test
 * database and a month can be sent only once.
 */
@Import(OtherTenantFixture.class)
class ReviewByAgentApiTest extends IntegrationTest {

  @Autowired private ClientInvoiceRepository clientInvoiceRepository;
  @Autowired private AgentInvoiceRepository agentInvoiceRepository;
  @Autowired private OtherTenantFixture otherTenantFixture;

  private String managerToken;

  private record NewAgent(UUID id, String token) {}

  private NewAgent newAgent(String name) throws Exception {
    managerToken = managerToken();
    String email = "review-agent-" + UUID.randomUUID() + "@agents.example";
    MvcResult created =
        mockMvc
            .perform(
                post("/api/agents")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","country":"UNITED_STATES","salaryAmount":2000.00,"username":"%s"}
                        """
                            .formatted(name, email)))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
    return new NewAgent(UUID.fromString(body.get("id").asText()), loginAs(email, body.get("password").asText()));
  }

  /** A Contract with a Postpaid SIM for the Agent, its files attached and its draft opened. */
  private UUID contract(NewAgent agent, String clientName) throws Exception {
    UUID contractId = createContract(managerToken, createClient(managerToken, clientName), agent.id());
    addUniquePostpaidSim(managerToken, contractId);
    CarrierInvoiceTestSupport.attachCarrierInvoicesForEveryPostpaidLineOfCurrentMonth(
        mockMvc, objectMapper, contractId, agent.token());
    return open(agent, contractId);
  }

  private UUID open(NewAgent agent, UUID contractId) throws Exception {
    mockMvc
            .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agent.token()))
            .andExpect(status().isOk());
    return contractId;
  }

  private ClientInvoice invoiceOf(UUID contractId) {
    return clientInvoiceRepository
        .findByContractIdAndBillingMonth(contractId, BillingMonth.current())
        .orElseThrow();
  }

  private JsonNode json(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode read(String token, String path) throws Exception {
    return json(mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn());
  }

  private JsonNode agentsList() throws Exception {
    return read(managerToken(), "/api/review-queue/agents");
  }

  private JsonNode agentRow(UUID agentId) throws Exception {
    for (JsonNode row : agentsList()) {
      if (row.get("agentId").asText().equals(agentId.toString())) {
        return row;
      }
    }
    return null;
  }

  private String monthPath(UUID agentId, String month) {
    return "/api/review-queue/agents/" + agentId + "/months/" + month;
  }

  private void approve(UUID invoiceId) throws Exception {
    mockMvc
        .perform(post("/api/client-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());
  }

  private void sendBack(UUID invoiceId) throws Exception {
    mockMvc
        .perform(
            post("/api/client-invoices/" + invoiceId + "/send-back")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"Please check the second line\"}"))
        .andExpect(status().isOk());
  }

  private void sendAgentInvoice(NewAgent agent) throws Exception {
    mockMvc
        .perform(post("/api/agents/" + agent.id() + "/invoice/send").header("Authorization", "Bearer " + agent.token()))
        .andExpect(status().isOk());
  }

  private static String previous(String month) {
    return YearMonth.parse(month).minusMonths(1).toString();
  }

  // --- The Agents -----------------------------------------------------------------------------

  @Test
  void theAgentsCountWhatWaitsOnTheManagerAndAnAgentWithNothingIsZero() throws Exception {
    NewAgent busy = newAgent("Busy Agent");
    NewAgent quiet = newAgent("Quiet Agent");
    contract(busy, "Count Client A");
    contract(busy, "Count Client B");
    sendTheMonth(mockMvc, busy.token());
    sendAgentInvoice(busy);

    assertThat(agentRow(busy.id()).get("waiting").asInt()).isEqualTo(3);
    assertThat(agentRow(busy.id()).get("agentName").asText()).isEqualTo("Busy Agent");
    assertThat(agentRow(quiet.id()).get("waiting").asInt()).isZero();
  }

  @Test
  void anApprovedAgentInvoiceStillWaitsButAnApprovedClientInvoiceDoesNot() throws Exception {
    NewAgent agent = newAgent("Approving Agent");
    UUID contractId = contract(agent, "Approving Client");
    sendTheMonth(mockMvc, agent.token());
    sendAgentInvoice(agent);
    assertThat(agentRow(agent.id()).get("waiting").asInt()).isEqualTo(2);

    approve(invoiceOf(contractId).getId());

    assertThat(agentRow(agent.id()).get("waiting").asInt()).isEqualTo(1);
  }

  @Test
  void theAgentsAreOrderedByLowercaseNameThenIdAndOtherTenantsAreAbsent() throws Exception {
    NewAgent lower = newAgent("zz-order lower");
    NewAgent upper = newAgent("ZZ-Order Upper");
    NewAgent middle = newAgent("zz-order Middle");
    UUID other = otherTenantFixture.agentLoginInAnotherTenant("other-" + UUID.randomUUID(), "Passw0rd!x").agentId();

    List<String> ids = new ArrayList<>();
    for (JsonNode row : agentsList()) {
      ids.add(row.get("agentId").asText());
    }

    assertThat(ids.indexOf(lower.id().toString())).isGreaterThanOrEqualTo(0);
    assertThat(ids.indexOf(lower.id().toString())).isLessThan(ids.indexOf(middle.id().toString()));
    assertThat(ids.indexOf(middle.id().toString())).isLessThan(ids.indexOf(upper.id().toString()));
    assertThat(ids).doesNotContain(other.toString());
  }

  @Test
  void theWaitingCountsSumToTheLengthOfTheReviewQueue() throws Exception {
    NewAgent agent = newAgent("Summing Agent");
    contract(agent, "Summing Client");
    sendTheMonth(mockMvc, agent.token());
    sendAgentInvoice(agent);

    long sum = 0;
    for (JsonNode row : agentsList()) {
      sum += row.get("waiting").asLong();
    }

    assertThat(sum).isEqualTo(read(managerToken, "/api/review-queue").size());
  }

  // --- The months -----------------------------------------------------------------------------

  @Test
  void theMonthsComeNewestFirstWithWhatWaitsInEachAndOmitNeverSentDrafts() throws Exception {
    NewAgent agent = newAgent("Months Agent");
    UUID older = contract(agent, "Months Client Old");
    UUID newer = contract(agent, "Months Client New");
    sendTheMonth(mockMvc, agent.token());
    // Stands in for a month sent a month ago: the invoice stays SENT, now in the previous month.
    ClientInvoice olderInvoice = invoiceOf(older);
    olderInvoice.setBillingMonth(olderInvoice.getBillingMonth().minusMonths(1));
    clientInvoiceRepository.saveAndFlush(olderInvoice);
    approve(invoiceOf(newer).getId());
    sendAgentInvoice(agent);
    // A draft opened after the send never reached the Manager, whatever month it sits in.
    UUID draftOnly = createContract(managerToken, createClient(managerToken, "Months Draft Client"), agent.id());
    open(agent, draftOnly);
    ClientInvoice draft = invoiceOf(draftOnly);
    draft.setBillingMonth(draft.getBillingMonth().minusMonths(2));
    clientInvoiceRepository.saveAndFlush(draft);

    JsonNode months = read(managerToken, "/api/review-queue/agents/" + agent.id() + "/months");

    assertThat(months.size()).isEqualTo(2);
    assertThat(months.get(0).get("billingMonth").asText()).isEqualTo(currentMonth() + "-01");
    assertThat(months.get(0).get("waiting").asInt()).isEqualTo(1);
    assertThat(months.get(1).get("billingMonth").asText()).isEqualTo(previous(currentMonth()) + "-01");
    assertThat(months.get(1).get("waiting").asInt()).isEqualTo(1);
  }

  @Test
  void aMonthHoldingOnlyANonDraftAgentInvoiceIsListed() throws Exception {
    NewAgent agent = newAgent("Agent Invoice Only");
    sendAgentInvoice(agent);

    JsonNode months = read(managerToken, "/api/review-queue/agents/" + agent.id() + "/months");

    assertThat(months.size()).isEqualTo(1);
    assertThat(months.get(0).get("billingMonth").asText()).isEqualTo(currentMonth() + "-01");
    assertThat(months.get(0).get("waiting").asInt()).isEqualTo(1);
  }

  // --- The month ------------------------------------------------------------------------------

  @Test
  void theMonthListsTheInvoicesThatReachedTheManagerByClientNameWithTheirReviewStatus() throws Exception {
    NewAgent agent = newAgent("Month Agent");
    UUID charlie = contract(agent, "Charlie Client");
    UUID alpha = contract(agent, "alpha Client");
    UUID bravo = contract(agent, "Bravo Client");
    sendTheMonth(mockMvc, agent.token());
    approve(invoiceOf(bravo).getId());
    sendBack(invoiceOf(charlie).getId());
    // A late draft: opened after the send, so it never reached the Manager.
    UUID late = createContract(managerToken, createClient(managerToken, "Delta Late Client"), agent.id());
    open(agent, late);

    JsonNode month = read(managerToken, monthPath(agent.id(), currentMonth()));

    assertThat(month.get("agentId").asText()).isEqualTo(agent.id().toString());
    assertThat(month.get("agentName").asText()).isEqualTo("Month Agent");
    assertThat(month.get("billingMonth").asText()).isEqualTo(currentMonth() + "-01");
    assertThat(month.get("monthSentAt").isNull()).isFalse();
    JsonNode invoices = month.get("clientInvoices");
    assertThat(invoices.size()).isEqualTo(3);
    assertThat(invoices.get(0).get("clientName").asText()).isEqualTo("alpha Client");
    assertThat(invoices.get(0).get("reviewStatus").asText()).isEqualTo("TO_REVIEW");
    assertThat(invoices.get(1).get("clientName").asText()).isEqualTo("Bravo Client");
    assertThat(invoices.get(1).get("reviewStatus").asText()).isEqualTo("APPROVED");
    assertThat(invoices.get(1).get("approvedAt").isNull()).isFalse();
    assertThat(invoices.get(2).get("clientName").asText()).isEqualTo("Charlie Client");
    assertThat(invoices.get(2).get("reviewStatus").asText()).isEqualTo("SENT_BACK");
    assertThat(invoices.get(2).get("sentBackAt").isNull()).isFalse();
    assertThat(invoices.get(0).get("id").asText()).isEqualTo(invoiceOf(alpha).getId().toString());
    assertThat(invoices.get(0).get("contractId").asText()).isEqualTo(alpha.toString());
    assertThat(month.get("agentInvoice").isNull()).isTrue();
  }

  @Test
  void theMonthServesTheTotalFromTheStoredLinesAndTheAgentInvoiceOnceSent() throws Exception {
    NewAgent agent = newAgent("Totals Agent");
    UUID contractId = contract(agent, "Totals Client");
    sendTheMonth(mockMvc, agent.token());
    sendAgentInvoice(agent);
    UUID invoiceId = invoiceOf(contractId).getId();
    JsonNode queued = null;
    for (JsonNode item : read(managerToken, "/api/review-queue")) {
      if (item.get("id").asText().equals(invoiceId.toString())) {
        queued = item;
      }
    }

    JsonNode month = read(managerToken, monthPath(agent.id(), currentMonth()));

    assertThat(queued).isNotNull();
    assertThat(month.get("clientInvoices").get(0).get("totalAmount").decimalValue())
        .isEqualByComparingTo(queued.get("totalAmount").decimalValue());
    assertThat(month.get("clientInvoices").get(0).get("totalAmount").decimalValue().signum()).isPositive();
    JsonNode agentInvoice = month.get("agentInvoice");
    assertThat(agentInvoice.get("status").asText()).isEqualTo("SENT");
    assertThat(agentInvoice.get("currency").asText()).isNotEmpty();
    AgentInvoice stored =
        agentInvoiceRepository.findByAgentIdAndBillingMonth(agent.id(), BillingMonth.current()).orElseThrow();
    assertThat(agentInvoice.get("id").asText()).isEqualTo(stored.getId().toString());
    assertThat(agentInvoice.get("totalAmount").decimalValue()).isNotNull();
  }

  @Test
  void aMonthTheAgentHasNotSentIsEmpty() throws Exception {
    NewAgent agent = newAgent("Unsent Agent");

    mockMvc
        .perform(get(monthPath(agent.id(), "2025-01")).header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientInvoices.length()").value(0))
        .andExpect(jsonPath("$.monthSentAt").doesNotExist())
        .andExpect(jsonPath("$.agentInvoice").doesNotExist());
  }

  // --- Refusals -------------------------------------------------------------------------------

  @Test
  void anUnknownOrOtherTenantAgentIsNotFoundOnAllThreeReads() throws Exception {
    managerToken = managerToken();
    UUID otherTenantAgent =
        otherTenantFixture.agentLoginInAnotherTenant("other-" + UUID.randomUUID(), "Passw0rd!x").agentId();

    for (UUID agentId : new UUID[] {UUID.randomUUID(), otherTenantAgent}) {
      mockMvc
          .perform(get("/api/review-queue/agents/" + agentId + "/months").header("Authorization", "Bearer " + managerToken))
          .andExpect(status().isNotFound());
      mockMvc
          .perform(get(monthPath(agentId, currentMonth())).header("Authorization", "Bearer " + managerToken))
          .andExpect(status().isNotFound());
    }
  }

  @Test
  void aMalformedMonthIsABadRequest() throws Exception {
    NewAgent agent = newAgent("Malformed Agent");

    mockMvc
        .perform(get(monthPath(agent.id(), "2026-13")).header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anAgentAndATesterAreForbiddenOnAllThreeReads() throws Exception {
    NewAgent agent = newAgent("Forbidden Agent");
    String[] paths = {
      "/api/review-queue/agents",
      "/api/review-queue/agents/" + agent.id() + "/months",
      monthPath(agent.id(), currentMonth())
    };

    for (String token : new String[] {agent.token(), testerToken()}) {
      for (String path : paths) {
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
      }
    }
  }

  @Test
  void theReadsCreateAndChangeNoInvoice() throws Exception {
    NewAgent agent = newAgent("Read Only Agent");
    contract(agent, "Read Only Client");
    sendTheMonth(mockMvc, agent.token());
    long before = clientInvoiceRepository.count();
    long agentInvoicesBefore = agentInvoiceRepository.count();

    agentsList();
    read(managerToken, "/api/review-queue/agents/" + agent.id() + "/months");
    read(managerToken, monthPath(agent.id(), currentMonth()));
    read(managerToken, monthPath(agent.id(), "2020-02"));

    assertThat(clientInvoiceRepository.count()).isEqualTo(before);
    assertThat(agentInvoiceRepository.count()).isEqualTo(agentInvoicesBefore);
  }
}
