package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.domain.Fee;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A Contract's draft Client Invoice: its live base-amount/Fee-line computation, carrier invoice
 * file attachment, and the "only the Contract's own Agent (or a Manager) can build/view it" access
 * boundary (spec.md Solution's Client Invoice entity; client-invoice-generation ticket, user
 * stories 22-23). Mirrors {@link FeeApiTest}/{@link SimCardApiTest}'s pattern: nested under a
 * Contract, driven through the real HTTP seam.
 */
class ClientInvoiceApiTest extends IntegrationTest {

  @Autowired private FeeRepository feeRepository;

  private UUID addSimCard(
      String managerToken, UUID contractId, String number, String flavor, String monthlyFeeAmount) throws Exception {
    // A Postpaid SIM's monthly fee comes from a Postpaid Plan of its Carrier (postpaid-sim-plan
    // ticket), so the fee this test wants is set up as a Plan at that price.
    String body =
        monthlyFeeAmount == null
            ? """
                {"number":"%s","carrierId":"%s","flavor":"%s"}
                """.formatted(number, carrierFor(managerToken, contractId), flavor)
            : postpaidSimCardJson(managerToken, contractId, number, monthlyFeeAmount);

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

  private void retireSimCard(String agentToken, UUID contractId, UUID simCardId) throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/api/contracts/" + contractId + "/sim-cards/" + simCardId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"RETIRED"}
                    """))
        .andExpect(status().isOk());
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

  // --- AC: get-or-create on first access ---------------------------------------------------------

  @Test
  void openingAContractsClientInvoiceForTheFirstTimeThisMonthCreatesItInDraft() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contractId").value(contractId.toString()))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.billingMonth").value(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).toString()))
        .andExpect(jsonPath("$.baseAmount").value(0))
        .andExpect(jsonPath("$.feeLines").isArray())
        .andExpect(jsonPath("$.feeLines.length()").value(0))
        .andExpect(jsonPath("$.files").isArray())
        .andExpect(jsonPath("$.files.length()").value(0));
  }

  @Test
  void reopeningTheSameContractsClientInvoiceTheSameMonthReturnsTheSameDraft() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String agentToken = agentToken();

    MvcResult first =
        mockMvc
            .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    UUID firstId = UUID.fromString(objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText());

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(firstId.toString()));
  }

  // --- AC: base amount from a mix of Postpaid/Prepaid/Retired SIMs -----------------------------

  @Test
  void baseAmountSumsOnlyCurrentlyActivePostpaidSims() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Bright Path Clinics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String agentToken = agentToken();

    addSimCard(managerToken, contractId, "+1-555-0100", "POSTPAID", "25.00");
    addSimCard(managerToken, contractId, "+1-555-0101", "POSTPAID", "15.50");
    // Prepaid never carries a monthly fee — excluded regardless of status.
    addSimCard(managerToken, contractId, "+1-555-0102", "PREPAID", null);
    // A Postpaid SIM that has since been retired must not count, even though it once did.
    UUID retiredPostpaid = addSimCard(managerToken, contractId, "+1-555-0103", "POSTPAID", "40.00");
    retireSimCard(agentToken, contractId, retiredPostpaid);

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseAmount").value(40.50))
        .andExpect(jsonPath("$.totalAmount").value(40.50));
  }

  // --- AC: Fee lines scoped to this Contract and this calendar month ---------------------------

  @Test
  void feeLinesReflectOnlyThisContractsFeesLoggedThisMonth() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "charlotte.finch@harborfinch.example", "Passw0rd!23");
    String agentToken = agentToken();

    UUID topupRequestId = submitRequest(testerToken, contractId, "TOPUP");
    logFee(agentToken, contractId, topupRequestId, "TOPUP", "45.00");
    UUID repairRequestId = submitRequest(testerToken, contractId, "REPAIR");
    logFee(agentToken, contractId, repairRequestId, "REPAIR", "60.00");

    // A Fee logged this month, but attributed to last month's billing month (the kind of
    // backdating Fee.java's Javadoc anticipates — bypassing the controller, which always sets
    // billingMonth to "now", to simulate it), must not appear on this month's invoice.
    UUID staleRequestId = submitRequest(testerToken, contractId, "TOPUP");
    UUID staleFeeId = logFee(agentToken, contractId, staleRequestId, "TOPUP", "99.00");
    Fee staleFee = feeRepository.findById(staleFeeId).orElseThrow();
    staleFee.setBillingMonth(staleFee.getBillingMonth().minusMonths(1));
    feeRepository.saveAndFlush(staleFee);

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.feeLines.length()").value(2))
        .andExpect(jsonPath("$.baseAmount").value(0))
        .andExpect(jsonPath("$.totalAmount").value(105.00));
  }

  // --- AC: carrier invoice file attachment ------------------------------------------------------

  @Test
  void agentAttachesACarrierInvoiceFileToTheDraftAndCanListAndDownloadIt() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Kessler & Vance LLP");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String agentToken = agentToken();

    MockMultipartFile file =
        new MockMultipartFile("file", "october-carrier-invoice.pdf", "application/pdf", "not a real pdf".getBytes());

    MvcResult uploadResult =
        mockMvc
            .perform(
                multipart("/api/contracts/" + contractId + "/client-invoice/files")
                    .file(file)
                    .header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.filename").value("october-carrier-invoice.pdf"))
            .andExpect(jsonPath("$.contentType").value("application/pdf"))
            .andExpect(jsonPath("$.sizeBytes").value("not a real pdf".getBytes().length))
            .andReturn();
    UUID fileId =
        UUID.fromString(objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("id").asText());

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/client-invoice/files").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(fileId.toString()));

    // The main invoice view embeds attached files too, so it's scannable at a glance in one call.
    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.files.length()").value(1))
        .andExpect(jsonPath("$.files[0].filename").value("october-carrier-invoice.pdf"));

    MvcResult downloadResult =
        mockMvc
            .perform(
                get("/api/contracts/" + contractId + "/client-invoice/files/" + fileId)
                    .header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andReturn();
    assertThat(downloadResult.getResponse().getContentAsByteArray()).isEqualTo("not a real pdf".getBytes());
  }

  @Test
  void aContractCanHaveMultipleCarrierInvoiceFilesAttached() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Solene Cosmetics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String agentToken = agentToken();

    mockMvc
        .perform(
            multipart("/api/contracts/" + contractId + "/client-invoice/files")
                .file(new MockMultipartFile("file", "invoice-1.pdf", "application/pdf", "one".getBytes()))
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            multipart("/api/contracts/" + contractId + "/client-invoice/files")
                .file(new MockMultipartFile("file", "invoice-2.pdf", "application/pdf", "two".getBytes()))
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/client-invoice/files").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  // --- Access control ------------------------------------------------------------------------

  @Test
  void aTesterCannotViewADraftClientInvoiceAtAll() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void anAgentOnADifferentContractCannotViewOrBuildItsClientInvoice() throws Exception {
    String managerToken = managerToken();
    UUID otherClient = createClient(managerToken, "Meridian Logistics");
    UUID otherAgentId =
        createAgent(managerToken, "Priya Nair", com.remotesupport.backend.domain.Country.PHILIPPINES);
    UUID otherContract = createContract(managerToken, otherClient, otherAgentId);

    mockMvc
        .perform(
            get("/api/contracts/" + otherContract + "/client-invoice").header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void aManagerCanViewAnyContractsDraftClientInvoice() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Bright Path Clinics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"));
  }

  @Test
  void aTesterCannotAttachACarrierInvoiceFile() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "charlotte.finch@harborfinch.example", "Passw0rd!23");

    mockMvc
        .perform(
            multipart("/api/contracts/" + contractId + "/client-invoice/files")
                .file(new MockMultipartFile("file", "invoice.pdf", "application/pdf", "x".getBytes()))
                .header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isForbidden());
  }

  // --- AC: send transition, snapshot correctness, editability lock -----------------------------

  @Test
  void agentSendsADraftClientInvoiceMovingItToSent() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Solene Cosmetics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String agentToken = agentToken();

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/api/contracts/" + contractId + "/client-invoice/send").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.sentAt").isNotEmpty());
  }

  @Test
  void sendingAClientInvoiceThatIsAlreadySentIsRejected() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Kessler & Vance LLP");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String agentToken = agentToken();

    mockMvc.perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken));
    mockMvc
        .perform(post("/api/contracts/" + contractId + "/client-invoice/send").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/api/contracts/" + contractId + "/client-invoice/send").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isConflict());
  }

  @Test
  void aManagerCannotSendAClientInvoiceOnlyTheContractsOwnAgentCan() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Bright Path Clinics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    mockMvc.perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + managerToken));

    mockMvc
        .perform(post("/api/contracts/" + contractId + "/client-invoice/send").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void anAgentOnADifferentContractCannotSendItsClientInvoice() throws Exception {
    String managerToken = managerToken();
    UUID otherClient = createClient(managerToken, "Meridian Logistics");
    UUID otherAgentId =
        createAgent(managerToken, "Priya Nair", com.remotesupport.backend.domain.Country.PHILIPPINES);
    UUID otherContract = createContract(managerToken, otherClient, otherAgentId);
    mockMvc.perform(get("/api/contracts/" + otherContract + "/client-invoice").header("Authorization", "Bearer " + managerToken));

    mockMvc
        .perform(
            post("/api/contracts/" + otherContract + "/client-invoice/send")
                .header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void aSentClientInvoiceCanNoLongerAcceptNewCarrierInvoiceFiles() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String agentToken = agentToken();

    mockMvc.perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken));
    mockMvc
        .perform(post("/api/contracts/" + contractId + "/client-invoice/send").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            multipart("/api/contracts/" + contractId + "/client-invoice/files")
                .file(new MockMultipartFile("file", "late-invoice.pdf", "application/pdf", "late".getBytes()))
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isConflict());
  }

  @Test
  void sendingSnapshotsTheTotalSoALaterFeeNeverChangesTheSentInvoice() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");
    String agentToken = agentToken();

    addSimCard(managerToken, contractId, "+1-555-0199", "POSTPAID", "25.00");
    UUID topupRequestId = submitRequest(testerToken, contractId, "TOPUP");
    logFee(agentToken, contractId, topupRequestId, "TOPUP", "45.00");

    mockMvc
        .perform(post("/api/contracts/" + contractId + "/client-invoice/send").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseAmount").value(25.00))
        .andExpect(jsonPath("$.totalAmount").value(70.00))
        .andExpect(jsonPath("$.feeLines.length()").value(1));

    // A Fee logged against this same Contract/month *after* sending must never change the sent
    // invoice's already-frozen numbers — the whole point of the snapshot-on-send decision
    // (ClientInvoice's Javadoc / CONTEXT.md).
    UUID repairRequestId = submitRequest(testerToken, contractId, "REPAIR");
    logFee(agentToken, contractId, repairRequestId, "REPAIR", "999.00");

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.baseAmount").value(25.00))
        .andExpect(jsonPath("$.totalAmount").value(70.00))
        .andExpect(jsonPath("$.feeLines.length()").value(1));

    // A subsequently-retired Postpaid SIM must not move the already-frozen base amount either.
    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseAmount").value(25.00))
        .andExpect(jsonPath("$.totalAmount").value(70.00));
  }

  // --- AC: Tester visibility gated on non-draft status ------------------------------------------

  @Test
  void aTesterCanViewASentClientInvoiceReadOnly() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "charlotte.finch@harborfinch.example", "Passw0rd!23");
    String agentToken = agentToken();

    mockMvc.perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken));
    mockMvc
        .perform(post("/api/contracts/" + contractId + "/client-invoice/send").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"));
  }

  @Test
  void aTesterFromADifferentClientCannotViewASentClientInvoice() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Solene Cosmetics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String agentToken = agentToken();
    mockMvc.perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken));
    mockMvc
        .perform(post("/api/contracts/" + contractId + "/client-invoice/send").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());

    UUID otherClientId = createClient(managerToken, "Meridian Logistics");
    String otherTesterToken =
        createTesterAndLogin(managerToken, otherClientId, "priya.raman@meridian.example", "Passw0rd!23");

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + otherTesterToken))
        .andExpect(status().isForbidden());
  }

  // --- AC: on-demand PDF ---------------------------------------------------------------------

  @Test
  void aTesterGeneratesAPdfOfASentClientInvoiceReflectingTheSnapshot() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Kessler & Vance LLP");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "charlotte.finch@kesslervance.example", "Passw0rd!23");
    String agentToken = agentToken();

    addSimCard(managerToken, contractId, "+1-555-0177", "POSTPAID", "30.00");
    mockMvc.perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken));
    mockMvc
        .perform(post("/api/contracts/" + contractId + "/client-invoice/send").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());

    MvcResult pdfResult =
        mockMvc
            .perform(
                get("/api/contracts/" + contractId + "/client-invoice/pdf").header("Authorization", "Bearer " + testerToken))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andReturn();

    byte[] pdfBytes = pdfResult.getResponse().getContentAsByteArray();
    assertThat(new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    assertThat(pdfBytes.length).isGreaterThan(100);
  }

  @Test
  void generatingAPdfForAStillDraftClientInvoiceIsRejected() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Bright Path Clinics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String agentToken = agentToken();

    mockMvc.perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken));

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/client-invoice/pdf").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isConflict());
  }

  // --- Contract step: the Manager's current-month approve route is gone -------------------------

  /**
   * remove-current-month-manager-invoice-actions ticket: a Manager approves a Client Invoice by
   * the invoice's own id and nothing else, so the old {@code POST
   * /api/contracts/{contractId}/client-invoice/approve} route is deleted rather than merely left
   * uncalled. The rules it used to carry — the {@code SENT -> APPROVED} transition, the 409 on a
   * draft, and Manager-only access — are asserted against the by-id route in {@link
   * ClientInvoiceByIdApiTest}.
   */
  @Test
  void theCurrentMonthApproveRouteIsGoneAndApprovingBacksOntoTheInvoiceId() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String agentToken = agentToken();

    mockMvc.perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken));
    MvcResult sent =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/client-invoice/send")
                    .header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    UUID invoiceId = UUID.fromString(objectMapper.readTree(sent.getResponse().getContentAsString()).get("id").asText());

    mockMvc
        .perform(post("/api/contracts/" + contractId + "/client-invoice/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isNotFound());

    // The invoice is untouched: the route is gone, not silently succeeding somewhere else.
    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath("$.status").value("SENT"));

    // ... and the by-id route is how it gets approved now.
    mockMvc
        .perform(post("/api/client-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }
}
