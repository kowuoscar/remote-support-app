package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceLine;
import com.remotesupport.backend.domain.ClientInvoiceLineKind;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.domain.Fee;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardStatus;
import com.remotesupport.backend.repository.ClientInvoiceLineRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The Agent's edit of a draft Client Invoice's lines (edit-client-invoice-lines spec, "Backend:
 * editing a line"; agent-edits-a-client-invoice-line ticket), through the real HTTP seam. Nothing
 * can send an invoice back yet, so the draft-with-stored-lines shape is reached by a fixture that
 * sets a sent invoice back to {@code DRAFT}, as {@link ClientInvoiceStoredLinesApiTest} does.
 */
@Import(OtherTenantFixture.class)
class ClientInvoiceLineEditApiTest extends IntegrationTest {

  @Autowired private ClientInvoiceRepository clientInvoiceRepository;
  @Autowired private ClientInvoiceLineRepository clientInvoiceLineRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private FeeRepository feeRepository;
  @Autowired private SimCardRepository simCardRepository;
  @Autowired private OtherTenantFixture otherTenantFixture;

  private String managerToken;
  private String agentToken;
  private UUID clientId;
  private UUID contractId;

  private void fixture(String clientName) throws Exception {
    managerToken = managerToken();
    agentToken = agentToken();
    clientId = createClient(managerToken, clientName);
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
  }

  private UUID addPostpaidSim(String number, String fee) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/sim-cards")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(postpaidSimCardJson(managerToken, contractId, number, fee)))
            .andExpect(status().isCreated())
            .andReturn();
    return idOf(result);
  }

  private UUID logFee(String amount) throws Exception {
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "tester-" + UUID.randomUUID() + "@example.com");
    MvcResult request =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/requests")
                    .header("Authorization", "Bearer " + testerToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"type":"OTHER","description":"Screen replacement"}
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    MvcResult fee =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/fees")
                    .header("Authorization", "Bearer " + agentToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"requestId":"%s","feeType":"OTHER","amount":%s}
                        """
                            .formatted(idOf(request), amount)))
            .andExpect(status().isCreated())
            .andReturn();
    return idOf(fee);
  }

  private UUID idOf(MvcResult result) throws Exception {
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private ResultActions editAs(String token, UUID onContract, String body) throws Exception {
    return mockMvc.perform(
        put("/api/contracts/" + onContract + "/client-invoice/lines")
            .header("Authorization", "Bearer " + token)
            .contentType(APPLICATION_JSON)
            .content(body));
  }

  private ResultActions edit(String kind, UUID sourceId, String amount) throws Exception {
    return editAs(agentToken, contractId, body(kind, sourceId, amount));
  }

  private static String body(String kind, UUID sourceId, String amount) {
    return """
        {"kind":"%s","sourceId":%s,"amount":%s}
        """
        .formatted(kind, sourceId == null ? "null" : "\"" + sourceId + "\"", amount);
  }

  private JsonNode json(ResultActions actions) throws Exception {
    return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
  }

  private JsonNode agentRead() throws Exception {
    return json(
        mockMvc
            .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk()));
  }

  private UUID send() throws Exception {
    return UUID.fromString(
        json(
                mockMvc
                    .perform(
                        post("/api/contracts/" + contractId + "/client-invoice/send")
                            .header("Authorization", "Bearer " + agentToken))
                    .andExpect(status().isOk()))
            .get("id")
            .asText());
  }

  private void setBackToDraft(UUID invoiceId) {
    ClientInvoice invoice = clientInvoiceRepository.findById(invoiceId).orElseThrow();
    invoice.setStatus(ClientInvoiceStatus.DRAFT);
    invoice.setSentAt(null);
    clientInvoiceRepository.saveAndFlush(invoice);
  }

  private static JsonNode lineOf(JsonNode invoice, String array, String idField, UUID id) {
    for (JsonNode line : invoice.get(array)) {
      if (line.get(idField).asText().equals(id.toString())) {
        return line;
      }
    }
    throw new AssertionError("no line " + id + " in " + array);
  }

  private static JsonNode simLine(JsonNode invoice, UUID simId) {
    return lineOf(invoice, "basePostpaidSims", "simCardId", simId);
  }

  private static JsonNode feeLine(JsonNode invoice, UUID feeId) {
    return lineOf(invoice, "feeLines", "id", feeId);
  }

  private static void assertTotals(JsonNode invoice, String base, String fees, String total) {
    BigDecimal feeSum = BigDecimal.ZERO;
    for (JsonNode fee : invoice.get("feeLines")) {
      feeSum = feeSum.add(fee.get("amount").decimalValue());
    }
    assertThat(invoice.get("baseAmount").decimalValue()).isEqualByComparingTo(base);
    assertThat(feeSum).isEqualByComparingTo(fees);
    assertThat(invoice.get("totalAmount").decimalValue()).isEqualByComparingTo(total);
  }

  private static void assertLine(JsonNode line, String amount, String computed, boolean edited) {
    assertThat(line.get("amount").decimalValue()).isEqualByComparingTo(amount);
    assertThat(line.get("computedAmount").decimalValue()).isEqualByComparingTo(computed);
    assertThat(line.get("edited").asBoolean()).isEqualTo(edited);
  }

  @Test
  void editSimLineAndFeeLineRecomputesTotalsAndMarksEdited() throws Exception {
    fixture("Line Edit Totals");
    UUID sim = addPostpaidSim("+1-555-0400", "18.00");
    UUID fee = logFee("5.00");

    json(edit("POSTPAID_SIM", sim, "\"20.00\"").andExpect(status().isOk()));
    JsonNode invoice = json(edit("FEE", fee, "7.50").andExpect(status().isOk()));

    assertLine(simLine(invoice, sim), "20.00", "18.00", true);
    assertThat(simLine(invoice, sim).get("monthlyFeeAmount").decimalValue()).isEqualByComparingTo("18.00");
    assertLine(feeLine(invoice, fee), "7.50", "5.00", true);
    assertTotals(invoice, "20.00", "7.50", "27.50");
    assertTotals(agentRead(), "20.00", "7.50", "27.50");
  }

  @Test
  void feeListIsUnchangedAfterAnEdit() throws Exception {
    fixture("Line Edit Fee List");
    UUID fee = logFee("5.00");

    edit("FEE", fee, "9.00").andExpect(status().isOk());

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/fees").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(fee.toString()))
        .andExpect(jsonPath("$[0].amount").value(5.00));
  }

  @Test
  void newSimAndNewFeeAppearAndEditedLineKeepsItsAmountWhileUntouchedLineFollows() throws Exception {
    fixture("Line Edit Follow");
    UUID editedSim = addPostpaidSim("+1-555-0401", "18.00");
    UUID editedFee = logFee("5.00");
    edit("POSTPAID_SIM", editedSim, "20.00").andExpect(status().isOk());
    edit("FEE", editedFee, "6.00").andExpect(status().isOk());

    UUID newSim = addPostpaidSim("+1-555-0402", "7.00");
    UUID newFee = logFee("2.50");

    JsonNode invoice = agentRead();
    assertLine(simLine(invoice, editedSim), "20.00", "18.00", true);
    assertLine(simLine(invoice, newSim), "7.00", "7.00", false);
    assertLine(feeLine(invoice, editedFee), "6.00", "5.00", true);
    assertLine(feeLine(invoice, newFee), "2.50", "2.50", false);
    assertTotals(invoice, "27.00", "8.50", "35.50");
  }

  @Test
  void resetBySavingTheComputedAmountClearsEdited() throws Exception {
    fixture("Line Edit Reset");
    UUID sim = addPostpaidSim("+1-555-0403", "18.00");
    UUID fee = logFee("5.00");
    edit("POSTPAID_SIM", sim, "20.00").andExpect(status().isOk());
    edit("FEE", fee, "7.00").andExpect(status().isOk());

    edit("POSTPAID_SIM", sim, "18.00").andExpect(status().isOk());
    JsonNode invoice = json(edit("FEE", fee, "5").andExpect(status().isOk()));

    assertLine(simLine(invoice, sim), "18.00", "18.00", false);
    assertLine(feeLine(invoice, fee), "5.00", "5.00", false);
    assertTotals(invoice, "18.00", "5.00", "23.00");
    assertThat(clientInvoiceLineRepository.count())
        .as("a never-sent draft's reset removes the override row")
        .isZero();
  }

  @Test
  void zeroIsAccepted() throws Exception {
    fixture("Line Edit Zero");
    UUID sim = addPostpaidSim("+1-555-0404", "18.00");
    UUID fee = logFee("5.00");

    edit("POSTPAID_SIM", sim, "0").andExpect(status().isOk());
    JsonNode invoice = json(edit("FEE", fee, "0.00").andExpect(status().isOk()));

    assertLine(simLine(invoice, sim), "0", "18.00", true);
    assertTotals(invoice, "0", "0", "0");
  }

  @Test
  void negativeBlankMissingAndThreeDecimalAmountsGet400WithAMessage() throws Exception {
    fixture("Line Edit Invalid");
    UUID sim = addPostpaidSim("+1-555-0405", "18.00");

    for (String amount : List.of("-1", "-0.01", "\"\"", "null", "10.123", "\"10.123\"")) {
      edit("POSTPAID_SIM", sim, amount)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").isNotEmpty());
    }
    editAs(agentToken, contractId, "{\"kind\":\"POSTPAID_SIM\",\"sourceId\":\"" + sim + "\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").isNotEmpty());
    editAs(agentToken, contractId, "{\"sourceId\":\"" + sim + "\",\"amount\":5}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").isNotEmpty());
    assertTotals(agentRead(), "18.00", "0", "18.00");
  }

  @Test
  void anUnbilledSimOrAnotherMonthsOrContractsFeeOrABaseAmountLineGets404() throws Exception {
    fixture("Line Edit Missing");
    UUID retiredSim = addPostpaidSim("+1-555-0406", "18.00");
    SimCard retired = simCardRepository.findById(retiredSim).orElseThrow();
    retired.setStatus(SimCardStatus.RETIRED);
    simCardRepository.saveAndFlush(retired);
    UUID otherMonthFee = logFee("5.00");
    Fee fee = feeRepository.findById(otherMonthFee).orElseThrow();
    fee.setBillingMonth(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(1));
    feeRepository.saveAndFlush(fee);
    UUID otherContractFee = logFee("3.00");
    Fee movedFee = feeRepository.findById(otherContractFee).orElseThrow();
    movedFee.setContract(
        contractRepository
            .findById(createContract(managerToken, createClient(managerToken, "Other Line Edit"), SEEDED_AGENT_ID))
            .orElseThrow());
    feeRepository.saveAndFlush(movedFee);

    edit("POSTPAID_SIM", retiredSim, "5.00").andExpect(status().isNotFound());
    edit("POSTPAID_SIM", UUID.randomUUID(), "5.00").andExpect(status().isNotFound());
    edit("POSTPAID_SIM", null, "5.00").andExpect(status().isNotFound());
    edit("FEE", otherMonthFee, "5.00").andExpect(status().isNotFound());
    edit("FEE", otherContractFee, "5.00").andExpect(status().isNotFound());
    edit("BASE_AMOUNT", null, "5.00").andExpect(status().isNotFound());
  }

  @Test
  void editAfterSendGets409() throws Exception {
    fixture("Line Edit After Send");
    UUID sim = addPostpaidSim("+1-555-0407", "18.00");
    UUID fee = logFee("5.00");
    UUID invoiceId = send();

    edit("POSTPAID_SIM", sim, "20.00").andExpect(status().isConflict());
    edit("FEE", fee, "6.00").andExpect(status().isConflict());
    mockMvc
        .perform(post("/api/client-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());
    edit("FEE", fee, "6.00").andExpect(status().isConflict());
    assertTotals(agentRead(), "18.00", "5.00", "23.00");
  }

  @Test
  void managerTesterAndAnotherAgentGet403() throws Exception {
    fixture("Line Edit Forbidden");
    UUID sim = addPostpaidSim("+1-555-0408", "18.00");
    String testerToken = createTesterAndLogin(managerToken, clientId, "tester-" + UUID.randomUUID() + "@example.com");
    UUID otherAgentId = createAgent(managerToken, "Other Agent", Country.PHILIPPINES);
    UUID othersContract = createContract(managerToken, createClient(managerToken, "Others Client"), otherAgentId);

    editAs(managerToken, contractId, body("POSTPAID_SIM", sim, "20.00")).andExpect(status().isForbidden());
    editAs(testerToken, contractId, body("POSTPAID_SIM", sim, "20.00")).andExpect(status().isForbidden());
    editAs(agentToken, othersContract, body("POSTPAID_SIM", sim, "20.00")).andExpect(status().isForbidden());
    assertTotals(agentRead(), "18.00", "0", "18.00");
  }

  @Test
  void anotherTenantsContractGets404() throws Exception {
    fixture("Line Edit Tenant");
    UUID otherInvoice = otherTenantFixture.sentClientInvoiceInAnotherTenant();
    UUID otherContract = clientInvoiceRepository.findById(otherInvoice).orElseThrow().getContract().getId();

    editAs(agentToken, otherContract, body("POSTPAID_SIM", UUID.randomUUID(), "5.00")).andExpect(status().isNotFound());
  }

  @Test
  void managerByIdReadOfASentInvoiceServesPerSimLinesWithAmountComputedAndEdited() throws Exception {
    fixture("Line Edit Manager Read");
    UUID editedSim = addPostpaidSim("+1-555-0409", "18.00");
    UUID plainSim = addPostpaidSim("+1-555-0410", "7.00");
    UUID fee = logFee("5.00");
    edit("POSTPAID_SIM", editedSim, "31.40").andExpect(status().isOk());
    UUID invoiceId = send();
    logFee("9.00");
    addPostpaidSim("+1-555-0411", "40.00");

    JsonNode invoice =
        json(
            mockMvc
                .perform(get("/api/client-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk()));

    assertThat(invoice.get("basePostpaidSims")).hasSize(2);
    assertLine(simLine(invoice, editedSim), "31.40", "18.00", true);
    assertLine(simLine(invoice, plainSim), "7.00", "7.00", false);
    assertLine(feeLine(invoice, fee), "5.00", "5.00", false);
    assertTotals(invoice, "38.40", "5.00", "43.40");
  }

  @Test
  void managerByIdReadOfALegacyBaseAmountInvoiceHasNoPerSimLines() throws Exception {
    fixture("Line Edit Legacy");
    Contract contract = contractRepository.findById(contractId).orElseThrow();
    ClientInvoice invoice = new ClientInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setTenant(contract.getTenant());
    invoice.setContract(contract);
    invoice.setBillingMonth(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1));
    invoice.setStatus(ClientInvoiceStatus.SENT);
    invoice.setCurrency(contract.getCurrency());
    invoice.setCreatedAt(Instant.now());
    invoice.setSentAt(Instant.now());
    invoice.setLinesStored(true);
    clientInvoiceRepository.saveAndFlush(invoice);
    ClientInvoiceLine base = new ClientInvoiceLine();
    base.setId(UUID.randomUUID());
    base.setTenant(contract.getTenant());
    base.setClientInvoice(invoice);
    base.setKind(ClientInvoiceLineKind.BASE_AMOUNT);
    base.setAmount(new BigDecimal("30.00"));
    base.setComputedAmount(new BigDecimal("30.00"));
    base.setCreatedAt(Instant.now());
    clientInvoiceLineRepository.saveAndFlush(base);

    mockMvc
        .perform(get("/api/client-invoices/" + invoice.getId()).header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.basePostpaidSims").doesNotExist())
        .andExpect(jsonPath("$.baseAmount").value(30.00));
  }

  @Test
  void testerReadHasNullComputedAndEditedAndTheSameBilledAmounts() throws Exception {
    fixture("Line Edit Tester Read");
    UUID sim = addPostpaidSim("+1-555-0412", "18.00");
    UUID fee = logFee("5.00");
    edit("POSTPAID_SIM", sim, "20.00").andExpect(status().isOk());
    edit("FEE", fee, "6.00").andExpect(status().isOk());
    send();
    String testerToken = createTesterAndLogin(managerToken, clientId, "reader-" + UUID.randomUUID() + "@example.com");

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.basePostpaidSims[0].amount").value(20.00))
        .andExpect(jsonPath("$.basePostpaidSims[0].computedAmount").value(nullValue()))
        .andExpect(jsonPath("$.basePostpaidSims[0].edited").value(nullValue()))
        .andExpect(jsonPath("$.feeLines[0].amount").value(6.00))
        .andExpect(jsonPath("$.feeLines[0].computedAmount").value(nullValue()))
        .andExpect(jsonPath("$.feeLines[0].edited").value(nullValue()))
        .andExpect(jsonPath("$.baseAmount").value(20.00))
        .andExpect(jsonPath("$.totalAmount").value(26.00));
  }

  @Test
  void reviewQueueTotalEqualsTheEditedTotal() throws Exception {
    fixture("Line Edit Queue");
    UUID sim = addPostpaidSim("+1-555-0413", "18.00");
    UUID fee = logFee("5.00");
    edit("POSTPAID_SIM", sim, "31.40").andExpect(status().isOk());
    edit("FEE", fee, "40.00").andExpect(status().isOk());
    UUID invoiceId = send();

    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + invoiceId + "')].totalAmount").value(71.40));
  }

  @Test
  void sentBackDraftEditsASentLineAndTheLateFeeLineAndResetKeepsTheRow() throws Exception {
    fixture("Line Edit Sent Back");
    UUID sim = addPostpaidSim("+1-555-0414", "18.00");
    UUID sentFee = logFee("5.00");
    edit("POSTPAID_SIM", sim, "20.00").andExpect(status().isOk());
    UUID invoiceId = send();
    setBackToDraft(invoiceId);
    UUID lateFee = logFee("3.25");
    addPostpaidSim("+1-555-0415", "40.00");

    JsonNode read = agentRead();
    assertThat(read.get("basePostpaidSims")).hasSize(1);
    assertLine(simLine(read, sim), "20.00", "18.00", true);
    assertLine(feeLine(read, sentFee), "5.00", "5.00", false);
    assertLine(feeLine(read, lateFee), "3.25", "3.25", false);
    assertTotals(read, "20.00", "8.25", "28.25");

    JsonNode afterSimEdit = json(edit("POSTPAID_SIM", sim, "25.00").andExpect(status().isOk()));
    assertLine(simLine(afterSimEdit, sim), "25.00", "18.00", true);
    assertThat(clientInvoiceLineRepository.findByClientInvoiceId(invoiceId)).hasSize(2);

    JsonNode afterLateEdit = json(edit("FEE", lateFee, "9.00").andExpect(status().isOk()));
    assertLine(feeLine(afterLateEdit, lateFee), "9.00", "3.25", true);
    assertThat(clientInvoiceLineRepository.findByClientInvoiceId(invoiceId)).hasSize(3);

    JsonNode afterReset = json(edit("FEE", lateFee, "3.25").andExpect(status().isOk()));
    assertLine(feeLine(afterReset, lateFee), "3.25", "3.25", false);
    assertThat(clientInvoiceLineRepository.findByClientInvoiceId(invoiceId))
        .as("a reset on a draft with stored lines keeps the row")
        .hasSize(3);

    JsonNode afterSentLineReset = json(edit("POSTPAID_SIM", sim, "18.00").andExpect(status().isOk()));
    assertLine(simLine(afterSentLineReset, sim), "18.00", "18.00", false);
    assertThat(clientInvoiceLineRepository.findByClientInvoiceId(invoiceId)).hasSize(3);
  }

  @Test
  void resendAfterEditsStoresTheLateFeeLineWithoutAConstraintViolation() throws Exception {
    fixture("Line Edit Resend");
    UUID sim = addPostpaidSim("+1-555-0416", "18.00");
    logFee("5.00");
    UUID invoiceId = send();
    setBackToDraft(invoiceId);
    UUID lateFee = logFee("3.25");
    edit("FEE", lateFee, "4.00").andExpect(status().isOk());
    edit("POSTPAID_SIM", sim, "21.00").andExpect(status().isOk());

    send();
    logFee("11.00");

    JsonNode read = agentRead();
    assertThat(read.get("status").asText()).isEqualTo("SENT");
    assertLine(feeLine(read, lateFee), "4.00", "3.25", true);
    assertLine(simLine(read, sim), "21.00", "18.00", true);
    assertTotals(read, "21.00", "9.00", "30.00");
    assertThat(clientInvoiceLineRepository.findByClientInvoiceId(invoiceId)).hasSize(3);
  }

  @Test
  void sentBackLegacyInvoiceShowsTheLateFeeAndLeavesTheBaseAmountLineUntouched() throws Exception {
    fixture("Line Edit Legacy Sent Back");
    UUID sentFee = logFee("5.00");
    Contract contract = contractRepository.findById(contractId).orElseThrow();
    ClientInvoice invoice = new ClientInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setTenant(contract.getTenant());
    invoice.setContract(contract);
    invoice.setBillingMonth(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1));
    invoice.setStatus(ClientInvoiceStatus.DRAFT);
    invoice.setCurrency(contract.getCurrency());
    invoice.setCreatedAt(Instant.now());
    invoice.setLinesStored(true);
    clientInvoiceRepository.saveAndFlush(invoice);
    storeLine(invoice, ClientInvoiceLineKind.BASE_AMOUNT, null, "30.00");
    storeLine(invoice, ClientInvoiceLineKind.FEE, feeRepository.findById(sentFee).orElseThrow(), "5.00");
    UUID lateFee = logFee("4.00");

    JsonNode read = agentRead();
    assertThat(read.has("basePostpaidSims")).isFalse();
    assertLine(feeLine(read, lateFee), "4.00", "4.00", false);
    assertTotals(read, "30.00", "9.00", "39.00");
    JsonNode afterEdit = json(edit("BASE_AMOUNT", null, "33.00").andExpect(status().isOk()));
    assertTotals(afterEdit, "33.00", "9.00", "42.00");
  }

  private void storeLine(ClientInvoice invoice, ClientInvoiceLineKind kind, Fee fee, String amount) {
    ClientInvoiceLine line = new ClientInvoiceLine();
    line.setId(UUID.randomUUID());
    line.setTenant(invoice.getTenant());
    line.setClientInvoice(invoice);
    line.setKind(kind);
    line.setFee(fee);
    line.setAmount(new BigDecimal(amount));
    line.setComputedAmount(new BigDecimal(amount));
    line.setCreatedAt(Instant.now());
    clientInvoiceLineRepository.saveAndFlush(line);
  }

  @Test
  void anOverrideForASimThatNoLongerBillsIsKeptWithAZeroComputedAmountAndStaysEditable() throws Exception {
    fixture("Line Edit Unbilled Override");
    UUID sim = addPostpaidSim("+1-555-0417", "18.00");
    UUID keptSim = addPostpaidSim("+1-555-0418", "7.00");
    edit("POSTPAID_SIM", sim, "20.00").andExpect(status().isOk());
    SimCard retired = simCardRepository.findById(sim).orElseThrow();
    retired.setStatus(SimCardStatus.RETIRED);
    retired.setCancellationEffectiveDate(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(1));
    simCardRepository.saveAndFlush(retired);

    JsonNode draft = agentRead();
    assertThat(draft.get("basePostpaidSims")).hasSize(2);
    assertLine(simLine(draft, sim), "20.00", "0", true);
    assertLine(simLine(draft, keptSim), "7.00", "7.00", false);
    assertTotals(draft, "27.00", "0", "27.00");

    UUID invoiceId = send();
    JsonNode sent = agentRead();
    assertThat(sent.get("status").asText()).isEqualTo("SENT");
    assertLine(simLine(sent, sim), "20.00", "0", true);
    assertTotals(sent, "27.00", "0", "27.00");
    assertThat(clientInvoiceLineRepository.findByClientInvoiceId(invoiceId)).hasSize(2);
  }

  @Test
  void anOverrideForASimThatNoLongerBillsCanBeResetAwayOnTheDraft() throws Exception {
    fixture("Line Edit Unbilled Reset");
    UUID sim = addPostpaidSim("+1-555-0419", "18.00");
    edit("POSTPAID_SIM", sim, "20.00").andExpect(status().isOk());
    SimCard retired = simCardRepository.findById(sim).orElseThrow();
    retired.setStatus(SimCardStatus.RETIRED);
    retired.setCancellationEffectiveDate(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).minusMonths(1));
    simCardRepository.saveAndFlush(retired);

    JsonNode afterZero = json(edit("POSTPAID_SIM", sim, "0").andExpect(status().isOk()));

    assertThat(afterZero.get("basePostpaidSims")).isEmpty();
    assertTotals(afterZero, "0", "0", "0");
    assertThat(clientInvoiceLineRepository.count()).isZero();
  }

  @Test
  void auditLineNamesActorTenantInvoiceLineOldAndNewAmount() throws Exception {
    fixture("Line Edit Audit");
    UUID sim = addPostpaidSim("+1-555-0420", "18.00");
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      JsonNode invoice = json(edit("POSTPAID_SIM", sim, "20.00").andExpect(status().isOk()));

      List<String> logged =
          appender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .filter(m -> m.contains("action=CLIENT_INVOICE_LINE_EDITED"))
              .toList();
      assertThat(logged).hasSize(1);
      assertThat(logged.get(0))
          .contains("entityId=" + invoice.get("id").asText())
          .contains("kind=POSTPAID_SIM")
          .contains("sourceId=" + sim)
          .contains("oldAmount=18.00")
          .contains("newAmount=20.00")
          .contains("actorUserId=")
          .contains("tenantId=" + SEEDED_TENANT_ID);
    } finally {
      auditLogger.detachAppender(appender);
    }
  }
}
