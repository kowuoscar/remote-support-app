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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Completing a Provision Smartphone, Provision SIM, Replace Smartphone or Replace SIM Request
 * from the Contract's own Agent's Stock instead of adding a new unit (returns-and-agent-stock
 * spec, Solution's Fulfilment from Stock; fulfil-from-stock ticket ACs). Mirrors {@link
 * StockApiTest}'s own "put a unit into Stock via a completed Return" fixture shape, and {@link
 * ProvisionRequestDetailsApiTest}'s/{@link ReplaceRequestsApiTest}'s own submit-then-approve
 * fixture helpers for the four types this ticket extends.
 */
class StockFulfilmentApiTest extends IntegrationTest {

  private String managerToken;
  private String agentToken;
  private String testerToken;
  private UUID contractId;
  private UUID testerId;

  @BeforeEach
  void setUp() throws Exception {
    managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Outfitters");
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    testerToken = createTesterAndLogin(managerToken, clientId, "noor.abadi@meridian.example", "Passw0rd!23");
    agentToken = agentToken();
    testerId = findTesterId(managerToken, contractId, "noor.abadi@meridian.example");
  }

  // --- AC: Provision Smartphone / Replace Smartphone may fulfil from Stock -----------------------

  @Test
  void completingAProvisionSmartphoneFromStockMovesTheStockUnitOntoTheContractInsteadOfAddingANewOne()
      throws Exception {
    UUID stockSmartphoneId = keepSmartphoneInStock("Pixel 8");
    UUID requestId = submitProvisionSmartphone("Galaxy S24");
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(
            contractId,
            requestId,
            agentToken,
            "{\"status\":\"COMPLETED\",\"fulfillFromStockSmartphoneId\":\"%s\"}".formatted(stockSmartphoneId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    JsonNode fleet = fleetSmartphones();
    assertThat(idsOf(fleet)).containsExactly(stockSmartphoneId.toString());
    JsonNode unit = byId(fleet, stockSmartphoneId);
    assertThat(unit.get("model").asText()).isEqualTo("Pixel 8");
    assertThat(unit.get("owner").asText()).isEqualTo("COMPANY");
    assertThat(unit.get("status").asText()).isEqualTo("ACTIVE");

    assertThat(idsOf(readStock(agentToken))).doesNotContain(stockSmartphoneId.toString());
  }

  @Test
  void completingAReplaceSmartphoneFromStockUsesTheStockUnitAndCarriesSimCardsOver() throws Exception {
    UUID oldSmartphoneId = createSmartphone(managerToken, contractId, "iPhone 13");
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    installSimCard(simCardId, oldSmartphoneId);
    UUID stockSmartphoneId = keepSmartphoneInStock("Pixel 8");
    UUID requestId = submitReplaceSmartphone(oldSmartphoneId);
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(
            contractId,
            requestId,
            agentToken,
            "{\"status\":\"COMPLETED\",\"fulfillFromStockSmartphoneId\":\"%s\"}".formatted(stockSmartphoneId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    assertThat(smartphoneStatus(oldSmartphoneId)).isEqualTo("RETIRED");
    JsonNode fleet = fleetSmartphones();
    JsonNode newUnit = byId(fleet, stockSmartphoneId);
    assertThat(newUnit.get("status").asText()).isEqualTo("ACTIVE");

    // The old Smartphone's SIM Card carried over onto its replacement (replace-requests AC,
    // unchanged by Stock fulfilment).
    JsonNode simCard = byId(fleetSimCards(), simCardId);
    assertThat(simCard.get("installedInSmartphoneId").asText()).isEqualTo(stockSmartphoneId.toString());

    assertThat(idsOf(readStock(agentToken))).doesNotContain(stockSmartphoneId.toString());
  }

  // --- AC: Provision SIM / Replace SIM may fulfil from a matching Stock SIM Card ------------------

  @Test
  void completingAProvisionSimFromAMatchingStockSimCardKeepsItsNumberAndFeeAndAsksForNoNumber() throws Exception {
    UUID planId = createPostpaidPlan(managerToken, SEEDED_US_CARRIER_ID, "Stock Match Plan", "60.00");
    UUID stockSimCardId = keepPostpaidSimCardInStock(SEEDED_US_CARRIER_ID, planId, "+1-555-0301", "60.00");

    UUID requestId =
        submitProvisionSim(
            ("{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"POSTPAID\",\"requestedCarrierId\":\"%s\","
                    + "\"requestedPostpaidPlanId\":\"%s\"}")
                .formatted(SEEDED_US_CARRIER_ID, planId));
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(
            contractId,
            requestId,
            agentToken,
            "{\"status\":\"COMPLETED\",\"fulfillFromStockSimCardId\":\"%s\"}".formatted(stockSimCardId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    JsonNode fleet = fleetSimCards();
    assertThat(idsOf(fleet)).containsExactly(stockSimCardId.toString());
    JsonNode unit = byId(fleet, stockSimCardId);
    assertThat(unit.get("number").asText()).isEqualTo("+1-555-0301");
    assertThat(unit.get("monthlyFeeAmount").asDouble()).isEqualTo(60.00);
    assertThat(unit.get("postpaidPlanId").asText()).isEqualTo(planId.toString());

    assertThat(idsOf(readStock(agentToken))).doesNotContain(stockSimCardId.toString());
  }

  @Test
  void aFulfilledPostpaidSimCardsFeeEntersTheBaseAmount() throws Exception {
    UUID planId = createPostpaidPlan(managerToken, SEEDED_US_CARRIER_ID, "Base Amount Plan", "37.50");
    UUID stockSimCardId = keepPostpaidSimCardInStock(SEEDED_US_CARRIER_ID, planId, "+1-555-0302", "37.50");

    UUID requestId =
        submitProvisionSim(
            ("{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"POSTPAID\",\"requestedCarrierId\":\"%s\","
                    + "\"requestedPostpaidPlanId\":\"%s\"}")
                .formatted(SEEDED_US_CARRIER_ID, planId));
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
    patchStatus(
            contractId,
            requestId,
            agentToken,
            "{\"status\":\"COMPLETED\",\"fulfillFromStockSimCardId\":\"%s\"}".formatted(stockSimCardId))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseAmount").value(37.50));
  }

  @Test
  void completingAReplaceSimFromAMatchingStockSimCardKeepsItsNumberAndFee() throws Exception {
    UUID oldSimId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    UUID stockSimCardId = keepPrepaidSimCardInStock(SEEDED_US_CARRIER_ID, "+1-555-0303");
    UUID requestId = submitReplaceSim(oldSimId);
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(
            contractId,
            requestId,
            agentToken,
            "{\"status\":\"COMPLETED\",\"fulfillFromStockSimCardId\":\"%s\"}".formatted(stockSimCardId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    assertThat(simCardStatus(oldSimId)).isEqualTo("RETIRED");
    JsonNode fleet = fleetSimCards();
    JsonNode newUnit = byId(fleet, stockSimCardId);
    assertThat(newUnit.get("number").asText()).isEqualTo("+1-555-0303");
    assertThat(newUnit.get("status").asText()).isEqualTo("ACTIVE");

    assertThat(idsOf(readStock(agentToken))).doesNotContain(stockSimCardId.toString());
  }

  // --- AC: a Stock unit of another Agent, or a SIM Card that doesn't match, is refused ------------

  @Test
  void aSmartphoneFromAnotherAgentsStockIsRefused() throws Exception {
    UUID otherAgentId = createAgent(managerToken, "Priya Nadar", com.remotesupport.backend.domain.Country.UNITED_KINGDOM);
    UUID otherClientId = createClient(managerToken, "Other Agent's Client");
    UUID otherContractId = createContract(managerToken, otherClientId, otherAgentId);
    UUID otherStockSmartphoneId = keepSmartphoneInStock(otherContractId, "Pixel 6");

    UUID requestId = submitProvisionSmartphone("Galaxy S24");
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(
            contractId,
            requestId,
            agentToken,
            "{\"status\":\"COMPLETED\",\"fulfillFromStockSmartphoneId\":\"%s\"}".formatted(otherStockSmartphoneId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aSimCardThatDoesNotMatchTheRequestsCarrierIsRefused() throws Exception {
    UUID otherCarrierId = createCarrier(managerToken, com.remotesupport.backend.domain.Country.UNITED_STATES, "Mint Mobile");
    UUID stockSimCardId = keepPrepaidSimCardInStock(otherCarrierId, "+1-555-0304");

    UUID requestId =
        submitProvisionSim(
            "{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"PREPAID\",\"requestedCarrierId\":\"%s\"}"
                .formatted(SEEDED_US_CARRIER_ID));
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(
            contractId,
            requestId,
            agentToken,
            "{\"status\":\"COMPLETED\",\"fulfillFromStockSimCardId\":\"%s\"}".formatted(stockSimCardId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aSimCardWithTheWrongFlavorIsRefused() throws Exception {
    UUID stockSimCardId = keepPrepaidSimCardInStock(SEEDED_US_CARRIER_ID, "+1-555-0305");
    UUID planId = createPostpaidPlan(managerToken, SEEDED_US_CARRIER_ID, "Wrong Flavor Plan", "20.00");

    UUID requestId =
        submitProvisionSim(
            ("{\"type\":\"PROVISION_SIM\",\"requestedFlavor\":\"POSTPAID\",\"requestedCarrierId\":\"%s\","
                    + "\"requestedPostpaidPlanId\":\"%s\"}")
                .formatted(SEEDED_US_CARRIER_ID, planId));
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(
            contractId,
            requestId,
            agentToken,
            "{\"status\":\"COMPLETED\",\"fulfillFromStockSimCardId\":\"%s\"}".formatted(stockSimCardId))
        .andExpect(status().isBadRequest());
  }

  // --- AC: naming nothing from Stock completes exactly as before -----------------------------------

  @Test
  void namingNothingFromStockStillCompletesAProvisionSmartphoneTheOldWay() throws Exception {
    UUID requestId = submitProvisionSmartphone("Galaxy S24");
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    JsonNode fleet = fleetSmartphones();
    assertThat(fleet.size()).isEqualTo(1);
    assertThat(fleet.get(0).get("model").asText()).isEqualTo("Galaxy S24");
    assertThat(fleet.get(0).get("owner").asText()).isEqualTo("COMPANY");
  }

  // --- helpers --------------------------------------------------------------------------------

  private UUID keepSmartphoneInStock(String model) throws Exception {
    return keepSmartphoneInStock(contractId, model);
  }

  /** Puts a fresh company-owned Smartphone into its Contract's own Agent's Stock via a completed Return. */
  private UUID keepSmartphoneInStock(UUID onContractId, String model) throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, onContractId, model);
    String testerTokenForContract = onContractId.equals(contractId) ? testerToken : testerTokenFor(onContractId);

    MvcResult created =
        mockMvc
            .perform(
                post("/api/contracts/" + onContractId + "/requests")
                    .header("Authorization", "Bearer " + testerTokenForContract)
                    .contentType(APPLICATION_JSON)
                    .content("{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(smartphoneId)))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode createdBody = objectMapper.readTree(created.getResponse().getContentAsString());
    UUID requestId = UUID.fromString(createdBody.get("id").asText());
    UUID unitId = UUID.fromString(createdBody.get("returnedUnits").get(0).get("id").asText());

    mockMvc
        .perform(
            post("/api/requests/" + requestId + "/approve")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"dispositions\":[{\"returnedUnitId\":\"%s\",\"disposition\":\"KEPT_IN_STOCK\"}]}"
                        .formatted(unitId)))
        .andExpect(status().isOk());

    String agentTokenForContract = onContractId.equals(contractId) ? agentToken : agentTokenFor(onContractId);
    mockMvc
        .perform(
            patch("/api/contracts/" + onContractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentTokenForContract)
                .contentType(APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}"))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            patch("/api/contracts/" + onContractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentTokenForContract)
                .contentType(APPLICATION_JSON)
                .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isOk());

    return smartphoneId;
  }

  /** A fresh Tester logged into {@code onContractId}'s own Client, for a fixture on another Agent's Contract. */
  private String testerTokenFor(UUID onContractId) throws Exception {
    JsonNode contract = contractJson(onContractId);
    UUID clientId = UUID.fromString(contract.get("clientId").asText());
    return createTesterAndLogin(
        managerToken, clientId, "fixture+" + UUID.randomUUID() + "@example.com", "Passw0rd!23");
  }

  private String agentTokenFor(UUID onContractId) {
    // Every fixture Agent this test creates is fresh and login-less for sign-in purposes other
    // than the seeded one, so completing a Return on another Agent's Contract is done as the
    // Manager instead — allowed by RequestAccessGuard#requireCanChangeStatus exactly like an
    // Agent's own Contract.
    return managerToken;
  }

  private JsonNode contractJson(UUID id) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/contracts").header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andReturn();
    for (JsonNode contract : objectMapper.readTree(result.getResponse().getContentAsString())) {
      if (contract.get("id").asText().equals(id.toString())) {
        return contract;
      }
    }
    throw new IllegalStateException("No contract with id " + id);
  }

  /** Puts a fresh Prepaid SIM Card into the seeded Agent's Stock via a completed Return. */
  private UUID keepPrepaidSimCardInStock(UUID carrierId, String number) throws Exception {
    UUID simCardId = createNumberedSimCard(carrierId, number, null, null);
    return keepSimCardInStock(simCardId);
  }

  /** Puts a fresh Postpaid SIM Card naming {@code planId} into the seeded Agent's Stock via a completed Return. */
  private UUID keepPostpaidSimCardInStock(UUID carrierId, UUID planId, String number, String price) throws Exception {
    UUID simCardId = createNumberedSimCard(carrierId, number, planId, price);
    return keepSimCardInStock(simCardId);
  }

  private UUID createNumberedSimCard(UUID carrierId, String number, UUID planId, String price) throws Exception {
    String json =
        planId == null
            ? "{\"number\":\"%s\",\"carrierId\":\"%s\",\"flavor\":\"PREPAID\"}".formatted(number, carrierId)
            : "{\"number\":\"%s\",\"carrierId\":\"%s\",\"flavor\":\"POSTPAID\",\"postpaidPlanId\":\"%s\"}"
                .formatted(number, carrierId, planId);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/sim-cards")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID keepSimCardInStock(UUID simCardId) throws Exception {
    MvcResult created =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/requests")
                    .header("Authorization", "Bearer " + testerToken)
                    .contentType(APPLICATION_JSON)
                    .content("{\"type\":\"RETURN\",\"returnedSimCardIds\":[\"%s\"]}".formatted(simCardId)))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode createdBody = objectMapper.readTree(created.getResponse().getContentAsString());
    UUID requestId = UUID.fromString(createdBody.get("id").asText());
    UUID unitId = UUID.fromString(createdBody.get("returnedUnits").get(0).get("id").asText());

    mockMvc
        .perform(
            post("/api/requests/" + requestId + "/approve")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"dispositions\":[{\"returnedUnitId\":\"%s\",\"disposition\":\"KEPT_IN_STOCK\"}]}"
                        .formatted(unitId)))
        .andExpect(status().isOk());
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
    patchStatus(contractId, requestId, agentToken, "{\"status\":\"COMPLETED\"}").andExpect(status().isOk());

    return simCardId;
  }

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

  private UUID submitReplaceSmartphone(UUID targetSmartphoneId) throws Exception {
    MvcResult result =
        postRequest(
                contractId,
                testerToken,
                "{\"type\":\"REPLACE_SMARTPHONE\",\"targetSmartphoneId\":\"%s\"}".formatted(targetSmartphoneId))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andReturn();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    approveAsManager(managerToken, requestId);
    return requestId;
  }

  private UUID submitReplaceSim(UUID targetSimCardId) throws Exception {
    MvcResult result =
        postRequest(
                contractId,
                testerToken,
                "{\"type\":\"REPLACE_SIM\",\"targetSimCardId\":\"%s\"}".formatted(targetSimCardId))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andReturn();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    approveAsManager(managerToken, requestId);
    return requestId;
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

  private String smartphoneStatus(UUID smartphoneId) throws Exception {
    return byId(fleetSmartphones(), smartphoneId).get("status").asText();
  }

  private String simCardStatus(UUID simCardId) throws Exception {
    return byId(fleetSimCards(), simCardId).get("status").asText();
  }

  private JsonNode fleetSmartphones() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/contracts/" + contractId + "/smartphones").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode fleetSimCards() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode readStock(String token) throws Exception {
    MvcResult result =
        mockMvc.perform(get("/api/stock").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private List<String> idsOf(JsonNode nodes) {
    List<String> ids = new ArrayList<>();
    nodes.forEach(node -> ids.add(node.get("id").asText()));
    return ids;
  }

  private JsonNode byId(JsonNode nodes, UUID id) {
    for (JsonNode node : nodes) {
      if (node.get("id").asText().equals(id.toString())) {
        return node;
      }
    }
    throw new IllegalStateException("No entry with id " + id);
  }
}
