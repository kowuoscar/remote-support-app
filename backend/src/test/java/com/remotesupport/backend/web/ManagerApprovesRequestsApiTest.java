package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The Company Manager's approval gate on Provision and Replace Requests
 * (request-types-and-flow spec, Lifecycle/Fees and approval/Manager approval;
 * manager-approves-requests ticket). Mirrors {@link ReplaceRequestsApiTest}'s pattern; approve and
 * reject are addressed by the Request's own id, the way {@code AgentInvoiceByIdController}
 * addresses an invoice (spec.md Manager approval).
 */
class ManagerApprovesRequestsApiTest extends IntegrationTest {

  private String managerToken;
  private String agentToken;
  private String testerToken;
  private UUID contractId;

  @BeforeEach
  void setUp() throws Exception {
    managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    testerToken = createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");
    agentToken = agentToken();
  }

  // --- AC: only the Manager approves or rejects; the decision records who and when -------------

  @Test
  void approvingMovesAPendingRequestToSubmittedAndRecordsTheDecision() throws Exception {
    UUID requestId = submitProvisionSmartphone();

    mockMvc
        .perform(post("/api/requests/" + requestId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.decidedByUsername").value(MANAGER_USERNAME))
        .andExpect(jsonPath("$.decidedAt").isNotEmpty());
  }

  @Test
  void rejectingRequiresAReasonAndMovesToRejected() throws Exception {
    UUID requestId = submitProvisionSmartphone();

    mockMvc
        .perform(post("/api/requests/" + requestId + "/reject").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/requests/" + requestId + "/reject")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"reason":"Budget is tight this month"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.rejectionReason").value("Budget is tight this month"))
        .andExpect(jsonPath("$.decidedByUsername").value(MANAGER_USERNAME));
  }

  @Test
  void rejectedIsTerminal() throws Exception {
    UUID requestId = submitProvisionSmartphone();
    rejectAsManager(managerToken, requestId, "Not needed");

    mockMvc
        .perform(post("/api/requests/" + requestId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isConflict());
  }

  /**
   * The approve action accepts an optional body (empty for every type today) rather than none at
   * all, so a future type-specific payload — a Return Request's per-unit Disposition
   * (CONTEXT.md "Disposition") — has somewhere to go without a breaking change to this route or
   * its callers that still send nothing.
   */
  @Test
  void approvingWithAnEmptyBodyStillWorks() throws Exception {
    UUID requestId = submitProvisionSmartphone();

    mockMvc
        .perform(
            post("/api/requests/" + requestId + "/approve")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"));
  }

  @Test
  void approvingANonPendingRequestIsAConflict() throws Exception {
    UUID requestId = submitProvisionSmartphone();
    approveAsManager(managerToken, requestId);

    mockMvc
        .perform(post("/api/requests/" + requestId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isConflict());
  }

  // --- AC: only the Manager approves/rejects; an Agent or Tester is refused ----------------------

  @Test
  void anAgentOrTesterCannotApproveOrRejectOrSeeThePendingList() throws Exception {
    UUID requestId = submitProvisionSmartphone();

    mockMvc
        .perform(post("/api/requests/" + requestId + "/approve").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/requests/" + requestId + "/approve").header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/requests/" + requestId + "/reject")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"reason":"nope"}
                    """))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/pending-requests").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/pending-requests").header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void anUnknownOrOtherTenantRequestIs404OnApproveAndReject() throws Exception {
    mockMvc
        .perform(post("/api/requests/" + UUID.randomUUID() + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/requests/" + UUID.randomUUID() + "/reject")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"reason":"nope"}
                    """))
        .andExpect(status().isNotFound());
  }

  // --- AC: the Agent sees a Pending Approval Request but can only cancel it ----------------------

  @Test
  void anAgentCanSeeButNotStartAPendingApprovalRequest() throws Exception {
    UUID requestId = submitProvisionSmartphone();

    // Visible on the Contract's own Requests list...
    mockMvc
        .perform(get("/api/contracts/" + contractId + "/requests").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"));

    // ...but the general status route refuses to move it anywhere except Cancelled, even to what
    // approval alone would set (Submitted) — approve/reject only ever happen through their own
    // dedicated actions.
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"SUBMITTED\"}").andExpect(status().isBadRequest());
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isConflict());
  }

