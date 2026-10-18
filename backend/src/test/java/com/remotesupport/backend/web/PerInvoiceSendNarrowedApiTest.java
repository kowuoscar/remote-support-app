package com.remotesupport.backend.web;

import static com.remotesupport.backend.support.ClientInvoiceMonthTestSupport.sendTheMonth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.domain.BillingMonth;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.support.IntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The per-invoice send, narrowed (agent-sends-the-month spec, "The per-invoice send, narrowed"):
 * a never-sent invoice cannot be sent on its own while its Agent's month is not sent, on both
 * per-invoice routes; a resend and a late Contract's first send still go through. Through the real
 * HTTP seam with real Postgres. Every case builds a fresh Agent.
 */
class PerInvoiceSendNarrowedApiTest extends IntegrationTest {

  @Autowired private ClientInvoiceRepository clientInvoiceRepository;

  private record NewAgent(UUID id, String token) {}

  private NewAgent newAgent() throws Exception {
    String email = "narrowed-agent-" + UUID.randomUUID() + "@agents.example";
    MvcResult created =
        mockMvc
            .perform(
                post("/api/agents")
                    .header("Authorization", "Bearer " + managerToken())
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Narrowed Agent","country":"UNITED_STATES","salaryAmount":2000.00,"username":"%s"}
                        """
                            .formatted(email)))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
    return new NewAgent(UUID.fromString(body.get("id").asText()), loginAs(email, body.get("password").asText()));
  }

  private UUID contract(NewAgent agent, UUID clientId, int postpaidSims) throws Exception {
    UUID contractId = createContract(managerToken(), clientId, agent.id());
    for (int i = 0; i < postpaidSims; i++) {
      addUniquePostpaidSim(managerToken(), contractId);
    }
    return contractId;
  }

  private UUID contract(NewAgent agent, int postpaidSims) throws Exception {
    return contract(agent, createClient(managerToken(), "Narrowed Client " + UUID.randomUUID()), postpaidSims);
  }

  private UUID openDraft(NewAgent agent, UUID contractId) throws Exception {
    MvcResult opened =
        mockMvc
            .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agent.token()))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(opened.getResponse().getContentAsString()).get("id").asText());
  }

  private ResultActions sendById(NewAgent agent, UUID invoiceId) throws Exception {
    return mockMvc.perform(post("/api/client-invoices/" + invoiceId + "/send").header("Authorization", "Bearer " + agent.token()));
  }

  private ResultActions sendByContract(NewAgent agent, UUID contractId) throws Exception {
    return mockMvc.perform(
        post("/api/contracts/" + contractId + "/client-invoice/send").header("Authorization", "Bearer " + agent.token()));
  }

  private void sendBack(UUID invoiceId) throws Exception {
    mockMvc
        .perform(
            post("/api/client-invoices/" + invoiceId + "/send-back")
                .header("Authorization", "Bearer " + managerToken())
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"Please check the second line\"}"))
        .andExpect(status().isOk());
  }

  private ClientInvoice invoiceOf(UUID contractId) {
    return clientInvoiceRepository.findByContractIdAndBillingMonth(contractId, BillingMonth.current()).orElseThrow();
  }

  @ParameterizedTest
  @ValueSource(strings = {"byId", "byContract"})
  void aNeverSentInvoiceIsMonthNotSentOnEitherRouteWithExactlyCodeAndMessage(String route) throws Exception {
    NewAgent agent = newAgent();
    UUID contractId = contract(agent, 0);
    UUID invoiceId = openDraft(agent, contractId);

    MvcResult refused =
        ("byId".equals(route) ? sendById(agent, invoiceId) : sendByContract(agent, contractId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("MONTH_NOT_SENT"))
            .andReturn();

    JsonNode body = objectMapper.readTree(refused.getResponse().getContentAsString());
    assertThat(body.fieldNames()).toIterable().containsExactlyInAnyOrder("code", "message");
    assertThat(invoiceOf(contractId).getStatus()).isEqualTo(ClientInvoiceStatus.DRAFT);
  }

  @Test
  void theMonthRuleIsCheckedBeforeTheCarrierGateOnBothRoutes() throws Exception {
    NewAgent agent = newAgent();
    UUID contractId = contract(agent, 1);
    UUID invoiceId = openDraft(agent, contractId);

    sendById(agent, invoiceId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MONTH_NOT_SENT"));
    sendByContract(agent, contractId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MONTH_NOT_SENT"));
  }

  @Test
  void aSentBackInvoiceResendsAloneAfterTheMonthSendLeavingTheOthersAndTheMonthDateAlone() throws Exception {
    NewAgent agent = newAgent();
    UUID first = contract(agent, 0);
    UUID second = contract(agent, 0);
    sendTheMonth(mockMvc, agent.token());
    Instant monthSentAt = invoiceOf(first).getFirstSentAt();
    sendBack(invoiceOf(first).getId());

    sendByContract(agent, first).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"));

    assertThat(invoiceOf(second).getStatus()).isEqualTo(ClientInvoiceStatus.SENT);
    assertThat(invoiceOf(second).getFirstSentAt()).isEqualTo(monthSentAt);
    assertThat(invoiceOf(first).getFirstSentAt()).isEqualTo(monthSentAt);
  }

  @Test
  void aContractCreatedAfterTheMonthSendSendsAloneAndItsMissingFileIsCarrierInvoicesMissingNotMonthNotSent() throws Exception {
    NewAgent agent = newAgent();
    contract(agent, 0);
    sendTheMonth(mockMvc, agent.token());
    UUID lateWithFileMissing = contract(agent, 1);
    UUID lateEmpty = contract(agent, 0);

    sendByContract(agent, lateWithFileMissing)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CARRIER_INVOICES_MISSING"));
    sendByContract(agent, lateEmpty).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"));
    assertThat(invoiceOf(lateEmpty).getFirstSentAt()).isNotNull();
  }

  @Test
  void aMonthHoldingAnInvoiceSentBeforeThisFeatureReadsSentSoARemainingDraftSendsAlone() throws Exception {
    NewAgent agent = newAgent();
    UUID earlier = contract(agent, 0);
    UUID remaining = contract(agent, 0);
    openDraft(agent, earlier);
    ClientInvoice old = invoiceOf(earlier);
    Instant at = Instant.now().minusSeconds(3600);
    old.setStatus(ClientInvoiceStatus.SENT);
    old.setSentAt(at);
    old.setFirstSentAt(at);
    old.setLinesStored(true);
    clientInvoiceRepository.saveAndFlush(old);

    sendByContract(agent, remaining).andExpect(status().isOk());
  }

  @Test
  void aTesterSeesASentInvoiceOfTheMonthAndNothingOfADraft() throws Exception {
    NewAgent agent = newAgent();
    UUID clientId = createClient(managerToken(), "Narrowed Tester Client " + UUID.randomUUID());
    String tester = createTesterAndLogin(managerToken(), clientId, "narrowed-tester-" + UUID.randomUUID() + "@example.com");
    UUID contractId = contract(agent, clientId, 0);
    openDraft(agent, contractId);

    mockMvc
        .perform(get("/api/me/client/latest-client-invoices").header("Authorization", "Bearer " + tester))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    sendTheMonth(mockMvc, agent.token());

    mockMvc
        .perform(get("/api/me/client/latest-client-invoices").header("Authorization", "Bearer " + tester))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].contractId").value(contractId.toString()))
        .andExpect(jsonPath("$[0].status").value("SENT"));
  }

  @Test
  void theReviewQueueListsTheMonthsInvoicesAsSeparateRowsEachApprovableAndSendableBackOnItsOwn() throws Exception {
    NewAgent agent = newAgent();
    UUID a = contract(agent, 0);
    UUID b = contract(agent, 0);
    sendTheMonth(mockMvc, agent.token());
    UUID invoiceA = invoiceOf(a).getId();
    UUID invoiceB = invoiceOf(b).getId();

    MvcResult queue =
        mockMvc
            .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken()))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode rows = objectMapper.readTree(queue.getResponse().getContentAsString());
    long rowsOfTheMonth = 0;
    for (JsonNode row : rows) {
      String id = row.get("id").asText();
      if (id.equals(invoiceA.toString()) || id.equals(invoiceB.toString())) {
        rowsOfTheMonth++;
        assertThat(row.get("kind").asText()).isEqualTo("CLIENT_INVOICE");
        assertThat(row.get("status").asText()).isEqualTo("SENT");
      }
    }
    assertThat(rowsOfTheMonth).isEqualTo(2);

    mockMvc
        .perform(post("/api/client-invoices/" + invoiceA + "/approve").header("Authorization", "Bearer " + managerToken()))
        .andExpect(status().isOk());
    sendBack(invoiceB);
    assertThat(invoiceOf(a).getStatus()).isEqualTo(ClientInvoiceStatus.APPROVED);
    assertThat(invoiceOf(b).getStatus()).isEqualTo(ClientInvoiceStatus.DRAFT);
  }
}
