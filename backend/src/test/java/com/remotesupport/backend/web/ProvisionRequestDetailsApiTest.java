package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.Tester;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.RequestRepository;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.support.IntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Provision Smartphone and Provision SIM carry what to provision at submission, and complete from
 * it (provision-request-details ticket: spec.md Solution's "Details at submission"/"Fleet changes
 * on completion" tables, for these two types; ticket ACs). Mirrors {@link RequestApiTest}'s and
 * {@link SimCardCarrierApiTest}'s pattern.
 */
class ProvisionRequestDetailsApiTest extends IntegrationTest {

  @Autowired private RequestRepository requestRepository;
  @Autowired private TesterRepository testerRepository;
  @Autowired private ContractRepository contractRepository;

  private String managerToken;
  private String agentToken;
  private String testerToken;
  private UUID contractId;
  private UUID testerId;

  @BeforeEach
  void setUp() throws Exception {
    managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    testerToken = createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");
    agentToken = agentToken();
    testerId = findTesterId();
  }

  // --- AC: a Provision Smartphone Request requires a requestedModel -----------------------------

  @Test
  void aProvisionSmartphoneRequestWithoutARequestedModelIsRefused() throws Exception {
    postRequest(contractId, testerToken, "{\"type\":\"PROVISION_SMARTPHONE\"}").andExpect(status().isBadRequest());
  }

  @Test
  void aProvisionSmartphoneRequestWithABlankRequestedModelIsRefused() throws Exception {
    postRequest(contractId, testerToken, "{\"type\":\"PROVISION_SMARTPHONE\",\"requestedModel\":\"   \"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void aTesterSubmittedProvisionSmartphoneRequestCarriesItsRequestedModel() throws Exception {
    postRequest(contractId, testerToken, "{\"type\":\"PROVISION_SMARTPHONE\",\"requestedModel\":\"iPhone 15\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.requestedModel").value("iPhone 15"));
  }

  @Test
  void anAgentProactiveProvisionSmartphoneRequestAlsoRequiresARequestedModel() throws Exception {
    postRequest(contractId, agentToken, "{\"type\":\"PROVISION_SMARTPHONE\",\"testerId\":\"%s\"}".formatted(testerId))
        .andExpect(status().isBadRequest());
  }

  // --- AC: a Provision SIM Request requires a flavor and an active Carrier ----------------------

  @Test
  void aProvisionSimRequestWithoutAFlavorIsRefused() throws Exception {
    postRequest(contractId, testerToken, "{\"type\":\"PROVISION_SIM\"}").andExpect(status().isBadRequest());
  }

  @Test
  void aPrepaidProvisionSimRequestNamingAnActiveCarrierIsAccepted() throws Exception {
    postRequest(
            contractId,
            testerToken,
            "{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"PREPAID\",\"requestedCarrierId\":\"%s\"}"
                .formatted(SEEDED_US_CARRIER_ID))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.requestedFlavor").value("PREPAID"))
        .andExpect(jsonPath("$.requestedCarrierId").value(SEEDED_US_CARRIER_ID.toString()))
        .andExpect(jsonPath("$.requestedCarrierName").value("Verizon"));
  }

  @Test
  void aProvisionSimRequestWithoutACarrierIsRefused() throws Exception {
    postRequest(contractId, testerToken, "{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"PREPAID\"}")
        .andExpect(status().isBadRequest());
  }

  @Test
  void aPostpaidProvisionSimRequestWithoutAPlanIsRefused() throws Exception {
    postRequest(
            contractId,
            testerToken,
            "{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"POSTPAID\",\"requestedCarrierId\":\"%s\"}"
                .formatted(SEEDED_US_CARRIER_ID))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aPostpaidProvisionSimRequestNamingAnActivePlanOfItsCarrierIsAccepted() throws Exception {
    UUID planId = createPostpaidPlan(managerToken, SEEDED_US_CARRIER_ID, "Unlimited Test", "70.00");

    postRequest(
            contractId,
            testerToken,
            ("{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"POSTPAID\",\"requestedCarrierId\":\"%s\","
                    + "\"requestedPostpaidPlanId\":\"%s\"}")
                .formatted(SEEDED_US_CARRIER_ID, planId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.requestedPostpaidPlanId").value(planId.toString()))
        .andExpect(jsonPath("$.requestedPostpaidPlanName").value("Unlimited Test"));
  }

  @Test
  void aPostpaidProvisionSimRequestNamingAnotherCarriersPlanIsRefused() throws Exception {
    UUID otherCarrier = createCarrier(managerToken, com.remotesupport.backend.domain.Country.UNITED_STATES, "Cricket");
    UUID otherCarriersPlan = createPostpaidPlan(managerToken, otherCarrier, "Cricket Unlimited", "40.00");

    postRequest(
            contractId,
            testerToken,
            ("{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"POSTPAID\",\"requestedCarrierId\":\"%s\","
                    + "\"requestedPostpaidPlanId\":\"%s\"}")
                .formatted(SEEDED_US_CARRIER_ID, otherCarriersPlan))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aProvisionSimRequestMayNameATargetSmartphoneThatIsActiveOnTheContract() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");

    postRequest(
            contractId,
            testerToken,
            ("{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"PREPAID\",\"requestedCarrierId\":\"%s\","
                    + "\"targetSmartphoneId\":\"%s\"}")
                .formatted(SEEDED_US_CARRIER_ID, smartphoneId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.targetSmartphoneId").value(smartphoneId.toString()))
        .andExpect(jsonPath("$.targetSmartphoneModel").value("Pixel 8"));
  }

  @Test
  void aProvisionSimRequestNamingATargetSmartphoneOfAnotherContractIsRefused() throws Exception {
    UUID otherClient = createClient(managerToken, "Solene Cosmetics");
    UUID otherContract = createContract(managerToken, otherClient, SEEDED_AGENT_ID);
    UUID foreignSmartphone = createSmartphone(managerToken, otherContract, "Foreign Phone");

    postRequest(
            contractId,
            testerToken,
            ("{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"PREPAID\",\"requestedCarrierId\":\"%s\","
                    + "\"targetSmartphoneId\":\"%s\"}")
                .formatted(SEEDED_US_CARRIER_ID, foreignSmartphone))
        .andExpect(status().isBadRequest());
  }

  // --- AC: completing a Provision Smartphone needs no Agent input --------------------------------

  @Test
  void completingATesterRaisedProvisionSmartphoneNeedsNoAgentInputAndAddsACompanyOwnedUnit() throws Exception {
    UUID requestId = submitProvisionSmartphone("Galaxy S24");
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/smartphones").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].model").value("Galaxy S24"))
        .andExpect(jsonPath("$[0].serial").isEmpty())
        .andExpect(jsonPath("$[0].owner").value("COMPANY"))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"));
  }

  @Test
  void anAgentProactiveProvisionSmartphoneAlwaysStartsPendingApprovalEvenAskingForCompleted() throws Exception {
    // manager-approves-requests ticket: an Agent logging a Provision Request proactively can no
    // longer start it at Completed (spec.md Lifecycle) — asking for it is refused outright.
    postRequest(
            contractId,
            agentToken,
            ("{\"type\":\"PROVISION_SMARTPHONE\",\"testerId\":\"%s\",\"startingStatus\":\"COMPLETED\","
                    + "\"requestedModel\":\"Pixel 9\"}")
                .formatted(testerId))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/smartphones").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void onceApprovedAnAgentProactiveProvisionSmartphoneStillNeedsNoAgentInputToComplete() throws Exception {
    MvcResult result =
        postRequest(contractId, agentToken, "{\"type\":\"PROVISION_SMARTPHONE\",\"testerId\":\"%s\",\"requestedModel\":\"Pixel 9\"}"
                .formatted(testerId))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andReturn();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    approveAsManager(managerToken, requestId);
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\"}").andExpect(status().isOk());

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/smartphones").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].model").value("Pixel 9"))
        .andExpect(jsonPath("$[0].serial").isEmpty());
  }

  // --- AC: completing a Provision SIM asks only for the SIM number --------------------------------

  @Test
  void completingATesterRaisedProvisionSimAsksOnlyForTheNumber() throws Exception {
    UUID planId = createPostpaidPlan(managerToken, SEEDED_US_CARRIER_ID, "Unlimited Test", "70.00");
    UUID requestId =
        submitProvisionSim(
            ("{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"POSTPAID\",\"requestedCarrierId\":\"%s\","
                    + "\"requestedPostpaidPlanId\":\"%s\"}")
                .formatted(SEEDED_US_CARRIER_ID, planId));
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\",\"simCardNumber\":\"+1-555-0177\"}")
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].number").value("+1-555-0177"))
        .andExpect(jsonPath("$[0].carrierId").value(SEEDED_US_CARRIER_ID.toString()))
        .andExpect(jsonPath("$[0].flavor").value("POSTPAID"))
        .andExpect(jsonPath("$[0].postpaidPlanId").value(planId.toString()))
        .andExpect(jsonPath("$[0].monthlyFeeAmount").value(70.00))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"));
  }

  @Test
  void completingAProvisionSimWithoutASimCardNumberIsRejected() throws Exception {
    UUID requestId =
        submitProvisionSim(
            "{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"PREPAID\",\"requestedCarrierId\":\"%s\"}"
                .formatted(SEEDED_US_CARRIER_ID));
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\"}").andExpect(status().isBadRequest());
  }

  @Test
  void completingAProvisionSimStillWorksEvenIfItsCarrierWasArchivedAfterSubmission() throws Exception {
    UUID carrier = createCarrier(managerToken, com.remotesupport.backend.domain.Country.UNITED_STATES, "Mint Mobile");
    UUID requestId =
        submitProvisionSim(
            "{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"PREPAID\",\"requestedCarrierId\":\"%s\"}"
                .formatted(carrier));
    archiveCarrier(managerToken, carrier);
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\",\"simCardNumber\":\"+1-555-0188\"}")
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].carrierId").value(carrier.toString()))
        .andExpect(jsonPath("$[0].carrierArchived").value(true));
  }

  @Test
  void aProvisionSimNamingATargetSmartphoneWithRoomIsInstalledOnCompletion() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID requestId =
        submitProvisionSim(
            ("{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"PREPAID\",\"requestedCarrierId\":\"%s\","
                    + "\"targetSmartphoneId\":\"%s\"}")
                .formatted(SEEDED_US_CARRIER_ID, smartphoneId));
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\",\"simCardNumber\":\"+1-555-0199\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completionNote").doesNotExist());

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].installedInSmartphoneId").value(smartphoneId.toString()))
        .andExpect(jsonPath("$[0].installedInSmartphoneModel").value("Pixel 8"));
  }

  @Test
  void aProvisionSimNamingAFullTargetSmartphoneIsAddedUninstalledAndTheAgentIsTold() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    // Fill the target Smartphone's two SIM Card slots first.
    createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    JsonNode simCards =
        objectMapper.readTree(
            mockMvc
                .perform(
                    get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + agentToken))
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode sim : simCards) {
      mockMvc
          .perform(
              patch(
                      "/api/contracts/"
                          + contractId
                          + "/sim-cards/"
                          + sim.get("id").asText()
                          + "/installed-in")
                  .header("Authorization", "Bearer " + managerToken)
                  .contentType(APPLICATION_JSON)
                  .content("{\"smartphoneId\":\"%s\"}".formatted(smartphoneId)))
          .andExpect(status().isOk());
    }

    UUID requestId =
        submitProvisionSim(
            ("{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"PREPAID\",\"requestedCarrierId\":\"%s\","
                    + "\"targetSmartphoneId\":\"%s\"}")
                .formatted(SEEDED_US_CARRIER_ID, smartphoneId));
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\",\"simCardNumber\":\"+1-555-0155\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completionNote").exists());

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
    JsonNode after =
        objectMapper.readTree(
            mockMvc
                .perform(
                    get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + agentToken))
                .andReturn()
                .getResponse()
                .getContentAsString());
    boolean newOneUninstalled = false;
    for (JsonNode sim : after) {
      if ("+1-555-0155".equals(sim.get("number").asText())) {
        newOneUninstalled = !sim.has("installedInSmartphoneId");
      }
    }
    assertThat(newOneUninstalled).as("the new SIM Card was added uninstalled").isTrue();
  }

  // --- AC: an Agent logging one proactively at Completed also gives the SIM number ---------------

  @Test
  void anAgentProactiveProvisionSimAlwaysStartsPendingApprovalEvenAskingForCompleted() throws Exception {
    // manager-approves-requests ticket: an Agent logging a Provision Request proactively can no
    // longer start it at Completed (spec.md Lifecycle) — asking for it is refused outright,
    // regardless of whether a SIM number was given too.
    postRequest(
            contractId,
            agentToken,
            ("{\"type\":\"PROVISION_SIM\",\"testerId\":\"%s\",\"startingStatus\":\"COMPLETED\","
                    + "\"requestedFlavor\":\"PREPAID\",\"requestedCarrierId\":\"%s\",\"simCardNumber\":\"+1-555-0166\"}")
                .formatted(testerId, SEEDED_US_CARRIER_ID))
        .andExpect(status().isBadRequest());
  }

  @Test
  void onceApprovedAnAgentProactiveProvisionSimStillNeedsOnlyTheSimNumberToComplete() throws Exception {
    MvcResult result =
        postRequest(
                contractId,
                agentToken,
                ("{\"type\":\"PROVISION_SIM\",\"testerId\":\"%s\",\"requestedFlavor\":\"PREPAID\","
                        + "\"requestedCarrierId\":\"%s\"}")
                    .formatted(testerId, SEEDED_US_CARRIER_ID))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andReturn();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    approveAsManager(managerToken, requestId);
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\",\"simCardNumber\":\"+1-555-0166\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].number").value("+1-555-0166"));
  }

  // --- AC: a Provision Request created before this ticket completes through the previous form ----

  @Test
  void aLegacyProvisionSmartphoneRequestCompletesThroughThePreviousFullForm() throws Exception {
    UUID requestId = insertLegacyRequest(RequestType.PROVISION_SMARTPHONE);
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    // No requestedModel on this Request (it predates the ticket), so completion still needs the
    // full newSmartphone form.
    patchStatus(contractId, requestId, agentToken,
            "{\"status\":\"COMPLETED\",\"newSmartphone\":{\"model\":\"iPhone 13\",\"serial\":\"SN-LEGACY-1\"}}")
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/smartphones").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].model").value("iPhone 13"))
        .andExpect(jsonPath("$[0].serial").value("SN-LEGACY-1"));
  }

  @Test
  void aProvisionSmartphoneRequestWithNoRequestedModelIsRejectedAtCompletionWithoutTheFullForm() throws Exception {
    UUID requestId = insertLegacyRequest(RequestType.PROVISION_SMARTPHONE);
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\"}").andExpect(status().isBadRequest());
  }

  @Test
  void aLegacyProvisionSimRequestCompletesThroughThePreviousFullForm() throws Exception {
    UUID requestId = insertLegacyRequest(RequestType.PROVISION_SIM);
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(contractId, requestId, agentToken,
            ("{\"status\":\"COMPLETED\",\"newSimCard\":{\"number\":\"+1-555-0111\",\"carrierId\":\"%s\","
                    + "\"flavor\":\"PREPAID\"}}")
                .formatted(SEEDED_US_CARRIER_ID))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].number").value("+1-555-0111"));
  }

  @Test
  void aProvisionSimRequestWithNoRequestedFlavorIsRejectedAtCompletionWithoutTheFullForm() throws Exception {
    UUID requestId = insertLegacyRequest(RequestType.PROVISION_SIM);
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\",\"simCardNumber\":\"+1-555-0100\"}")
        .andExpect(status().isBadRequest());
  }

  // --- helpers -----------------------------------------------------------------------------------

  /**
   * Submits a Provision Smartphone Request and has the Manager approve it immediately
   * (manager-approves-requests ticket: Provision Smartphone now starts Pending Approval, and every
   * caller of this helper goes straight on to progress/complete it).
   */
  private UUID submitProvisionSmartphone(String requestedModel) throws Exception {
    MvcResult result =
        postRequest(
                contractId,
                testerToken,
                "{\"type\":\"PROVISION_SMARTPHONE\",\"requestedModel\":\"%s\"}".formatted(requestedModel))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andReturn();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    approveAsManager(managerToken, requestId);
    return requestId;
  }

  /** Submits a Provision SIM Request and has the Manager approve it immediately — see above. */
  private UUID submitProvisionSim(String json) throws Exception {
    MvcResult result =
        postRequest(contractId, testerToken, json)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andReturn();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    approveAsManager(managerToken, requestId);
    return requestId;
  }

  /**
   * Inserts a Request directly through the repository, bypassing {@code RequestDetailsValidator}
   * entirely — the only way to produce a Request with none of this ticket's details set, exactly
   * as every Request that exists before this ticket ships does (ticket AC: "A Provision Request
   * created before this ticket completes through the previous full form").
   */
  private UUID insertLegacyRequest(RequestType type) throws Exception {
    Tester tester = testerRepository.findById(testerId).orElseThrow();
    Request request = new Request();
    request.setId(UUID.randomUUID());
    request.setTenant(contractRepository.findById(contractId).orElseThrow().getTenant());
    request.setContract(contractRepository.findById(contractId).orElseThrow());
    request.setTester(tester);
    request.setRaisedByUser(tester.getUser());
    request.setAgentAuthored(false);
    request.setType(type);
    request.setStatus(RequestStatus.SUBMITTED);
    request.setCreatedAt(Instant.now());
    requestRepository.save(request);
    return request.getId();
  }

  private UUID findTesterId() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/contracts/" + contractId + "/testers").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode testers = objectMapper.readTree(result.getResponse().getContentAsString());
    return UUID.fromString(testers.get(0).get("id").asText());
  }
}
