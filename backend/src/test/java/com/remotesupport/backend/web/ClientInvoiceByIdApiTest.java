package com.remotesupport.backend.web;

import static com.remotesupport.backend.support.ClientInvoiceMonthTestSupport.sendTheMonth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.math.BigDecimal;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.ClientInvoiceLineRepository;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.ClientInvoiceLineKind;
import com.remotesupport.backend.domain.ClientInvoiceLine;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.support.CarrierInvoiceTestSupport;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

  /**
   * The first send goes through the month send (it sends every Contract the seeded Agent holds);
   * returns the Contract's invoice id.
   */
  private UUID send(String agentToken, UUID contractId) throws Exception {
    CarrierInvoiceTestSupport.attachCarrierInvoicesForEveryPostpaidLineOfCurrentMonth(
        mockMvc, objectMapper, contractId, agentToken);
    sendTheMonth(mockMvc, agentToken);
    return openDraft(agentToken, contractId);
  }

  /**
   * Sends the seeded Agent's month first, with a Contract that bills nothing, so the Contract a
   * test creates next is a late one: its per-invoice send is allowed and reaches the carrier gate.
   */
  private void theAgentsMonthIsSent(String managerToken, String agentToken) throws Exception {
    newContract(managerToken, "Month Anchor " + UUID.randomUUID());
    sendTheMonth(mockMvc, agentToken);
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

  // --- The check mark: carrierInvoiceFileId on each Postpaid SIM line (carrier-invoice-check-mark) ---

  @Test
  void aLineServesTheIdOfItsSimsFileAndAnUncoveredLineServesAnExplicitNull() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID contractId = newContract(managerToken, "Check Mark Co");
    UUID coveredSim = addUniquePostpaidSim(managerToken, contractId);
    UUID bareSim = addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId = openDraft(agentToken, contractId);

    UUID fileId = UUID.fromString(
        objectMapper
            .readTree(attachById(agentToken, invoiceId, coveredSim, "covered.pdf")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
            .get("id")
            .asText());

    for (String token : List.of(managerToken, agentToken)) {
      String path = token.equals(managerToken)
          ? "/api/client-invoices/" + invoiceId
          : "/api/contracts/" + contractId + "/client-invoice";
      String body =
          mockMvc
              .perform(get(path).header("Authorization", "Bearer " + token))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      for (com.fasterxml.jackson.databind.JsonNode line : objectMapper.readTree(body).get("basePostpaidSims")) {
        if (line.get("simCardId").asText().equals(coveredSim.toString())) {
          assertThat(line.get("carrierInvoiceFileId").asText()).isEqualTo(fileId.toString());
        } else {
          assertThat(line.get("simCardId").asText()).isEqualTo(bareSim.toString());
          assertThat(line.has("carrierInvoiceFileId")).isTrue();
          assertThat(line.get("carrierInvoiceFileId").isNull()).isTrue();
        }
      }
    }
  }

  @Test
  void aFileWhoseSimHasNoLineIsListedButSetsNoLinesField() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID clientId = createClient(managerToken, "Check Mark Orphan Co");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken = createTesterAndLogin(managerToken, clientId, "check.mark.orphan@example.com");
    UUID leavingSim = addUniquePostpaidSim(managerToken, contractId);
    UUID stayingSim = addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId = openDraft(agentToken, contractId);
    attachById(agentToken, invoiceId, leavingSim, "leaving.pdf").andExpect(status().isCreated());

    cancelSimCard(contractId, managerToken, testerToken, agentToken, leavingSim, currentMonth().minusMonths(2));

    String body =
        mockMvc
            .perform(get("/api/client-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.files.length()").value(1))
            .andExpect(jsonPath("$.basePostpaidSims.length()").value(1))
            .andExpect(jsonPath("$.basePostpaidSims[0].simCardId").value(stayingSim.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(objectMapper.readTree(body).get("basePostpaidSims").get(0).get("carrierInvoiceFileId").isNull())
        .isTrue();
  }

  // --- Removing a file (remove-carrier-invoice-file) ----------------------------------------------

  @Autowired private com.remotesupport.backend.repository.CarrierInvoiceFileRepository carrierInvoiceFileRepository;

  private org.springframework.test.web.servlet.ResultActions removeById(String token, UUID invoiceId, UUID fileId)
      throws Exception {
    return mockMvc.perform(
        delete("/api/client-invoices/" + invoiceId + "/files/" + fileId).header("Authorization", "Bearer " + token));
  }

  private UUID attachReturningFileId(String token, UUID invoiceId, UUID simId, String name) throws Exception {
    String body =
        attachById(token, invoiceId, simId, name).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return UUID.fromString(objectMapper.readTree(body).get("id").asText());
  }

  private List<String> keysOf(UUID invoiceId) {
    return com.remotesupport.backend.support.ObjectStoreTestSupport.keysUnder(
        "carrier-invoices/" + SEEDED_TENANT_ID + "/" + invoiceId + "/");
  }

  private int fileRows(UUID invoiceId) {
    return carrierInvoiceFileRepository.findByClientInvoiceIdOrderByUploadedAtAsc(invoiceId).size();
  }

  /** A second Agent, created with a known login; returns its token. */
  private String otherAgentToken(String managerToken) throws Exception {
    String email = "other-agent-" + UUID.randomUUID() + "@agents.example";
    MvcResult created =
        mockMvc
            .perform(
                post("/api/agents")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Other Agent","country":"PHILIPPINES","salaryAmount":2000.00,"username":"%s"}
                        """
                            .formatted(email)))
            .andExpect(status().isCreated())
            .andReturn();
    return loginAs(email, objectMapper.readTree(created.getResponse().getContentAsString()).get("password").asText());
  }

  @Test
  void theAgentRemovesAFileFromACurrentMonthDraftAndItsRowAndObjectAreGone() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID contractId = newContract(managerToken, "Remove Draft Co");
    UUID simId = addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId = openDraft(agentToken, contractId);
    UUID fileId = attachReturningFileId(agentToken, invoiceId, simId, "a.pdf");
    assertThat(keysOf(invoiceId)).hasSize(1);

    removeById(agentToken, invoiceId, fileId).andExpect(status().isNoContent());

    assertThat(fileRows(invoiceId)).isZero();
    assertThat(keysOf(invoiceId)).isEmpty();
    mockMvc
        .perform(get("/api/client-invoices/" + invoiceId + "/files").header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void theAgentRemovesAFileFromASentBackDraftAndAttachesItsReplacement() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID contractId = newContract(managerToken, "Remove Sent Back Co");
    UUID simId = addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId = openDraft(agentToken, contractId);
    UUID fileId = attachReturningFileId(agentToken, invoiceId, simId, "wrong.pdf");
    send(agentToken, contractId);
    mockMvc
        .perform(
            post("/api/client-invoices/" + invoiceId + "/send-back")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"wrong file\"}"))
        .andExpect(status().isOk());
    moveToPastMonth(invoiceId, 1);

    removeById(agentToken, invoiceId, fileId).andExpect(status().isNoContent());

    assertThat(fileRows(invoiceId)).isZero();
    assertThat(keysOf(invoiceId)).isEmpty();
    attachById(agentToken, invoiceId, simId, "right.pdf").andExpect(status().isCreated());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      value = {"sent,", "approved,", "a never-sent past-month draft,PAST_MONTH_DRAFT_NOT_SENDABLE"},
      ignoreLeadingAndTrailingWhitespace = true)
  void removingFromAnInvoiceNotOpenToTheAgentIsAConflictAndKeepsTheFile(String state, String code)
      throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID contractId = newContract(managerToken, "Remove Refused Co " + state);
    UUID simId = addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId = openDraft(agentToken, contractId);
    UUID fileId = attachReturningFileId(agentToken, invoiceId, simId, "a.pdf");
    switch (state) {
      case "sent" -> send(agentToken, contractId);
      case "approved" -> {
        send(agentToken, contractId);
        mockMvc
            .perform(
                post("/api/client-invoices/" + invoiceId + "/approve").header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk());
      }
      default -> moveToPastMonth(invoiceId, 1);
    }

    var refused = removeById(agentToken, invoiceId, fileId).andExpect(status().isConflict());
    if (code != null) {
      refused.andExpect(jsonPath("$.code").value(code));
    }

    assertThat(fileRows(invoiceId)).isEqualTo(1);
    assertThat(keysOf(invoiceId)).hasSize(1);
  }

  @Test
  void aManagerATesterAndAnotherAgentAreForbiddenFromRemovingAFile() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID clientId = createClient(managerToken, "Remove Forbidden Co");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken = createTesterAndLogin(managerToken, clientId, "remove.forbidden@example.com");
    UUID simId = addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId = openDraft(agentToken, contractId);
    UUID fileId = attachReturningFileId(agentToken, invoiceId, simId, "a.pdf");
    String otherAgentToken = otherAgentToken(managerToken);

    removeById(managerToken, invoiceId, fileId).andExpect(status().isForbidden());
    removeById(testerToken, invoiceId, fileId).andExpect(status().isForbidden());
    removeById(otherAgentToken, invoiceId, fileId).andExpect(status().isForbidden());

    assertThat(fileRows(invoiceId)).isEqualTo(1);
    assertThat(keysOf(invoiceId)).hasSize(1);
  }

  @Test
  void removingAnUnknownFileIs404() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID contractId = newContract(managerToken, "Remove Unknown Co");
    UUID invoiceId = openDraft(agentToken, contractId);

    removeById(agentToken, invoiceId, UUID.randomUUID()).andExpect(status().isNotFound());
  }

  @Test
  void removingWhileFileStorageIsUnavailableIs503AndKeepsTheRow() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID contractId = newContract(managerToken, "Remove Outage Co");
    UUID simId = addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId = openDraft(agentToken, contractId);
    UUID fileId = attachReturningFileId(agentToken, invoiceId, simId, "a.pdf");

    objectStoreAvailability.markUnavailable();
    try {
      removeById(agentToken, invoiceId, fileId).andExpect(status().isServiceUnavailable());
    } finally {
      objectStoreAvailability.probe();
    }

    assertThat(fileRows(invoiceId)).isEqualTo(1);
    assertThat(keysOf(invoiceId)).hasSize(1);
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

  // --- The send gate: every Postpaid SIM line needs its Carrier Invoice File ---------------------

  @Autowired private ClientInvoiceLineRepository clientInvoiceLineRepository;
  @Autowired private ContractRepository contractRepository;
  @Autowired private ObjectStoreAvailability objectStoreAvailability;
  @Autowired private JdbcTemplate jdbcTemplate;

  private org.springframework.test.web.servlet.ResultActions sendById(String token, UUID invoiceId) throws Exception {
    return mockMvc.perform(post("/api/client-invoices/" + invoiceId + "/send").header("Authorization", "Bearer " + token));
  }

  private String numberOf(UUID simId) {
    return jdbcTemplate.queryForObject("SELECT number FROM sim_cards WHERE id = ?", String.class, simId);
  }

  private void sendBackById(String managerToken, UUID invoiceId) throws Exception {
    mockMvc
        .perform(
            post("/api/client-invoices/" + invoiceId + "/send-back")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"Wrong file\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void sendByIdWithAMissingLineIsACoded409NamingTheCountAndNumberAndWritesNothing() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    theAgentsMonthIsSent(managerToken, agentToken);
    UUID contractId = newContract(managerToken, "Gate Missing Co");
    UUID covered = addUniquePostpaidSim(managerToken, contractId);
    UUID bare = addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId = openDraft(agentToken, contractId);
    attachById(agentToken, invoiceId, covered, "covered.pdf").andExpect(status().isCreated());

    sendById(agentToken, invoiceId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CARRIER_INVOICES_MISSING"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1 Postpaid SIM")))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(numberOf(bare))));

    ClientInvoice stored = clientInvoiceRepository.findById(invoiceId).orElseThrow();
    assertThat(stored.getStatus().name()).isEqualTo("DRAFT");
    assertThat(stored.isLinesStored()).isFalse();
    assertThat(clientInvoiceLineRepository.findByClientInvoiceId(invoiceId)).isEmpty();
  }

  @Test
  void sendByIdWithEveryLineCoveredIs200() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    theAgentsMonthIsSent(managerToken, agentToken);
    UUID contractId = newContract(managerToken, "Gate Covered Co");
    addUniquePostpaidSim(managerToken, contractId);
    addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId = openDraft(agentToken, contractId);
    CarrierInvoiceTestSupport.attachCarrierInvoicesForEveryPostpaidLine(mockMvc, objectMapper, invoiceId, agentToken);

    sendById(agentToken, invoiceId).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"));
  }

  @Test
  void sendByIdWithNoPostpaidSimIs200() throws Exception {
    String agentToken = agentToken();
    theAgentsMonthIsSent(managerToken(), agentToken);
    UUID contractId = newContract(managerToken(), "Gate No Postpaid Co");
    UUID invoiceId = openDraft(agentToken, contractId);

    sendById(agentToken, invoiceId).andExpect(status().isOk());
  }

  @Test
  void aLegacyBaseAmountInvoiceSentBackResendsWithoutAnyFile() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    UUID contractId = newContract(managerToken, "Gate Legacy Co");
    Contract contract = contractRepository.findById(contractId).orElseThrow();
    ClientInvoice invoice = new ClientInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setTenant(contract.getTenant());
    invoice.setContract(contract);
    invoice.setBillingMonth(currentMonth());
    invoice.setStatus(ClientInvoiceStatus.SENT);
    invoice.setCurrency(contract.getCurrency());
    invoice.setCreatedAt(Instant.now());
    invoice.setSentAt(Instant.now());
    invoice.setFirstSentAt(Instant.now());
    invoice.setSnapshotBaseAmount(new BigDecimal("42.00"));
    invoice.setLinesStored(true);
    clientInvoiceRepository.saveAndFlush(invoice);
    ClientInvoiceLine line = new ClientInvoiceLine();
    line.setId(UUID.randomUUID());
    line.setTenant(invoice.getTenant());
    line.setClientInvoice(invoice);
    line.setKind(ClientInvoiceLineKind.BASE_AMOUNT);
    line.setAmount(new BigDecimal("42.00"));
    line.setComputedAmount(new BigDecimal("42.00"));
    line.setCreatedAt(Instant.now());
    clientInvoiceLineRepository.saveAndFlush(line);
    sendBackById(managerToken, invoice.getId());

    sendById(agentToken, invoice.getId()).andExpect(status().isOk());
  }

  @Test
  void aResendAfterASendBackIsGatedOnTheStoredLines() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    theAgentsMonthIsSent(managerToken, agentToken);
    UUID contractId = newContract(managerToken, "Gate Resend Co");
    addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId = openDraft(agentToken, contractId);
    CarrierInvoiceTestSupport.attachCarrierInvoicesForEveryPostpaidLine(mockMvc, objectMapper, invoiceId, agentToken);
    sendById(agentToken, invoiceId).andExpect(status().isOk());
    sendBackById(managerToken, invoiceId);
    // The stored line still has its file, so the resend passes.
    sendById(agentToken, invoiceId).andExpect(status().isOk());
    sendBackById(managerToken, invoiceId);
    jdbcTemplate.update("DELETE FROM carrier_invoice_files WHERE client_invoice_id = ?", invoiceId);

    sendById(agentToken, invoiceId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CARRIER_INVOICES_MISSING"));
    assertThat(clientInvoiceRepository.findById(invoiceId).orElseThrow().getStatus().name()).isEqualTo("DRAFT");
  }

  @Test
  void aFileWhoseSimStoppedBillingNeitherCountsNorBlocksTheSend() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    theAgentsMonthIsSent(managerToken, agentToken);
    UUID clientId = createClient(managerToken, "Gate Orphan Co");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken = createTesterAndLogin(managerToken, clientId, "gate.orphan@example.com");
    UUID leavingSim = addUniquePostpaidSim(managerToken, contractId);
    UUID stayingSim = addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId = openDraft(agentToken, contractId);
    attachById(agentToken, invoiceId, leavingSim, "leaving.pdf").andExpect(status().isCreated());
    cancelSimCard(contractId, managerToken, testerToken, agentToken, leavingSim, currentMonth().minusMonths(2));

    // Only the staying SIM bills now, and the leaving SIM's file does not stand in for it.
    sendById(agentToken, invoiceId).andExpect(status().isConflict());
    attachById(agentToken, invoiceId, stayingSim, "staying.pdf").andExpect(status().isCreated());
    sendById(agentToken, invoiceId).andExpect(status().isOk());
  }

  @Test
  void sendByIdSucceedsWhileFileStorageIsUnavailableWhenEveryLineHasItsFile() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    theAgentsMonthIsSent(managerToken, agentToken);
    UUID contractId = newContract(managerToken, "Gate Outage Co");
    addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId = openDraft(agentToken, contractId);
    CarrierInvoiceTestSupport.attachCarrierInvoicesForEveryPostpaidLine(mockMvc, objectMapper, invoiceId, agentToken);

    objectStoreAvailability.markUnavailable();
    try {
      sendById(agentToken, invoiceId).andExpect(status().isOk());
    } finally {
      objectStoreAvailability.probe();
    }
  }

  @Test
  void sendByIdWhileFileStorageIsUnavailableIsStillRefusedWhenAFileIsMissing() throws Exception {
    String managerToken = managerToken();
    String agentToken = agentToken();
    theAgentsMonthIsSent(managerToken, agentToken);
    UUID contractId = newContract(managerToken, "Gate Outage Missing Co");
    addUniquePostpaidSim(managerToken, contractId);
    UUID invoiceId = openDraft(agentToken, contractId);

    objectStoreAvailability.markUnavailable();
    try {
      sendById(agentToken, invoiceId)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("CARRIER_INVOICES_MISSING"));
    } finally {
      objectStoreAvailability.probe();
    }
  }
}
