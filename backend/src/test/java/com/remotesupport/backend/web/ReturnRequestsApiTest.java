package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Return's own details and no-approval path (return-client-owned-smartphones ticket: spec.md
 * Solution's "Return type" and the Client-owned rows of its Approval/Disposition/Completion
 * tables; ticket ACs). Only Client-owned Smartphones are accepted in this ticket — any SIM Card,
 * or a company-owned Smartphone, is refused (ticket AC). Mirrors {@link ReplaceRequestsApiTest}'s
 * pattern, with its own helper names per this ticket's instructions (a finisher elsewhere is
 * hoisting {@code postRequest}/{@code patchStatus}/{@code findTesterId} into {@code
 * IntegrationTest} — this file defines its own instead of colliding with that).
 */
class ReturnRequestsApiTest extends IntegrationTest {

  private String managerToken;
  private String agentToken;
  private String testerToken;
  private UUID contractId;
  private UUID returnTesterId;

  @BeforeEach
  void setUp() throws Exception {
    managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Bramblewood Traders");
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    testerToken = createTesterAndLogin(managerToken, clientId, "noor.haddad@bramblewood.example", "Passw0rd!23");
    agentToken = agentToken();
    returnTesterId = returnContractTesterId();
  }

  // --- AC: a Return requires at least one Active unit of its Contract, each at most once --------

  @Test
  void aReturnWithNoUnitsIsRefused() throws Exception {
    postReturnRequest(testerToken, "{\"type\":\"RETURN\"}").andExpect(status().isBadRequest());
  }

