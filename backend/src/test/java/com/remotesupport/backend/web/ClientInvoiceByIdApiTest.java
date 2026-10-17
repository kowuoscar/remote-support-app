package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A Client Invoice addressed by its own id (manager-invoice-review-queue spec, "invoice by id";
 * client-invoice-review-page ticket): the Manager's read, Carrier Invoice Files, PDF and approve
 * for an invoice of any billing month. These routes never get-or-create, and resolve only inside
 * the caller's tenant. Driven through the real HTTP seam, like {@link ClientInvoiceApiTest}.
 */
@Import(OtherTenantFixture.class)
class ClientInvoiceByIdApiTest extends IntegrationTest {

  @Autowired private ClientInvoiceRepository clientInvoiceRepository;
  @Autowired private OtherTenantFixture otherTenantFixture;

  private static LocalDate currentMonth() {
    return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
  }

  private static String monthYear() {
    LocalDate month = currentMonth();
    return month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
        + " "
        + month.getYear();
  }

  private UUID openDraft(String agentToken, UUID contractId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID send(String agentToken, UUID contractId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/client-invoice/send")
                    .header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  // reboot-and-topup-details ticket: a Topup Request now requires its own target SIM Card.
  private UUID submitTopup(String testerToken, UUID contractId) throws Exception {
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

  private UUID addPostpaidSim(String managerToken, UUID contractId, String number, String fee) throws Exception {
    MvcResult created =
        mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/sim-cards")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(postpaidSimCardJson(managerToken, contractId, number, fee)))
        .andExpect(status().isCreated())
        .andReturn();
    return UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
  }

  /** Attaches a file for a new Postpaid SIM of the Contract. */
  private UUID attachFile(String agentToken, UUID contractId, String filename, String content) throws Exception {
    return attachFile(agentToken, contractId, addUniquePostpaidSim(managerToken(), contractId), filename, content);
  }

  private UUID attachFile(String agentToken, UUID contractId, UUID simCardId, String filename, String content)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/contracts/" + contractId + "/client-invoice/files")
                    .file(new MockMultipartFile("file", filename, "application/pdf", content.getBytes()))
                    .param("simCardId", simCardId.toString())
                    .header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  /** Moves an invoice into an earlier billing month — stands in for a month rollover in a test. */
  private void moveToPastMonth(UUID invoiceId, int monthsBack) {
    ClientInvoice invoice = clientInvoiceRepository.findById(invoiceId).orElseThrow();
    invoice.setBillingMonth(invoice.getBillingMonth().minusMonths(monthsBack));
    clientInvoiceRepository.saveAndFlush(invoice);
  }

  private UUID newContract(String managerToken, String clientName) throws Exception {
    return createContract(managerToken, createClient(managerToken, clientName), SEEDED_AGENT_ID);
  }

  // --- Read ---------------------------------------------------------------------------------

  @Test
  void managerReadsASentClientInvoiceByIdFromItsSnapshot() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken = createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example");

    UUID simId = addPostpaidSim(managerToken, contractId, "+1-555-0190", "25.00");
    logTopupFee(agentToken, contractId, submitTopup(testerToken, contractId), "45.00");
    attachFile(agentToken, contractId, simId, "carrier.pdf", "carrier bytes");
    UUID invoiceId = send(agentToken, contractId);

    // Snapshot-on-send (ADR 0001): a Fee logged after sending never moves the by-id total.
    logTopupFee(agentToken, contractId, submitTopup(testerToken, contractId), "999.00");

    mockMvc
        .perform(get("/api/client-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(invoiceId.toString()))
        .andExpect(jsonPath("$.contractId").value(contractId.toString()))
        .andExpect(jsonPath("$.billingMonth").value(currentMonth().toString()))
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.baseAmount").value(25.00))
        .andExpect(jsonPath("$.feeLines.length()").value(1))
        .andExpect(jsonPath("$.feeLines[0].amount").value(45.00))
        .andExpect(jsonPath("$.totalAmount").value(70.00))
        .andExpect(jsonPath("$.files.length()").value(1))
        .andExpect(
            jsonPath("$.files[0].filename")
                .value("Aurora Retail Group - " + monthYear() + " - AT&T - +1-555-0190.pdf"))
        .andExpect(jsonPath("$.sentAt").isNotEmpty())
        .andExpect(jsonPath("$.approvedAt").isEmpty());
  }

  @Test
  void managerReadsAPastMonthsClientInvoiceById() throws Exception {
    String managerToken = managerToken();
    UUID contractId = newContract(managerToken, "Meridian Logistics");
    UUID invoiceId = send(agentToken(), contractId);
    moveToPastMonth(invoiceId, 1);

    mockMvc
        .perform(get("/api/client-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.billingMonth").value(currentMonth().minusMonths(1).toString()))
        .andExpect(jsonPath("$.status").value("SENT"));
  }

  @Test
  void managerReadsADraftClientInvoiceById() throws Exception {
    String managerToken = managerToken();
    UUID contractId = newContract(managerToken, "Bright Path Clinics");
    UUID invoiceId = openDraft(agentToken(), contractId);

    mockMvc
        .perform(get("/api/client-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"));
  }

  // --- Files and PDF ------------------------------------------------------------------------

  @Test
  void managerListsAndDownloadsAPastMonthsCarrierInvoiceFilesById() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID contractId = newContract(managerToken, "Kessler & Vance LLP");
    UUID fileId = attachFile(agentToken, contractId, "august-carrier.pdf", "august bytes");
    UUID invoiceId = send(agentToken, contractId);
    moveToPastMonth(invoiceId, 1);

    mockMvc
        .perform(get("/api/client-invoices/" + invoiceId + "/files").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(fileId.toString()));

    MvcResult download =
        mockMvc
            .perform(
                get("/api/client-invoices/" + invoiceId + "/files/" + fileId)
                    .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andReturn();
    assertThat(download.getResponse().getContentAsByteArray()).isEqualTo("august bytes".getBytes());
  }

  @Test
  void aFileFromAnotherInvoiceIsNotFoundUnderThisInvoiceId() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID contractA = newContract(managerToken, "Solene Cosmetics");
    UUID invoiceA = send(agentToken, contractA);
    UUID contractB = newContract(managerToken, "Harbor & Finch Realty");
    UUID fileOfB = attachFile(agentToken, contractB, "b.pdf", "b");

    mockMvc
        .perform(
            get("/api/client-invoices/" + invoiceA + "/files/" + fileOfB)
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isNotFound());
  }

  @Test
  void managerRendersAPastMonthsClientInvoicePdfById() throws Exception {
    String managerToken = managerToken();
    UUID contractId = newContract(managerToken, "Aurora Retail Group");
    UUID invoiceId = send(agentToken(), contractId);
    moveToPastMonth(invoiceId, 2);

    MvcResult pdf =
        mockMvc
            .perform(get("/api/client-invoices/" + invoiceId + "/pdf").header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andReturn();
    byte[] bytes = pdf.getResponse().getContentAsByteArray();
    assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
  }

  @Test
  void aDraftClientInvoiceHasNoPdfById() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = openDraft(agentToken(), newContract(managerToken, "Meridian Logistics"));

    mockMvc
        .perform(get("/api/client-invoices/" + invoiceId + "/pdf").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isConflict());
  }

  // --- Approve ------------------------------------------------------------------------------

  @Test
  void managerApprovesASentClientInvoiceByIdAndItLeavesTheReviewQueue() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = send(agentToken(), newContract(managerToken, "Harbor & Finch Realty"));

    mockMvc
        .perform(post("/api/client-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(invoiceId.toString()))
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.approvedAt").isNotEmpty());

    mockMvc
        .perform(get("/api/client-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.status").value("APPROVED"));
    mockMvc
        .perform(get("/api/review-queue").header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void managerApprovesAPastMonthsSentClientInvoiceById() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = send(agentToken(), newContract(managerToken, "Kessler & Vance LLP"));
    moveToPastMonth(invoiceId, 1);

    mockMvc
        .perform(post("/api/client-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.billingMonth").value(currentMonth().minusMonths(1).toString()));
  }

  @Test
  void approvingADraftClientInvoiceByIdIsAConflict() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = openDraft(agentToken(), newContract(managerToken, "Solene Cosmetics"));

    mockMvc
        .perform(post("/api/client-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isConflict());
    mockMvc
        .perform(get("/api/client-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.status").value("DRAFT"));
  }

  @Test
  void approvingAnAlreadyApprovedClientInvoiceByIdIsAConflict() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = send(agentToken(), newContract(managerToken, "Bright Path Clinics"));
    mockMvc
        .perform(post("/api/client-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());

    mockMvc
        .perform(post("/api/client-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isConflict());
  }

  @Test
  void approvingByIdLogsTheStatusTransitionAuditEvent() throws Exception {
    String managerToken = managerToken();
    UUID invoiceId = send(agentToken(), newContract(managerToken, "Aurora Retail Group"));

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);
    try {
      mockMvc
          .perform(
              post("/api/client-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken))
          .andExpect(status().isOk());

      String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertThat(logged).contains("action=STATUS_CHANGE");
      assertThat(logged).contains("entity=ClientInvoice");
      assertThat(logged).contains("entityId=" + invoiceId);
      assertThat(logged).contains("oldStatus=SENT");
      assertThat(logged).contains("newStatus=APPROVED");
      assertThat(logged).contains("actorUserId=22222222-2222-2222-2222-222222222222");
      assertThat(logged).contains("tenantId=11111111-1111-1111-1111-111111111111");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  // --- Access, tenancy, never creating ------------------------------------------------------

  private List<MockHttpServletRequestBuilder> everyByIdRoute(UUID invoiceId, UUID fileId) {
    return List.of(
        get("/api/client-invoices/" + invoiceId),
        get("/api/client-invoices/" + invoiceId + "/files"),
        get("/api/client-invoices/" + invoiceId + "/files/" + fileId),
        get("/api/client-invoices/" + invoiceId + "/pdf"),
        post("/api/client-invoices/" + invoiceId + "/approve"));
  }

  @Test
  void agentsAndTestersAreForbiddenFromEveryByIdRouteEvenForTheirOwnContract() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken = createTesterAndLogin(managerToken, clientId, "ops@meridian.example");
    UUID fileId = attachFile(agentToken, contractId, "carrier.pdf", "x");
    UUID invoiceId = send(agentToken, contractId);

    // The Contract's own Agent reads its invoice and files by id since send-a-client-invoice-back;
    // what stays refused to it is the PDF and the Manager's approve.
    for (MockHttpServletRequestBuilder route :
        List.of(
            get("/api/client-invoices/" + invoiceId + "/pdf"),
            post("/api/client-invoices/" + invoiceId + "/approve"))) {
      mockMvc.perform(route.header("Authorization", "Bearer " + agentToken)).andExpect(status().isForbidden());
    }
    for (MockHttpServletRequestBuilder route : everyByIdRoute(invoiceId, fileId)) {
      mockMvc.perform(route.header("Authorization", "Bearer " + testerToken)).andExpect(status().isForbidden());
    }
    mockMvc
        .perform(get("/api/client-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
        .andExpect(jsonPath("$.status").value("SENT"));
  }

  @Test
  void anUnknownInvoiceIdIsNotFoundOnEveryByIdRouteAndCreatesNothing() throws Exception {
    String managerToken = managerToken();
    // A Contract with no invoice this month: nothing any by-id route does may conjure one.
    newContract(managerToken, "Harbor & Finch Realty");
    long invoicesBefore = clientInvoiceRepository.count();

    for (MockHttpServletRequestBuilder route : everyByIdRoute(UUID.randomUUID(), UUID.randomUUID())) {
      mockMvc.perform(route.header("Authorization", "Bearer " + managerToken)).andExpect(status().isNotFound());
    }

    assertThat(clientInvoiceRepository.count()).isEqualTo(invoicesBefore);
  }

  @Test
  void anotherTenantsInvoiceIdIsNotFoundOnEveryByIdRoute() throws Exception {
    String managerToken = managerToken();
    UUID otherTenantInvoice = otherTenantFixture.sentClientInvoiceInAnotherTenant();

    for (MockHttpServletRequestBuilder route : everyByIdRoute(otherTenantInvoice, UUID.randomUUID())) {
      mockMvc.perform(route.header("Authorization", "Bearer " + managerToken)).andExpect(status().isNotFound());
    }
    assertThat(clientInvoiceRepository.findById(otherTenantInvoice).orElseThrow().getStatus().name())
        .isEqualTo("SENT");
  }

  // --- Attaching one file per Postpaid SIM line, by id (carrier-invoice-per-sim) ----------------

  private org.springframework.test.web.servlet.ResultActions attachById(
      String token, UUID invoiceId, UUID simCardId, String name) throws Exception {
    return mockMvc.perform(
        multipart("/api/client-invoices/" + invoiceId + "/files")
            .file(new MockMultipartFile("file", name, "application/pdf", name.getBytes()))
            .param("simCardId", simCardId.toString())
            .header("Authorization", "Bearer " + token));
  }

  @Test
  void aSecondAttachForTheSameSimByIdIsACoded409WithOneRowAndOneObject() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID contractId = newContract(managerToken, "Twice By Id Co");
    UUID simId = addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId = openDraft(agentToken, contractId);

    attachById(agentToken, invoiceId, simId, "first.pdf")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.simCardId").value(simId.toString()));
    attachById(agentToken, invoiceId, simId, "second.pdf")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CARRIER_INVOICE_ALREADY_ATTACHED"))
        .andExpect(jsonPath("$.message").isNotEmpty());

    mockMvc
        .perform(get("/api/client-invoices/" + invoiceId + "/files").header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(
            jsonPath("$[0].filename")
                .value(org.hamcrest.Matchers.startsWith("Twice By Id Co - " + monthYear() + " - AT&T - ")));
    assertThat(
            com.remotesupport.backend.support.ObjectStoreTestSupport.keysUnder(
                "carrier-invoices/" + SEEDED_TENANT_ID + "/" + invoiceId + "/"))
        .hasSize(1);
  }

  @Test
  void attachByIdWithoutASimCardIdOrForANonLineSimIs400AndLeavesNothingInTheBucket() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID contractId = newContract(managerToken, "Bad Sim By Id Co");
    addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId = openDraft(agentToken, contractId);

    mockMvc
        .perform(
            multipart("/api/client-invoices/" + invoiceId + "/files")
                .file(new MockMultipartFile("file", "a.pdf", "application/pdf", "a".getBytes()))
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isBadRequest());
    attachById(agentToken, invoiceId, UUID.randomUUID(), "b.pdf")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Not a Postpaid SIM line of this Client Invoice"));

    assertThat(
            com.remotesupport.backend.support.ObjectStoreTestSupport.keysUnder(
                "carrier-invoices/" + SEEDED_TENANT_ID + "/" + invoiceId + "/"))
        .isEmpty();
  }

  // --- The computed name (carrier-invoice-computed-name) ---------------------------------------

  private String downloadedName(String token, UUID invoiceId, UUID fileId) throws Exception {
    String disposition =
        mockMvc
            .perform(get("/api/client-invoices/" + invoiceId + "/files/" + fileId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("Content-Disposition");
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("filename\\*=UTF-8''([^;]+)").matcher(disposition);
    assertThat(m.find()).as("filename* in %s", disposition).isTrue();
    return java.net.URLDecoder.decode(m.group(1).replace("+", "%2B"), StandardCharsets.UTF_8);
  }

  @Test
  void anAttachedFileIsNamedByItsClientMonthCarrierAndSimInTheBodyTheListAndTheDownload() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID contractId = newContract(managerToken, "Acme/Labs \"Test\": North");
    UUID simId = addPostpaidSim(managerToken, contractId, "+1-202-555-0102", "25.00");
    UUID invoiceId = openDraft(agentToken, contractId);
    String expected = "Acme_Labs _Test__ North - " + monthYear() + " - AT&T - +1-202-555-0102.pdf";

    MvcResult attached =
        attachById(agentToken, invoiceId, simId, "scan.PDF")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.filename").value(expected))
            .andReturn();
    UUID fileId =
        UUID.fromString(objectMapper.readTree(attached.getResponse().getContentAsString()).get("id").asText());

    mockMvc
        .perform(get("/api/client-invoices/" + invoiceId + "/files").header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath("$[0].filename").value(expected));
    assertThat(downloadedName(agentToken, invoiceId, fileId)).isEqualTo(expected);
    assertThat(downloadedName(managerToken, invoiceId, fileId)).isEqualTo(expected);
  }

  @Test
  void theNameIsFixedAtAttachWhenTheSimsNumberIsCorrectedLater() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID contractId = newContract(managerToken, "Fixed Name Co");
    UUID simId = addPostpaidSim(managerToken, contractId, "+1-202-555-0111", "25.00");
    UUID invoiceId = openDraft(agentToken, contractId);
    String expected = "Fixed Name Co - " + monthYear() + " - AT&T - +1-202-555-0111.pdf";
    UUID fileId =
        UUID.fromString(
            objectMapper
                .readTree(
                    attachById(agentToken, invoiceId, simId, "scan.pdf")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText());

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/api/contracts/" + contractId + "/sim-cards/" + simId + "/number")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"number\":\"+1-202-555-0999\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/client-invoices/" + invoiceId + "/files").header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath("$[0].filename").value(expected));
    assertThat(downloadedName(agentToken, invoiceId, fileId)).isEqualTo(expected);
  }
}
