package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A Tester's own Client and the latest sent or approved Client Invoice per Contract
 * ({@code GET /api/me/client}, {@code GET /api/me/client/latest-client-invoices}). Both routes take
 * no id: the Client is resolved from the caller, and neither route ever writes.
 */
@Import(OtherTenantFixture.class)
class MeClientApiTest extends IntegrationTest {

  private static final String LATEST = "/api/me/client/latest-client-invoices";

  @Autowired private ClientInvoiceRepository clientInvoiceRepository;
  @Autowired private OtherTenantFixture otherTenantFixture;

  private static LocalDate currentMonth() {
    return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
  }

  private record TesterOfClient(UUID clientId, String token) {}

  private TesterOfClient testerOfNewClient(String clientName) throws Exception {
    UUID clientId = createClient(managerToken(), clientName);
    String token =
        createTesterAndLogin(
            managerToken(), clientId, "me-client-" + UUID.randomUUID() + "@example.com");
    return new TesterOfClient(clientId, token);
  }

  private UUID newContract(UUID clientId) throws Exception {
    return createContract(managerToken(), clientId, SEEDED_AGENT_ID);
  }

  /** Opens (creating) the current month's draft for the Contract and returns its id. */
  private UUID openDraft(UUID contractId) throws Exception {
    return idOf(
        mockMvc
            .perform(
                get("/api/contracts/" + contractId + "/client-invoice")
                    .header("Authorization", "Bearer " + agentToken()))
            .andExpect(status().isOk())
            .andReturn());
  }