  @Test
  void aReturnNamingTheSameSmartphoneTwiceIsRefused() throws Exception {
    UUID smartphoneId = createClientOwnedSmartphone("Pixel 8");

    postReturnRequest(
            testerToken,
            "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\",\"%s\"]}"
                .formatted(smartphoneId, smartphoneId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aReturnNamingASmartphoneOfAnotherContractIsRefused() throws Exception {
    UUID otherClient = createClient(managerToken, "Foreign Fixture Co");
    UUID otherContract = createContract(managerToken, otherClient, SEEDED_AGENT_ID);
    UUID foreignSmartphone = createSmartphone(managerToken, otherContract, "Foreign Phone");

    postReturnRequest(testerToken, "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(foreignSmartphone))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aReturnNamingARetiredSmartphoneIsRefused() throws Exception {
    UUID smartphoneId = createClientOwnedSmartphone("Pixel 8");
    retireSmartphone(smartphoneId);

    postReturnRequest(testerToken, "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(smartphoneId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aReturnNamingACompanyOwnedSmartphoneIsRefusedAsUnsupported() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");

    postReturnRequest(testerToken, "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(smartphoneId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aReturnNamingAnySimCardIsRefusedAsUnsupported() throws Exception {
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);

    postReturnRequest(testerToken, "{\"type\":\"RETURN\",\"returnedSimCardIds\":[\"%s\"]}".formatted(simCardId))
        .andExpect(status().isBadRequest());
  }

  // --- AC: a Return of only Client-owned Smartphones starts at Submitted, or Completed for an Agent ---

  @Test
  void aTesterSubmittedReturnOfAClientOwnedSmartphoneStartsSubmittedAndCarriesItsUnit() throws Exception {
    UUID smartphoneId = createClientOwnedSmartphone("Pixel 8");

    postReturnRequest(testerToken, "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(smartphoneId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.returnedUnits[0].smartphoneId").value(smartphoneId.toString()))
        .andExpect(jsonPath("$.returnedUnits[0].smartphoneModel").value("Pixel 8"))
        .andExpect(jsonPath("$.returnedUnits[0].disposition").value("POSTED_TO_CLIENT"));
  }

  @Test
  void aReturnCanNameSeveralClientOwnedSmartphonesAtOnce() throws Exception {
    UUID first = createClientOwnedSmartphone("Pixel 8");
    UUID second = createClientOwnedSmartphone("iPhone 15");

    postReturnRequest(
            testerToken,
            "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\",\"%s\"]}".formatted(first, second))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.returnedUnits.length()").value(2));
  }

  @Test
  void anAgentProactiveReturnDefaultsToSubmitted() throws Exception {
    UUID smartphoneId = createClientOwnedSmartphone("Pixel 8");

    postReturnRequest(
            agentToken,
            "{\"type\":\"RETURN\",\"testerId\":\"%s\",\"returnedSmartphoneIds\":[\"%s\"]}"
                .formatted(returnTesterId, smartphoneId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUBMITTED"));
  }

  @Test
  void anAgentProactiveReturnMayStartImmediatelyCompleted() throws Exception {
    UUID smartphoneId = createClientOwnedSmartphone("Pixel 8");

    postReturnRequest(
            agentToken,
            ("{\"type\":\"RETURN\",\"testerId\":\"%s\",\"startingStatus\":\"COMPLETED\","
                    + "\"returnedSmartphoneIds\":[\"%s\"]}")
                .formatted(returnTesterId, smartphoneId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    assertThat(smartphoneStatus(smartphoneId)).isEqualTo("RETIRED");
  }

  // --- AC: a Return never carries a Fee ------------------------------------------------------------

  @Test
  void aFeeCannotBeLoggedAgainstAReturnRequest() throws Exception {
    UUID smartphoneId = createClientOwnedSmartphone("Pixel 8");
    MvcResult result =
        postReturnRequest(testerToken, "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(smartphoneId))
            .andExpect(status().isCreated())
            .andReturn();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"requestId\":\"%s\",\"feeType\":\"OTHER\",\"amount\":10.00}".formatted(requestId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aProactiveFeeCannotNameReturnAsItsType() throws Exception {
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"testerId\":\"%s\",\"feeType\":\"RETURN\",\"amount\":10.00}".formatted(returnTesterId)))
        .andExpect(status().isBadRequest());
  }

  // --- AC: completing the Return retires each Smartphone and uninstalls its SIM Cards ------------

  @Test
  void completingAReturnRetiresTheSmartphoneAndLeavesItsUninstalledSimCardInTheFleet() throws Exception {
    UUID smartphoneId = createClientOwnedSmartphone("Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    installSimCard(simCardId, smartphoneId);

    UUID requestId = submitAndCompleteReturn(smartphoneId);

    assertThat(smartphoneStatus(smartphoneId)).isEqualTo("RETIRED");
    JsonNode simCard = simCardById(simCardId);
    assertThat(simCard.get("status").asText()).isEqualTo("ACTIVE");
    assertThat(simCard.has("installedInSmartphoneId")).isFalse();
    assertThat(requestId).isNotNull();
  }

  @Test
  void completionIsRefusedWhenTheNamedSmartphoneIsNoLongerActive() throws Exception {
    UUID smartphoneId = createClientOwnedSmartphone("Pixel 8");
    MvcResult result =
        postReturnRequest(testerToken, "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(smartphoneId))
            .andExpect(status().isCreated())
            .andReturn();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    patchReturnStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    retireSmartphone(smartphoneId);

    patchReturnStatus(requestId, "{\"status\":\"COMPLETED\"}").andExpect(status().isConflict());
  }

  // --- helpers --------------------------------------------------------------------------------

  private UUID submitAndCompleteReturn(UUID smartphoneId) throws Exception {
    MvcResult result =
        postReturnRequest(testerToken, "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(smartphoneId))
            .andExpect(status().isCreated())
            .andReturn();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    patchReturnStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
    patchReturnStatus(requestId, "{\"status\":\"COMPLETED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
    return requestId;
  }

  private UUID createClientOwnedSmartphone(String model) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/smartphones")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content("{\"model\":\"%s\",\"owner\":\"CLIENT\"}".formatted(model)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
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

  private void retireSmartphone(UUID smartphoneId) throws Exception {
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/smartphones/" + smartphoneId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("{\"status\":\"RETIRED\"}"))
        .andExpect(status().isOk());
  }

  private String smartphoneStatus(UUID smartphoneId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/contracts/" + contractId + "/smartphones").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode smartphones = objectMapper.readTree(result.getResponse().getContentAsString());
    for (JsonNode smartphone : smartphones) {
      if (smartphone.get("id").asText().equals(smartphoneId.toString())) {
        return smartphone.get("status").asText();
      }
    }
    throw new IllegalStateException("No smartphone with id " + smartphoneId);
  }

  private JsonNode simCardById(UUID simCardId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode simCards = objectMapper.readTree(result.getResponse().getContentAsString());
    for (JsonNode simCard : simCards) {
      if (simCard.get("id").asText().equals(simCardId.toString())) {
        return simCard;
      }
    }
    throw new IllegalStateException("No sim card with id " + simCardId);
  }

  private ResultActions postReturnRequest(String token, String json) throws Exception {
    return mockMvc.perform(
        post("/api/contracts/" + contractId + "/requests")
            .header("Authorization", "Bearer " + token)
            .contentType(APPLICATION_JSON)
            .content(json));
  }

  private ResultActions patchReturnStatus(UUID requestId, String json) throws Exception {
    return mockMvc.perform(
        patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
            .header("Authorization", "Bearer " + agentToken)
            .contentType(APPLICATION_JSON)
            .content(json));
  }

  private UUID returnContractTesterId() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/contracts/" + contractId + "/testers").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode testers = objectMapper.readTree(result.getResponse().getContentAsString());
    return UUID.fromString(testers.get(0).get("id").asText());
  }
}
