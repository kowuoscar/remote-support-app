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
 * A SIM Swap Request's own move/exchange detail and its no-Agent-input completion effect
 * (sim-swap-moves ticket: spec.md Solution's "Details at submission"/"Fleet changes on completion"
 * tables, SIM Swap row; ticket ACs). Mirrors {@link ProvisionRequestDetailsApiTest}'s pattern and
 * reuses {@code sim-installed-in-smartphone}'s installed-in endpoint as its own fixture builder.
 */
class SimSwapRequestDetailsApiTest extends IntegrationTest {

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
    UUID clientId = createClient(managerToken, "Meridian Field Services");
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    testerToken = createTesterAndLogin(managerToken, clientId, "noor.haddad@meridian.example", "Passw0rd!23");
    agentToken = agentToken();
    testerId = findTesterId();
  }

  // --- AC: a move names an Active SIM Card and its destination Active Smartphone -----------------

  @Test
  void aSimSwapRequestWithoutATargetSimCardIdIsRefused() throws Exception {
    postRequest(contractId, testerToken, "{\"type\":\"SIM_SWAP\"}").andExpect(status().isBadRequest());
  }

  @Test
  void aMoveWithoutATargetSmartphoneIdIsRefused() throws Exception {
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    postRequest(contractId, testerToken, "{\"type\":\"SIM_SWAP\",\"targetSimCardId\":\"%s\"}".formatted(simCardId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aTesterSubmittedMoveCarriesItsSimCardAndDestinationSmartphone() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);

    postRequest(
            contractId,
            testerToken,
            "{\"type\":\"SIM_SWAP\",\"targetSimCardId\":\"%s\",\"targetSmartphoneId\":\"%s\"}"
                .formatted(simCardId, smartphoneId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.targetSimCardId").value(simCardId.toString()))
        .andExpect(jsonPath("$.targetSmartphoneId").value(smartphoneId.toString()))
        .andExpect(jsonPath("$.targetSmartphoneModel").value("Pixel 8"))
        .andExpect(jsonPath("$.secondSimCardId").doesNotExist());
  }

  @Test
  void aMoveNamingASimCardFromAnotherContractIsRefused() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID otherClient = createClient(managerToken, "Solene Cosmetics");
    UUID otherContract = createContract(managerToken, otherClient, SEEDED_AGENT_ID);
    UUID foreignSimCard = createSimCard(managerToken, otherContract, SEEDED_US_CARRIER_ID);

    postRequest(
            contractId,
            testerToken,
            "{\"type\":\"SIM_SWAP\",\"targetSimCardId\":\"%s\",\"targetSmartphoneId\":\"%s\"}"
                .formatted(foreignSimCard, smartphoneId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aMoveNamingASmartphoneFromAnotherContractIsRefused() throws Exception {
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    UUID otherClient = createClient(managerToken, "Solene Cosmetics");
    UUID otherContract = createContract(managerToken, otherClient, SEEDED_AGENT_ID);
    UUID foreignSmartphone = createSmartphone(managerToken, otherContract, "Foreign Phone");

    postRequest(
            contractId,
            testerToken,
            "{\"type\":\"SIM_SWAP\",\"targetSimCardId\":\"%s\",\"targetSmartphoneId\":\"%s\"}"
                .formatted(simCardId, foreignSmartphone))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aMoveNamingARetiredSmartphoneIsRefused() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    retireSmartphone(smartphoneId);

    postRequest(
            contractId,
            testerToken,
            "{\"type\":\"SIM_SWAP\",\"targetSimCardId\":\"%s\",\"targetSmartphoneId\":\"%s\"}"
                .formatted(simCardId, smartphoneId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aMoveThatChangesNothingIsRefused() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    installSimCard(managerToken, simCardId, smartphoneId);

    postRequest(
            contractId,
            testerToken,
            "{\"type\":\"SIM_SWAP\",\"targetSimCardId\":\"%s\",\"targetSmartphoneId\":\"%s\"}"
                .formatted(simCardId, smartphoneId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aMoveThatWouldExceedTheTwoSimLimitIsRefused() throws Exception {
    UUID target = createSmartphone(managerToken, contractId, "Pixel 8");
    installSimCard(managerToken, createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID), target);
    installSimCard(managerToken, createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID), target);
    UUID movingSimCard = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);

    postRequest(
            contractId,
            testerToken,
            "{\"type\":\"SIM_SWAP\",\"targetSimCardId\":\"%s\",\"targetSmartphoneId\":\"%s\"}"
                .formatted(movingSimCard, target))
        .andExpect(status().isConflict());
  }

  @Test
  void anAgentProactiveMoveAlsoRequiresTheSameFields() throws Exception {
    postRequest(contractId, agentToken, "{\"type\":\"SIM_SWAP\",\"testerId\":\"%s\"}".formatted(testerId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anAgentProactiveMoveIsAccepted() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);

    postRequest(
            contractId,
            agentToken,
            "{\"type\":\"SIM_SWAP\",\"testerId\":\"%s\",\"targetSimCardId\":\"%s\",\"targetSmartphoneId\":\"%s\"}"
                .formatted(testerId, simCardId, smartphoneId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.targetSimCardId").value(simCardId.toString()))
        .andExpect(jsonPath("$.targetSmartphoneId").value(smartphoneId.toString()));
  }

  @Test
  void anAgentProactiveExchangeIsAccepted() throws Exception {
    UUID phoneA = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID phoneB = createSmartphone(managerToken, contractId, "iPhone 15");
    UUID simA = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    UUID simB = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    installSimCard(managerToken, simA, phoneA);
    installSimCard(managerToken, simB, phoneB);

    postRequest(
            contractId,
            agentToken,
            "{\"type\":\"SIM_SWAP\",\"testerId\":\"%s\",\"targetSimCardId\":\"%s\",\"secondSimCardId\":\"%s\"}"
                .formatted(testerId, simA, simB))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.secondSimCardId").value(simB.toString()));
  }

  // --- AC: an exchange names two SIM Cards installed in two different Smartphones ----------------

  @Test
  void anExchangeOfTwoInstalledSimCardsSetsBothMoves() throws Exception {
    UUID phoneA = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID phoneB = createSmartphone(managerToken, contractId, "iPhone 15");
    UUID simA = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    UUID simB = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    installSimCard(managerToken, simA, phoneA);
    installSimCard(managerToken, simB, phoneB);

    postRequest(
            contractId,
            testerToken,
            "{\"type\":\"SIM_SWAP\",\"targetSimCardId\":\"%s\",\"secondSimCardId\":\"%s\"}"
                .formatted(simA, simB))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.targetSimCardId").value(simA.toString()))
        .andExpect(jsonPath("$.targetSmartphoneId").value(phoneB.toString()))
        .andExpect(jsonPath("$.secondSimCardId").value(simB.toString()))
        .andExpect(jsonPath("$.secondTargetSmartphoneId").value(phoneA.toString()))
        .andExpect(jsonPath("$.secondTargetSmartphoneModel").value("Pixel 8"));
  }

  @Test
  void anExchangeNamingTheSameSimCardTwiceIsRefused() throws Exception {
    UUID phoneA = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simA = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    installSimCard(managerToken, simA, phoneA);

    postRequest(
            contractId,
            testerToken,
            "{\"type\":\"SIM_SWAP\",\"targetSimCardId\":\"%s\",\"secondSimCardId\":\"%s\"}".formatted(simA, simA))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anExchangeNamingASimCardThatIsNotInstalledIsRefused() throws Exception {
    UUID phoneA = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simA = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    installSimCard(managerToken, simA, phoneA);
    UUID uninstalledSim = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);

    postRequest(
            contractId,
            testerToken,
            "{\"type\":\"SIM_SWAP\",\"targetSimCardId\":\"%s\",\"secondSimCardId\":\"%s\"}"
                .formatted(simA, uninstalledSim))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anExchangeNamingTwoSimCardsInTheSameSmartphoneIsRefused() throws Exception {
    UUID phoneA = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simA = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    UUID simB = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    installSimCard(managerToken, simA, phoneA);
    installSimCard(managerToken, simB, phoneA);

    postRequest(
            contractId,
            testerToken,
            "{\"type\":\"SIM_SWAP\",\"targetSimCardId\":\"%s\",\"secondSimCardId\":\"%s\"}"
                .formatted(simA, simB))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anExchangeNamingASecondSimCardFromAnotherContractIsRefused() throws Exception {
    UUID phoneA = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simA = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    installSimCard(managerToken, simA, phoneA);

    UUID otherClient = createClient(managerToken, "Solene Cosmetics");
    UUID otherContract = createContract(managerToken, otherClient, SEEDED_AGENT_ID);
    UUID otherPhone = createSmartphone(managerToken, otherContract, "Foreign Phone");
    UUID foreignSim = createSimCard(managerToken, otherContract, SEEDED_US_CARRIER_ID);
    installSimCard(managerToken, otherContract, foreignSim, otherPhone);

    postRequest(
            contractId,
            testerToken,
            "{\"type\":\"SIM_SWAP\",\"targetSimCardId\":\"%s\",\"secondSimCardId\":\"%s\"}"
                .formatted(simA, foreignSim))
        .andExpect(status().isBadRequest());
  }

  // --- AC: completion applies the moves with no Agent input, re-checked against the live Fleet ----

  @Test
  void completingATesterRaisedMoveInstallsTheSimCardWithNoAgentInput() throws Exception {
    UUID target = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    UUID requestId = submitMove(simCardId, target);
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\"}").andExpect(status().isOk());

    assertThat(installedSmartphoneIdOf(simCardId)).isEqualTo(target.toString());
  }

  @Test
  void completingAnExchangeSwapsBothSmartphonesAtomically() throws Exception {
    UUID phoneA = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID phoneB = createSmartphone(managerToken, contractId, "iPhone 15");
    UUID simA = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    UUID simB = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    installSimCard(managerToken, simA, phoneA);
    installSimCard(managerToken, simB, phoneB);

    UUID requestId = submitExchange(simA, simB);
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\"}").andExpect(status().isOk());

    assertThat(installedSmartphoneIdOf(simA)).isEqualTo(phoneB.toString());
    assertThat(installedSmartphoneIdOf(simB)).isEqualTo(phoneA.toString());
  }

  @Test
  void completingAMoveThatNoLongerFitsIsRefusedAndChangesNothing() throws Exception {
    UUID target = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID movingSimCard = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    UUID requestId = submitMove(movingSimCard, target);
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    // Fill the destination Smartphone's two slots after submission but before completion.
    installSimCard(managerToken, createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID), target);
    installSimCard(managerToken, createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID), target);

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\"}").andExpect(status().isConflict());

    assertThat(installedSmartphoneIdOf(movingSimCard)).isNull();
  }

  // --- AC: a SIM Swap Request created before this ticket completes without changing the Fleet ----

  @Test
  void aLegacySimSwapRequestCompletesWithoutChangingTheFleet() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    installSimCard(managerToken, simCardId, smartphoneId);

    UUID requestId = insertLegacyRequest();
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    assertThat(installedSmartphoneIdOf(simCardId)).isEqualTo(smartphoneId.toString());
  }

  // --- helpers -----------------------------------------------------------------------------------

  private UUID submitMove(UUID simCardId, UUID smartphoneId) throws Exception {
    MvcResult result =
        postRequest(
                contractId,
                testerToken,
                "{\"type\":\"SIM_SWAP\",\"targetSimCardId\":\"%s\",\"targetSmartphoneId\":\"%s\"}"
                    .formatted(simCardId, smartphoneId))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID submitExchange(UUID firstSimCardId, UUID secondSimCardId) throws Exception {
    MvcResult result =
        postRequest(
                contractId,
                testerToken,
                "{\"type\":\"SIM_SWAP\",\"targetSimCardId\":\"%s\",\"secondSimCardId\":\"%s\"}"
                    .formatted(firstSimCardId, secondSimCardId))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private void installSimCard(String token, UUID simCardId, UUID smartphoneId) throws Exception {
    installSimCard(token, contractId, simCardId, smartphoneId);
  }

  private void installSimCard(String token, UUID onContractId, UUID simCardId, UUID smartphoneId) throws Exception {
    mockMvc
        .perform(
            patch("/api/contracts/" + onContractId + "/sim-cards/" + simCardId + "/installed-in")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"smartphoneId\":\"" + smartphoneId + "\"}"))
        .andExpect(status().isOk());
  }

  private void retireSmartphone(UUID smartphoneId) throws Exception {
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/smartphones/" + smartphoneId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("{\"status\":\"RETIRED\"}"))
        .andExpect(status().isOk());
  }

  /** The Smartphone id {@code simCardId} is currently Installed in, or {@code null} if none. */
  private String installedSmartphoneIdOf(UUID simCardId) throws Exception {
    JsonNode simCards =
        objectMapper.readTree(
            mockMvc
                .perform(
                    get("/api/contracts/" + contractId + "/sim-cards")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    for (JsonNode sim : simCards) {
      if (sim.get("id").asText().equals(simCardId.toString())) {
        return sim.has("installedInSmartphoneId") ? sim.get("installedInSmartphoneId").asText() : null;
      }
    }
    throw new IllegalStateException("No SIM Card with id " + simCardId);
  }

  /**
   * Inserts a Request directly through the repository, bypassing {@code RequestDetailsValidator}
   * entirely — the only way to produce a SIM Swap Request with none of this ticket's details set,
   * exactly as every SIM Swap Request that exists before this ticket ships does (ticket AC: "A SIM
   * Swap Request created before this ticket completes without changing the Fleet"). Mirrors {@link
   * ProvisionRequestDetailsApiTest#insertLegacyRequest}.
   */
  private UUID insertLegacyRequest() {
    Tester tester = testerRepository.findById(testerId).orElseThrow();
    Request request = new Request();
    request.setId(UUID.randomUUID());
    request.setTenant(contractRepository.findById(contractId).orElseThrow().getTenant());
    request.setContract(contractRepository.findById(contractId).orElseThrow());
    request.setTester(tester);
    request.setRaisedByUser(tester.getUser());
    request.setAgentAuthored(false);
    request.setType(RequestType.SIM_SWAP);
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
