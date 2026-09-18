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

  private UUID submitTopup(String testerToken, UUID contractId) throws Exception {
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

  private void addPostpaidSim(String managerToken, UUID contractId, String number, String fee) throws Exception {
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/sim-cards")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(postpaidSimCardJson(managerToken, contractId, number, fee)))
        .andExpect(status().isCreated());
  }

  private UUID attachFile(String agentToken, UUID contractId, String filename, String content) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                multipart("/api/contracts/" + contractId + "/client-invoice/files")
                    .file(new MockMultipartFile("file", filename, "application/pdf", content.getBytes()))
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
    String testerToken = createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");

    addPostpaidSim(managerToken, contractId, "+1-555-0190", "25.00");
    logTopupFee(agentToken, contractId, submitTopup(testerToken, contractId), "45.00");
    attachFile(agentToken, contractId, "carrier.pdf", "carrier bytes");
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
        .andExpect(jsonPath("$.files[0].filename").value("carrier.pdf"))
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
    String testerToken = createTesterAndLogin(managerToken, clientId, "ops@meridian.example", "Passw0rd!23");
    UUID fileId = attachFile(agentToken, contractId, "carrier.pdf", "x");
    UUID invoiceId = send(agentToken, contractId);

    for (String token : List.of(agentToken, testerToken)) {
      for (MockHttpServletRequestBuilder route : everyByIdRoute(invoiceId, fileId)) {
        mockMvc.perform(route.header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
      }
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
}