  private UUID sendCurrent(UUID contractId) throws Exception {
    openDraft(contractId);
    return idOf(
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/client-invoice/send")
                    .header("Authorization", "Bearer " + agentToken()))
            .andExpect(status().isOk())
            .andReturn());
  }

  private void approve(UUID invoiceId) throws Exception {
    mockMvc
        .perform(
            post("/api/client-invoices/" + invoiceId + "/approve")
                .header("Authorization", "Bearer " + managerToken()))
        .andExpect(status().isOk());
  }

  private UUID idOf(MvcResult result) throws Exception {
    return UUID.fromString(
        objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  /** Moves an invoice into an earlier billing month, standing in for a month rollover. */
  private void moveToPastMonth(UUID invoiceId, int monthsBack) {
    ClientInvoice invoice = clientInvoiceRepository.findById(invoiceId).orElseThrow();
    invoice.setBillingMonth(invoice.getBillingMonth().minusMonths(monthsBack));
    clientInvoiceRepository.saveAndFlush(invoice);
  }

  /** A sent invoice for {@code monthsBack} months ago (0 = this month), optionally approved. */
  private UUID sentInvoice(UUID contractId, int monthsBack, boolean approved) throws Exception {
    UUID id = sendCurrent(contractId);
    if (approved) {
      approve(id);
    }
    if (monthsBack > 0) {
      moveToPastMonth(id, monthsBack);
    }
    return id;
  }

  private JsonNode latest(String token) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(LATEST).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode entryFor(JsonNode array, UUID contractId) {
    for (JsonNode entry : array) {
      if (entry.get("contractId").asText().equals(contractId.toString())) {
        return entry;
      }
    }
    return null;
  }

  private void logFee(String testerToken, UUID contractId, String amount) throws Exception {
    MvcResult created =
        postRequest(contractId, testerToken, "{\"type\":\"OTHER\",\"description\":\"Screen\"}")
            .andExpect(status().isCreated())
            .andReturn();
    UUID requestId = idOf(created);
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"requestId\":\"%s\",\"feeType\":\"OTHER\",\"amount\":%s}"
                        .formatted(requestId, amount)))
        .andExpect(status().isCreated());
  }

  // --- GET /api/me/client ---------------------------------------------------------------------

  @Test
  void testerGetsOwnClientName() throws Exception {
    TesterOfClient tester = testerOfNewClient("Own Client Co");
    newContract(tester.clientId());

    mockMvc
        .perform(get("/api/me/client").header("Authorization", "Bearer " + tester.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientId").value(tester.clientId().toString()))
        .andExpect(jsonPath("$.name").value("Own Client Co"));
  }

  @Test
  void testerWithNoContractStillGetsClientName() throws Exception {
    TesterOfClient tester = testerOfNewClient("Contractless Co");

    mockMvc
        .perform(get("/api/me/client").header("Authorization", "Bearer " + tester.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientId").value(tester.clientId().toString()))
        .andExpect(jsonPath("$.name").value("Contractless Co"));
    assertThat(latest(tester.token())).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/me/client", LATEST})
  void managerAgentAndUnlinkedTesterGet404OnBothRoutes(String url) throws Exception {
    for (String token : List.of(managerToken(), agentToken(), testerToken())) {
      mockMvc
          .perform(get(url).header("Authorization", "Bearer " + token))
          .andExpect(status().isNotFound());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/me/client", LATEST})
  void noTokenGets401OnBothRoutes(String url) throws Exception {
    mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
  }

  // --- GET /api/me/client/latest-client-invoices ----------------------------------------------

  @Test
  void latestReturnsCurrentMonthWhenSent() throws Exception {
    TesterOfClient tester = testerOfNewClient("Sent Co");
    UUID contractId = newContract(tester.clientId());
    UUID current = sentInvoice(contractId, 0, false);

    JsonNode entry = entryFor(latest(tester.token()), contractId);

    assertThat(entry).isNotNull();
    assertThat(entry.get("invoiceId").asText()).isEqualTo(current.toString());
    assertThat(entry.get("billingMonth").asText()).isEqualTo(currentMonth().toString());
    assertThat(entry.get("status").asText()).isEqualTo("SENT");
    assertThat(entry.get("currency").asText()).isEqualTo("USD");
    assertThat(entry.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "contractId", "invoiceId", "billingMonth", "status", "currency", "totalAmount");
  }

  @Test
  void latestFallsBackToOlderApprovedWhenCurrentIsDraft() throws Exception {
    TesterOfClient tester = testerOfNewClient("Draft Now Co");
    UUID contractId = newContract(tester.clientId());
    UUID older = sentInvoice(contractId, 1, true);
    openDraft(contractId);

    JsonNode entry = entryFor(latest(tester.token()), contractId);

    assertThat(entry.get("invoiceId").asText()).isEqualTo(older.toString());
    assertThat(entry.get("status").asText()).isEqualTo("APPROVED");
    assertThat(entry.get("billingMonth").asText())
        .isEqualTo(currentMonth().minusMonths(1).toString());
  }

  @Test
  void latestFallsBackWhenCurrentMonthAbsent() throws Exception {
    TesterOfClient tester = testerOfNewClient("Absent Now Co");
    UUID contractId = newContract(tester.clientId());
    sentInvoice(contractId, 3, true);
    UUID newer = sentInvoice(contractId, 2, false);

    JsonNode entry = entryFor(latest(tester.token()), contractId);

    assertThat(entry.get("invoiceId").asText()).isEqualTo(newer.toString());
    assertThat(entry.get("status").asText()).isEqualTo("SENT");
  }

  @Test
  void contractWithOnlyADraftIsAbsent() throws Exception {
    TesterOfClient tester = testerOfNewClient("Draft Only Co");
    UUID contractId = newContract(tester.clientId());
    openDraft(contractId);

    assertThat(latest(tester.token())).isEmpty();
  }

  @Test
  void neverReturnsAnotherClientsContract() throws Exception {
    TesterOfClient mine = testerOfNewClient("Mine Co");
    TesterOfClient other = testerOfNewClient("Not Mine Co");
    UUID myContract = newContract(mine.clientId());
    UUID otherContract = newContract(other.clientId());
    sentInvoice(myContract, 0, false);
    sentInvoice(otherContract, 1, false);

    JsonNode result = latest(mine.token());

    assertThat(result).hasSize(1);
    assertThat(entryFor(result, myContract)).isNotNull();
    assertThat(entryFor(result, otherContract)).isNull();
  }

  @Test
  void neverReturnsOtherTenantContract() throws Exception {
    TesterOfClient mine = testerOfNewClient("Tenant Mine Co");
    UUID myContract = newContract(mine.clientId());
    sentInvoice(myContract, 0, false);
    UUID foreignInvoice = otherTenantFixture.sentClientInvoiceInAnotherTenant();

    JsonNode result = latest(mine.token());

    assertThat(result).hasSize(1);
    for (JsonNode entry : result) {
      assertThat(entry.get("invoiceId").asText()).isNotEqualTo(foreignInvoice.toString());
    }
  }

  @Test
  void totalAmountEqualsTheInvoicesOwnTotal() throws Exception {
    TesterOfClient tester = testerOfNewClient("Total Co");
    UUID contractId = newContract(tester.clientId());
    logFee(tester.token(), contractId, "45.00");
    sentInvoice(contractId, 0, false);

    JsonNode entry = entryFor(latest(tester.token()), contractId);

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/client-invoice")
                .header("Authorization", "Bearer " + tester.token()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.totalAmount").value(entry.get("totalAmount").decimalValue().doubleValue()));
    assertThat(entry.get("totalAmount").decimalValue().doubleValue()).isGreaterThanOrEqualTo(45.00);
  }

  @Test
  void feeLoggedAfterSendingDoesNotMoveTotalAmount() throws Exception {
    TesterOfClient tester = testerOfNewClient("Frozen Co");
    UUID contractId = newContract(tester.clientId());
    sentInvoice(contractId, 0, false);
    double before = entryFor(latest(tester.token()), contractId).get("totalAmount").asDouble();

    logFee(tester.token(), contractId, "77.00");

    double after = entryFor(latest(tester.token()), contractId).get("totalAmount").asDouble();
    assertThat(after).isEqualTo(before);
  }

  @Test
  void callingTheReadCreatesNoClientInvoiceRow() throws Exception {
    TesterOfClient tester = testerOfNewClient("No Write Co");
    newContract(tester.clientId());
    long before = clientInvoiceRepository.count();

    mockMvc
        .perform(get("/api/me/client").header("Authorization", "Bearer " + tester.token()))
        .andExpect(status().isOk());
    latest(tester.token());

    assertThat(clientInvoiceRepository.count()).isEqualTo(before);
  }
}
