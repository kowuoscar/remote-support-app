package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Replace Smartphone and Replace SIM carry the unit to retire at submission, and complete by
 * retiring it and adding its replacement (replace-requests ticket: spec.md Solution's "Details at
 * submission"/"Fleet changes on completion" tables, for these two types; ticket ACs). Mirrors
 * {@link ProvisionRequestDetailsApiTest}'s pattern.
 */
class ReplaceRequestsApiTest extends IntegrationTest {

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

  // --- AC: a Replace Smartphone Request requires an Active Smartphone of its Contract ------------

  @Test
  void aReplaceSmartphoneRequestWithoutATargetIsRefused() throws Exception {
    postRequest(testerToken, "{\"type\":\"REPLACE_SMARTPHONE\"}").andExpect(status().isBadRequest());
  }

  @Test
  void aReplaceSmartphoneRequestNamingASmartphoneOfAnotherContractIsRefused() throws Exception {
    UUID otherClient = createClient(managerToken, "Solene Cosmetics");
    UUID otherContract = createContract(managerToken, otherClient, SEEDED_AGENT_ID);
    UUID foreignSmartphone = createSmartphone(managerToken, otherContract, "Foreign Phone");

    postRequest(testerToken, "{\"type\":\"REPLACE_SMARTPHONE\",\"targetSmartphoneId\":\"%s\"}".formatted(foreignSmartphone))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aReplaceSmartphoneRequestNamingARetiredSmartphoneIsRefused() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    retireSmartphone(smartphoneId);

    postRequest(testerToken, "{\"type\":\"REPLACE_SMARTPHONE\",\"targetSmartphoneId\":\"%s\"}".formatted(smartphoneId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aTesterSubmittedReplaceSmartphoneRequestCarriesItsTargetAndOptionalModel() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");

    postRequest(
            testerToken,
            "{\"type\":\"REPLACE_SMARTPHONE\",\"targetSmartphoneId\":\"%s\",\"requestedModel\":\"Pixel 9\"}"
                .formatted(smartphoneId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.targetSmartphoneId").value(smartphoneId.toString()))
        .andExpect(jsonPath("$.targetSmartphoneModel").value("Pixel 8"))
        .andExpect(jsonPath("$.requestedModel").value("Pixel 9"));
  }

  @Test
  void aReplaceSmartphoneRequestMayOmitTheRequestedModel() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");

    postRequest(testerToken, "{\"type\":\"REPLACE_SMARTPHONE\",\"targetSmartphoneId\":\"%s\"}".formatted(smartphoneId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.requestedModel").doesNotExist());
  }

  @Test
  void anAgentProactiveReplaceSmartphoneRequestAlsoRequiresATarget() throws Exception {
    postRequest(agentToken, "{\"type\":\"REPLACE_SMARTPHONE\",\"testerId\":\"%s\"}".formatted(testerId))
        .andExpect(status().isBadRequest());
  }

  // --- AC: a Replace SIM Request requires an Active SIM Card of its Contract ---------------------

  @Test
  void aReplaceSimRequestWithoutATargetIsRefused() throws Exception {
    postRequest(testerToken, "{\"type\":\"REPLACE_SIM\"}").andExpect(status().isBadRequest());
  }

  @Test
  void aReplaceSimRequestNamingARetiredSimCardIsRefused() throws Exception {
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    retireSimCard(simCardId);

    postRequest(testerToken, "{\"type\":\"REPLACE_SIM\",\"targetSimCardId\":\"%s\"}".formatted(simCardId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aTesterSubmittedReplaceSimRequestCarriesItsTarget() throws Exception {
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);

    postRequest(testerToken, "{\"type\":\"REPLACE_SIM\",\"targetSimCardId\":\"%s\"}".formatted(simCardId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.targetSimCardId").value(simCardId.toString()))
        .andExpect(jsonPath("$.targetSimCardNumber").exists());
  }

  @Test
  void anAgentProactiveReplaceSimRequestAlsoRequiresATarget() throws Exception {
    postRequest(agentToken, "{\"type\":\"REPLACE_SIM\",\"testerId\":\"%s\"}".formatted(testerId))
        .andExpect(status().isBadRequest());
  }

  // --- AC: completing a Replace Smartphone needs no Agent input -----------------------------------

  @Test
  void completingAReplaceSmartphoneRetiresTheOldOneAndAddsACompanyOwnedReplacementWithTheSameModel()
      throws Exception {
    UUID oldId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID requestId = submitReplaceSmartphone(oldId, null);
    patchStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(requestId, "{\"status\":\"COMPLETED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    JsonNode smartphones = smartphones();
    assertThat(smartphones).hasSize(2);
    boolean oldRetired = false;
    boolean newActive = false;
    for (JsonNode phone : smartphones) {
      if (phone.get("id").asText().equals(oldId.toString())) {
        oldRetired = "RETIRED".equals(phone.get("status").asText());
      } else {
        newActive =
            "Pixel 8".equals(phone.get("model").asText())
                && "ACTIVE".equals(phone.get("status").asText())
                && "COMPANY".equals(phone.get("owner").asText())
                && (!phone.has("serial") || phone.get("serial").isNull());
      }
    }
    assertThat(oldRetired).as("the named Smartphone is retired").isTrue();
    assertThat(newActive).as("a new company-owned Smartphone with the same model is added").isTrue();
  }

  @Test
  void completingAReplaceSmartphoneWithARequestedModelUsesItInstead() throws Exception {
    UUID oldId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID requestId = submitReplaceSmartphone(oldId, "Pixel 9 Pro");
    patchStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(requestId, "{\"status\":\"COMPLETED\"}").andExpect(status().isOk());

    JsonNode smartphones = smartphones();
    boolean newModelPresent = false;
    for (JsonNode phone : smartphones) {
      if ("Pixel 9 Pro".equals(phone.get("model").asText())) {
        newModelPresent = true;
      }
    }
    assertThat(newModelPresent).isTrue();
  }

  @Test
  void completingAReplaceSmartphoneCarriesItsSimCardsOntoTheReplacement() throws Exception {
    UUID oldId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simA = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    UUID simB = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    installSimCard(simA, oldId);
    installSimCard(simB, oldId);

    UUID requestId = submitReplaceSmartphone(oldId, null);
    patchStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
    patchStatus(requestId, "{\"status\":\"COMPLETED\"}").andExpect(status().isOk());

    UUID newSmartphoneId = null;
    for (JsonNode phone : smartphones()) {
      if (!phone.get("id").asText().equals(oldId.toString())) {
        newSmartphoneId = UUID.fromString(phone.get("id").asText());
      }
    }
    assertThat(newSmartphoneId).isNotNull();

    JsonNode simCards = simCards();
    int installedInNew = 0;
    for (JsonNode sim : simCards) {
      if (sim.has("installedInSmartphoneId") && sim.get("installedInSmartphoneId").asText().equals(newSmartphoneId.toString())) {
        installedInNew++;
      }
    }
    assertThat(installedInNew).as("both SIM Cards moved onto the replacement").isEqualTo(2);
  }

  @Test
  void completingAReplaceSmartphoneIsRefusedIfTheNamedUnitIsNoLongerActive() throws Exception {
    UUID oldId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID requestId = submitReplaceSmartphone(oldId, null);
    patchStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    retireSmartphone(oldId);

    patchStatus(requestId, "{\"status\":\"COMPLETED\"}").andExpect(status().isConflict());
  }

  @Test
  void anAgentProactiveReplaceSmartphoneStartingCompletedNeedsNoAgentInputEither() throws Exception {
    UUID oldId = createSmartphone(managerToken, contractId, "Pixel 8");

    postRequest(
            agentToken,
            ("{\"type\":\"REPLACE_SMARTPHONE\",\"testerId\":\"%s\",\"startingStatus\":\"COMPLETED\","
                    + "\"targetSmartphoneId\":\"%s\"}")
                .formatted(testerId, oldId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    assertThat(smartphones()).hasSize(2);
  }

  // --- Observability: a unit-replaced audit event ---------------------------------------------

  @Test
  void completingAReplaceSmartphoneLogsAUnitReplacedAuditEvent() throws Exception {
    UUID oldId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID requestId = submitReplaceSmartphone(oldId, null);
    patchStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);
    try {
      patchStatus(requestId, "{\"status\":\"COMPLETED\"}").andExpect(status().isOk());

      String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertThat(logged)
          .contains("action=UNIT_REPLACED")
          .contains("entity=Smartphone")
          .contains("requestId=" + requestId)
          .contains("retiredUnitId=" + oldId);
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  // --- AC: completing a Replace SIM asks for the new SIM Card's details ---------------------------

  @Test
  void completingAReplaceSimRetiresTheOldOneAndInstallsTheNewOneInItsSmartphone() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID oldSimId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    installSimCard(oldSimId, smartphoneId);

    UUID requestId = submitReplaceSim(oldSimId);
    patchStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(
            requestId,
            ("{\"status\":\"COMPLETED\",\"newSimCard\":{\"number\":\"+1-555-0177\",\"carrierId\":\"%s\","
                    + "\"flavor\":\"PREPAID\"}}")
                .formatted(SEEDED_US_CARRIER_ID))
        .andExpect(status().isOk());

    JsonNode simCards = simCards();
    JsonNode oldSim = null;
    JsonNode newSim = null;
    for (JsonNode sim : simCards) {
      if (sim.get("id").asText().equals(oldSimId.toString())) {
        oldSim = sim;
      }
      if ("+1-555-0177".equals(sim.get("number").asText())) {
        newSim = sim;
      }
    }
    assertThat(oldSim).isNotNull();
    assertThat(oldSim.get("status").asText()).isEqualTo("RETIRED");
    assertThat(newSim).isNotNull();
    assertThat(newSim.get("installedInSmartphoneId").asText()).isEqualTo(smartphoneId.toString());
  }

  @Test
  void completingAReplaceSimWithoutNewSimCardDetailsIsRejected() throws Exception {
    UUID oldSimId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    UUID requestId = submitReplaceSim(oldSimId);
    patchStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(requestId, "{\"status\":\"COMPLETED\"}").andExpect(status().isBadRequest());
  }

  @Test
  void completingAReplaceSimIsRefusedIfTheNamedUnitIsNoLongerActive() throws Exception {
    UUID oldSimId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    UUID requestId = submitReplaceSim(oldSimId);
    patchStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    retireSimCard(oldSimId);

    patchStatus(
            requestId,
            ("{\"status\":\"COMPLETED\",\"newSimCard\":{\"number\":\"+1-555-0177\",\"carrierId\":\"%s\","
                    + "\"flavor\":\"PREPAID\"}}")
                .formatted(SEEDED_US_CARRIER_ID))
        .andExpect(status().isConflict());
  }

  @Test
  void theNewSimCardObeysThePostpaidPlanRule() throws Exception {
    UUID oldSimId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    UUID requestId = submitReplaceSim(oldSimId);
    patchStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(
            requestId,
            ("{\"status\":\"COMPLETED\",\"newSimCard\":{\"number\":\"+1-555-0188\",\"carrierId\":\"%s\","
                    + "\"flavor\":\"POSTPAID\"}}")
                .formatted(SEEDED_US_CARRIER_ID))
        .andExpect(status().isBadRequest());
  }

  @Test
  void theNewSimCardObeysTheCarrierCountryRule() throws Exception {
    UUID otherCarrier = createCarrier(managerToken, Country.SPAIN, "Fixture Spanish Carrier");
    UUID oldSimId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    UUID requestId = submitReplaceSim(oldSimId);
    patchStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(
            requestId,
            ("{\"status\":\"COMPLETED\",\"newSimCard\":{\"number\":\"+1-555-0199\",\"carrierId\":\"%s\","
                    + "\"flavor\":\"PREPAID\"}}")
                .formatted(otherCarrier))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aReplaceSimNotCurrentlyInstalledCompletesWithTheNewOneAlsoUninstalled() throws Exception {
    UUID oldSimId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    UUID requestId = submitReplaceSim(oldSimId);
    patchStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(
            requestId,
            ("{\"status\":\"COMPLETED\",\"newSimCard\":{\"number\":\"+1-555-0166\",\"carrierId\":\"%s\","
                    + "\"flavor\":\"PREPAID\"}}")
                .formatted(SEEDED_US_CARRIER_ID))
        .andExpect(status().isOk());

    JsonNode newSim = null;
    for (JsonNode sim : simCards()) {
      if ("+1-555-0166".equals(sim.get("number").asText())) {
        newSim = sim;
      }
    }
    assertThat(newSim).isNotNull();
    assertThat(newSim.has("installedInSmartphoneId")).isFalse();
  }

  @Test
  void anAgentProactiveReplaceSimStartingCompletedRequiresNewSimCardDetails() throws Exception {
    UUID oldSimId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);

    postRequest(
            agentToken,
            ("{\"type\":\"REPLACE_SIM\",\"testerId\":\"%s\",\"startingStatus\":\"COMPLETED\","
                    + "\"targetSimCardId\":\"%s\"}")
                .formatted(testerId, oldSimId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anAgentProactiveReplaceSimStartingCompletedWithNewSimCardDetailsProvisionsIt() throws Exception {
    UUID oldSimId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);

    postRequest(
            agentToken,
            ("{\"type\":\"REPLACE_SIM\",\"testerId\":\"%s\",\"startingStatus\":\"COMPLETED\","
                    + "\"targetSimCardId\":\"%s\",\"newSimCard\":{\"number\":\"+1-555-0122\","
                    + "\"carrierId\":\"%s\",\"flavor\":\"PREPAID\"}}")
                .formatted(testerId, oldSimId, SEEDED_US_CARRIER_ID))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    boolean found = false;
    for (JsonNode sim : simCards()) {
      if ("+1-555-0122".equals(sim.get("number").asText())) {
        found = true;
      }
    }
    assertThat(found).isTrue();
  }

  // --- helpers -----------------------------------------------------------------------------------

  private UUID submitReplaceSmartphone(UUID targetSmartphoneId, String requestedModel) throws Exception {
    String json =
        requestedModel == null
            ? "{\"type\":\"REPLACE_SMARTPHONE\",\"targetSmartphoneId\":\"%s\"}".formatted(targetSmartphoneId)
            : "{\"type\":\"REPLACE_SMARTPHONE\",\"targetSmartphoneId\":\"%s\",\"requestedModel\":\"%s\"}"
                .formatted(targetSmartphoneId, requestedModel);
    MvcResult result = postRequest(testerToken, json).andExpect(status().isCreated()).andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID submitReplaceSim(UUID targetSimCardId) throws Exception {
    MvcResult result =
        postRequest(testerToken, "{\"type\":\"REPLACE_SIM\",\"targetSimCardId\":\"%s\"}".formatted(targetSimCardId))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private ResultActions postRequest(String token, String json) throws Exception {
    return mockMvc.perform(
        post("/api/contracts/" + contractId + "/requests")
            .header("Authorization", "Bearer " + token)
            .contentType(APPLICATION_JSON)
            .content(json));
  }

  private ResultActions patchStatus(UUID requestId, String json) throws Exception {
    return mockMvc.perform(
        patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
            .header("Authorization", "Bearer " + agentToken)
            .contentType(APPLICATION_JSON)
            .content(json));
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

  private void retireSimCard(UUID simCardId) throws Exception {
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simCardId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("{\"status\":\"RETIRED\"}"))
        .andExpect(status().isOk());
  }

  private void installSimCard(UUID simCardId, UUID smartphoneId) throws Exception {
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simCardId + "/installed-in")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("{\"smartphoneId\":\"%s\"}".formatted(smartphoneId)))
        .andExpect(status().isOk());
  }

  private JsonNode smartphones() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/contracts/" + contractId + "/smartphones").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode simCards() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
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