  @Test
  void anAgentOrManagerCanCancelAPendingApprovalRequestWithAReason() throws Exception {
    UUID requestId = submitProvisionSmartphone();

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"CANCELLED\"}").andExpect(status().isBadRequest());
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"CANCELLED\",\"cancellationReason\":\"No longer needed\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    // Cancelled is terminal — even for a Manager trying to approve it now.
    mockMvc
        .perform(post("/api/requests/" + requestId + "/approve").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isConflict());
  }

  // --- AC: end-to-end — approved, the Agent progresses and completes it as usual ------------------

  @Test
  void onceApprovedTheAgentProgressesAndCompletesTheRequestAsUsual() throws Exception {
    UUID requestId = submitProvisionSmartphone();
    approveAsManager(managerToken, requestId);

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/smartphones").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].model").value("Fixture Model"));
  }

  // --- AC: a proactive Fee is refused for the four approval-required types ----------------------

  @Test
  void aProactiveFeeIsRefusedForEachApprovalRequiredType() throws Exception {
    UUID testerId = findTesterId();

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"feeType":"PROVISION_SMARTPHONE","amount":150.00,"testerId":"%s","requestedModel":"iPhone 15"}
                    """
                        .formatted(testerId)))
        .andExpect(status().isBadRequest());

    UUID simCardToReplace = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"feeType":"REPLACE_SIM","amount":15.00,"testerId":"%s","targetSimCardId":"%s"}
                    """
                        .formatted(testerId, simCardToReplace)))
        .andExpect(status().isBadRequest());

    // Topup and Other keep working as before (spec.md Fees and approval).
    UUID simCardId = createTopupTargetSimCard(managerToken, contractId);
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"feeType":"TOPUP","amount":10.00,"testerId":"%s","targetSimCardId":"%s","description":"Kiosk top-up"}
                    """
                        .formatted(testerId, simCardId)))
        .andExpect(status().isCreated());
  }

  // --- AC: a Fee can't be logged against a Pending Approval, Rejected or Cancelled Request -------

  @Test
  void aFeeCannotBeLoggedAgainstAPendingApprovalOrRejectedOrCancelledRequest() throws Exception {
    UUID pendingRequestId = submitProvisionSmartphone();
    feeRequest(pendingRequestId, "PROVISION_SMARTPHONE").andExpect(status().isBadRequest());

    UUID rejectedRequestId = submitProvisionSmartphone();
    rejectAsManager(managerToken, rejectedRequestId, "No budget");
    feeRequest(rejectedRequestId, "PROVISION_SMARTPHONE").andExpect(status().isBadRequest());

    UUID cancelledRequestId = submitProvisionSmartphone();
    patchStatus(contractId, cancelledRequestId, agentToken, "{\"status\":\"CANCELLED\",\"cancellationReason\":\"No longer needed\"}")
        .andExpect(status().isOk());
    feeRequest(cancelledRequestId, "PROVISION_SMARTPHONE").andExpect(status().isBadRequest());
  }

  // --- AC: the Manager's Pending Requests page lists every Pending Approval Request --------------

  @Test
  void pendingRequestsListsEveryPendingApprovalRequestLongestWaitingFirstWithItsDetails() throws Exception {
    // The list is tenant-wide (spec.md: "across every Contract"), and the shared seed data (V45
    // migration) already holds one Pending Approval Request in this same dev tenant — so this
    // filters down to the two Requests this test itself created, by contractId, rather than
    // asserting an exact overall size or index.
    UUID firstRequestId = submitProvisionSmartphone();
    Thread.sleep(5);
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID secondRequestId = submitReplaceSmartphone(smartphoneId);

    JsonNode allItems =
        objectMapper.readTree(
            mockMvc
                .perform(get("/api/pending-requests").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    java.util.List<JsonNode> items = new java.util.ArrayList<>();
    for (JsonNode item : allItems) {
      if (item.get("request").get("contractId").asText().equals(contractId.toString())) {
        items.add(item);
      }
    }

    assertThat(items).hasSize(2);
    assertThat(items.get(0).get("request").get("id").asText()).isEqualTo(firstRequestId.toString());
    assertThat(items.get(1).get("request").get("id").asText()).isEqualTo(secondRequestId.toString());

    JsonNode first = items.get(0);
    assertThat(first.get("clientName").asText()).isEqualTo("Aurora Retail Group");
    assertThat(first.get("agentName").asText()).isEqualTo("Jordan Ellis");
    assertThat(first.get("request").get("raisedByUsername").asText()).isEqualTo("priya.raman@aurora.example");
    assertThat(first.get("request").get("requestedModel").asText()).isEqualTo("Fixture Model");
    assertThat(first.get("waitingSince").asText()).isNotBlank();

    // A Replace Request shows the unit that would be retired.
    JsonNode second = items.get(1);
    assertThat(second.get("request").get("targetSmartphoneId").asText()).isEqualTo(smartphoneId.toString());
  }

  // --- Observability -------------------------------------------------------------------------

  @Test
  void approvingAndRejectingLogAuditEntries() throws Exception {
    UUID approvedId = submitProvisionSmartphone();
    UUID rejectedId = submitProvisionSmartphone();

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);
    try {
      approveAsManager(managerToken, approvedId);
      rejectAsManager(managerToken, rejectedId, "Not this month");

      String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertThat(logged).contains("action=REQUEST_APPROVED").contains("entityId=" + approvedId);
      assertThat(logged).contains("action=REQUEST_REJECTED").contains("entityId=" + rejectedId).contains("reasonGiven=true");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  // --- helpers -----------------------------------------------------------------------------------

  private UUID submitProvisionSmartphone() throws Exception {
    MvcResult result =
        postRequest(contractId, testerToken, "{\"type\":\"PROVISION_SMARTPHONE\",\"requestedModel\":\"Fixture Model\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID submitReplaceSmartphone(UUID targetSmartphoneId) throws Exception {
    MvcResult result =
        postRequest(
                contractId,
                testerToken,
                "{\"type\":\"REPLACE_SMARTPHONE\",\"targetSmartphoneId\":\"%s\"}".formatted(targetSmartphoneId))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private ResultActions feeRequest(UUID requestId, String feeType) throws Exception {
    return mockMvc.perform(
        post("/api/contracts/" + contractId + "/fees")
            .header("Authorization", "Bearer " + agentToken)
            .contentType(APPLICATION_JSON)
            .content(
                """
                {"requestId":"%s","feeType":"%s","amount":10.00}
                """
                    .formatted(requestId, feeType)));
  }

  private UUID findTesterId() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/contracts/" + contractId + "/testers").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode testers = objectMapper.readTree(result.getResponse().getContentAsString());
    return UUID.fromString(testers.get(0).get("id").asText());
  }
}
