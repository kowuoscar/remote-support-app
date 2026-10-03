package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceLine;
import com.remotesupport.backend.domain.ClientInvoiceLineKind;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Fee;
import com.remotesupport.backend.repository.ClientInvoiceFeeSnapshotRepository;
import com.remotesupport.backend.repository.ClientInvoiceLineRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A Client Invoice's own stored lines (edit-client-invoice-lines spec, "The model" and
 * "Prefactoring"; serve-client-invoices-from-stored-lines ticket): what the send freezes and what
 * every read serves from, through the real HTTP seam. Nothing is editable yet, so the
 * draft-with-stored-lines shape (a sent-back invoice) is reached by a fixture that sets a sent
 * invoice back to {@code DRAFT}.
 */
class ClientInvoiceStoredLinesApiTest extends IntegrationTest {

  @Autowired private ClientInvoiceRepository clientInvoiceRepository;
  @Autowired private ClientInvoiceLineRepository clientInvoiceLineRepository;
  @Autowired private ClientInvoiceFeeSnapshotRepository clientInvoiceFeeSnapshotRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private FeeRepository feeRepository;

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

  private void addPostpaidSim(String number, String fee) throws Exception {
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/sim-cards")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(postpaidSimCardJson(managerToken, contractId, number, fee)))
        .andExpect(status().isCreated());
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
    UUID requestId =
        UUID.fromString(objectMapper.readTree(request.getResponse().getContentAsString()).get("id").asText());
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
                            .formatted(requestId, amount)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(fee.getResponse().getContentAsString()).get("id").asText());
  }

  private String agentRead() throws Exception {
    return mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private UUID send() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/client-invoice/send")
                    .header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private void sendExpecting(int httpStatus) throws Exception {
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/client-invoice/send")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().is(httpStatus));
  }

  private void setBackToDraft(UUID invoiceId) {
    ClientInvoice invoice = clientInvoiceRepository.findById(invoiceId).orElseThrow();
    invoice.setStatus(ClientInvoiceStatus.DRAFT);
    invoice.setSentAt(null);
    clientInvoiceRepository.saveAndFlush(invoice);
  }

  /** A pre-change invoice as the V56 backfill leaves it: one BASE_AMOUNT line plus a FEE line per Fee. */
  private UUID legacySentInvoice(String baseAmount, List<UUID> feeIds) {
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
    invoice.setSnapshotBaseAmount(new BigDecimal(baseAmount));
    invoice.setLinesStored(true);
    clientInvoiceRepository.saveAndFlush(invoice);

    storeLine(invoice, ClientInvoiceLineKind.BASE_AMOUNT, null, new BigDecimal(baseAmount));
    for (UUID feeId : feeIds) {
      Fee fee = feeRepository.findById(feeId).orElseThrow();
      storeLine(invoice, ClientInvoiceLineKind.FEE, fee, fee.getAmount());
    }
    clientInvoiceLineRepository.flush();
    return invoice.getId();
  }

  private void storeLine(ClientInvoice invoice, ClientInvoiceLineKind kind, Fee fee, BigDecimal amount) {
    ClientInvoiceLine line = new ClientInvoiceLine();
    line.setId(UUID.randomUUID());
    line.setTenant(invoice.getTenant());
    line.setClientInvoice(invoice);
    line.setKind(kind);
    line.setFee(fee);
    line.setAmount(amount);
    line.setComputedAmount(amount);
    line.setCreatedAt(Instant.now());
    clientInvoiceLineRepository.save(line);
  }

  private void assertTotals(String json, String base, String fees, String total) throws Exception {
    var tree = objectMapper.readTree(json);
    BigDecimal feeSum =
        java.util.stream.StreamSupport.stream(tree.get("feeLines").spliterator(), false)
            .map(n -> n.get("amount").decimalValue())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(tree.get("baseAmount").decimalValue()).isEqualByComparingTo(base);
    assertThat(feeSum).isEqualByComparingTo(fees);
    assertThat(tree.get("totalAmount").decimalValue()).isEqualByComparingTo(total);
  }

  @Test
  void uneditedDraftReadsAsTheComputation() throws Exception {
    fixture("Stored Lines Draft");
    addPostpaidSim("+1-555-0300", "18.00");
    addPostpaidSim("+1-555-0301", "7.00");
    logFee("5.00");
    logFee("2.50");

    String json = agentRead();

    var tree = objectMapper.readTree(json);
    assertThat(tree.get("status").asText()).isEqualTo("DRAFT");
    assertThat(tree.get("basePostpaidSims")).hasSize(2);
    assertThat(tree.get("feeLines")).hasSize(2);
    assertTotals(json, "25.00", "7.50", "32.50");
    assertThat(clientInvoiceLineRepository.count())
        .as("reading a draft never stores a line")
        .isEqualTo(0L);
  }

  @Test
  void sendFreezesLinesAndALaterFeeDoesNotAppear() throws Exception {
    fixture("Stored Lines Send");
    addPostpaidSim("+1-555-0302", "18.00");
    UUID fee = logFee("5.00");
    UUID invoiceId = send();

    ClientInvoice invoice = clientInvoiceRepository.findById(invoiceId).orElseThrow();
    assertThat(invoice.isLinesStored()).isTrue();
    assertThat(invoice.getSnapshotBaseAmount()).as("the old snapshot is no longer written").isNull();
    assertThat(clientInvoiceFeeSnapshotRepository.findByClientInvoiceId(invoiceId)).isEmpty();
    List<ClientInvoiceLine> lines = clientInvoiceLineRepository.findByClientInvoiceId(invoiceId);
    assertThat(lines).extracting(ClientInvoiceLine::getKind)
        .containsExactlyInAnyOrder(ClientInvoiceLineKind.POSTPAID_SIM, ClientInvoiceLineKind.FEE);

    logFee("9.00");

    String agentJson = agentRead();
    assertTotals(agentJson, "18.00", "5.00", "23.00");
    assertThat(objectMapper.readTree(agentJson).get("feeLines").get(0).get("id").asText()).isEqualTo(fee.toString());
    String managerJson =
        mockMvc
            .perform(get("/api/client-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTotals(managerJson, "18.00", "5.00", "23.00");
  }

  @Test
  void sentBackDraftServesSentLinesPlusLateFeeAsPrefilledAndNoNewSimLine() throws Exception {
    fixture("Stored Lines Sent Back");
    addPostpaidSim("+1-555-0303", "18.00");
    UUID sentFee = logFee("5.00");
    UUID invoiceId = send();
    setBackToDraft(invoiceId);
    UUID lateFee = logFee("3.25");
    addPostpaidSim("+1-555-0304", "40.00");

    String json = agentRead();

    var tree = objectMapper.readTree(json);
    assertThat(tree.get("status").asText()).isEqualTo("DRAFT");
    assertThat(tree.get("basePostpaidSims")).hasSize(1);
    assertThat(tree.get("feeLines")).hasSize(2);
    assertThat(tree.get("feeLines").get(0).get("id").asText()).isEqualTo(sentFee.toString());
    assertThat(tree.get("feeLines").get(1).get("id").asText()).isEqualTo(lateFee.toString());
    assertTotals(json, "18.00", "8.25", "26.25");
    assertThat(clientInvoiceLineRepository.findByClientInvoiceId(invoiceId))
        .as("the late Fee is pre-filled, not stored until sent")
        .hasSize(2);
  }

  @Test
  void sentBackLegacyInvoiceKeepsBaseAmountLineAndGainsLateFee() throws Exception {
    fixture("Stored Lines Legacy Sent Back");
    UUID sentFee = logFee("5.00");
    UUID invoiceId = legacySentInvoice("30.00", List.of(sentFee));
    setBackToDraft(invoiceId);
    logFee("4.00");

    String json = agentRead();

    var tree = objectMapper.readTree(json);
    assertThat(tree.get("status").asText()).isEqualTo("DRAFT");
    assertThat(tree.has("basePostpaidSims")).as("a BASE_AMOUNT invoice has no per-SIM breakdown").isFalse();
    assertThat(tree.get("feeLines")).hasSize(2);
    assertTotals(json, "30.00", "9.00", "39.00");
    assertThat(clientInvoiceLineRepository.findByClientInvoiceId(invoiceId))
        .filteredOn(l -> l.getKind() == ClientInvoiceLineKind.BASE_AMOUNT)
        .hasSize(1);
  }

  @Test
  void resendStoresTheLateFeeLineAndLeavesStoredRowsAlone() throws Exception {
    fixture("Stored Lines Resend");
    addPostpaidSim("+1-555-0305", "18.00");
    logFee("5.00");
    UUID invoiceId = send();
    List<ClientInvoiceLine> before = clientInvoiceLineRepository.findByClientInvoiceId(invoiceId);
    setBackToDraft(invoiceId);
    UUID lateFee = logFee("3.25");

    send();

    List<ClientInvoiceLine> after = clientInvoiceLineRepository.findByClientInvoiceId(invoiceId);
    assertThat(after).hasSize(before.size() + 1);
    for (ClientInvoiceLine original : before) {
      ClientInvoiceLine same =
          after.stream().filter(l -> l.getId().equals(original.getId())).findFirst().orElseThrow();
      assertThat(same.getAmount()).isEqualByComparingTo(original.getAmount());
      assertThat(same.getComputedAmount()).isEqualByComparingTo(original.getComputedAmount());
      assertThat(same.getEditedAt()).isEqualTo(original.getEditedAt());
    }
    ClientInvoiceLine added =
        after.stream()
            .filter(l -> l.getKind() == ClientInvoiceLineKind.FEE && l.getFee().getId().equals(lateFee))
            .findFirst()
            .orElseThrow();
    assertThat(added.getAmount()).isEqualByComparingTo("3.25");
    assertThat(clientInvoiceRepository.findById(invoiceId).orElseThrow().getStatus())
        .isEqualTo(ClientInvoiceStatus.SENT);
    assertTotals(agentRead(), "18.00", "8.25", "26.25");
  }

  @Test
  void feeLoggedAfterResendDoesNotAppear() throws Exception {
    fixture("Stored Lines Resend Later Fee");
    logFee("5.00");
    UUID invoiceId = send();
    setBackToDraft(invoiceId);
    logFee("3.25");
    send();

    logFee("11.00");

    assertTotals(agentRead(), "0.00", "8.25", "8.25");
  }

  @Test
  void reviewQueueRowSumsTheStoredLines() throws Exception {
    fixture("Stored Lines Queue");
    addPostpaidSim("+1-555-0306", "18.00");
    logFee("5.00");
    UUID invoiceId = send();
    logFee("9.00");

    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + invoiceId + "')].totalAmount").value(23.00));
  }

  @Test
  void sendOfASentOrApprovedInvoiceGets409() throws Exception {
    fixture("Stored Lines Conflict");
    UUID invoiceId = send();

    sendExpecting(409);

    mockMvc
        .perform(post("/api/client-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());
    sendExpecting(409);
  }

  @Test
  void legacyInvoiceReadsSameLinesTotalsPdfAndQueueRow() throws Exception {
    fixture("Stored Lines Legacy");
    UUID fee = logFee("5.00");
    UUID invoiceId = legacySentInvoice("30.00", List.of(fee));

    String agentJson = agentRead();
    assertThat(objectMapper.readTree(agentJson).has("basePostpaidSims")).isFalse();
    assertThat(objectMapper.readTree(agentJson).get("feeLines")).hasSize(1);
    assertTotals(agentJson, "30.00", "5.00", "35.00");
    String managerJson =
        mockMvc
            .perform(get("/api/client-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTotals(managerJson, "30.00", "5.00", "35.00");
    byte[] pdf =
        mockMvc
            .perform(get("/api/client-invoices/" + invoiceId + "/pdf").header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(pdf).isNotEmpty();
    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$[?(@.id == '" + invoiceId + "')].totalAmount").value(35.00));
  }
}
