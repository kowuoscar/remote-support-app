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
 * Return's own details, approval-by-content, Disposition and completion (spec.md Solution's
 * "Return type"/"Approval"/"Disposition"/"Completion" tables, and Agent Stock's "Kept in Stock"
 * Disposition; return-client-owned-smartphones and manager-decides-return-disposition tickets'
 * ACs). Covers Client-owned Smartphones (no approval needed, fixed {@code POSTED_TO_CLIENT}) and
 * company-owned Smartphones/SIM Cards (Pending Approval, Manager-chosen Disposition — {@code
 * POSTED_TO_COMPANY}/{@code CANCELLED} or {@code KEPT_IN_STOCK}) alike. Mirrors {@link
 * ReplaceRequestsApiTest}'s pattern, with its own helper names per this ticket's instructions (a
 * finisher elsewhere is hoisting {@code postRequest}/{@code patchStatus}/{@code findTesterId} into
 * {@code IntegrationTest} — this file defines its own instead of colliding with that).
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

  // --- AC: a Return naming any SIM Card or company-owned Smartphone starts Pending Approval ------

  @Test
  void aReturnNamingACompanyOwnedSmartphoneStartsPendingApprovalAndLeavesItsDispositionUnset() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");

    postReturnRequest(testerToken, "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(smartphoneId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
        .andExpect(jsonPath("$.returnedUnits[0].smartphoneId").value(smartphoneId.toString()))
        .andExpect(jsonPath("$.returnedUnits[0].disposition").doesNotExist());
  }

  @Test
  void aReturnNamingASimCardStartsPendingApprovalAndLeavesItsDispositionUnset() throws Exception {
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);

    postReturnRequest(testerToken, "{\"type\":\"RETURN\",\"returnedSimCardIds\":[\"%s\"]}".formatted(simCardId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
        .andExpect(jsonPath("$.returnedUnits[0].simCardId").value(simCardId.toString()))
        .andExpect(jsonPath("$.returnedUnits[0].disposition").doesNotExist());
  }

  @Test
  void aMixedReturnOfAClientOwnedAndACompanyOwnedSmartphoneStartsPendingApprovalWhoeverRaisesIt() throws Exception {
    UUID clientOwned = createClientOwnedSmartphone("Pixel 8");
    UUID companyOwned = createSmartphone(managerToken, contractId, "iPhone 15");

    postReturnRequest(
            agentToken,
            "{\"type\":\"RETURN\",\"testerId\":\"%s\",\"returnedSmartphoneIds\":[\"%s\",\"%s\"]}"
                .formatted(returnTesterId, clientOwned, companyOwned))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
  }

  @Test
  void anAgentProactiveReturnOfACompanyOwnedSmartphoneCannotStartImmediatelyCompleted() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");

    postReturnRequest(
            agentToken,
            ("{\"type\":\"RETURN\",\"testerId\":\"%s\",\"startingStatus\":\"COMPLETED\","
                    + "\"returnedSmartphoneIds\":[\"%s\"]}")
                .formatted(returnTesterId, smartphoneId))
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

  // --- AC: approving a Return asks for a Disposition per company-owned unit -----------------------

  @Test
  void approvingIsRefusedUntilEveryCompanyOwnedUnitHasADisposition() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID requestId = submitReturn(smartphoneOnlyBody(smartphoneId));

    approveReturn(requestId, null).andExpect(status().isBadRequest());
  }

  @Test
  void approvingIsRefusedWithADispositionThatDoesNotFitTheUnitKind() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    JsonNode created = postReturnAndParse(testerToken, smartphoneOnlyBody(smartphoneId));
    UUID requestId = requestIdOf(created);
    UUID unitId = returnedUnitId(created, "smartphoneId", smartphoneId);

    approveReturn(requestId, dispositionsBody(unitId, "CANCELLED")).andExpect(status().isBadRequest());
  }

  @Test
  void approvingIsRefusedWithADispositionForAClientOwnedUnit() throws Exception {
    UUID clientOwned = createClientOwnedSmartphone("Pixel 8");
    UUID companyOwned = createSmartphone(managerToken, contractId, "iPhone 15");
    JsonNode created =
        postReturnAndParse(
            testerToken,
            "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\",\"%s\"]}".formatted(clientOwned, companyOwned));
    UUID requestId = requestIdOf(created);
    UUID clientOwnedUnitId = returnedUnitId(created, "smartphoneId", clientOwned);

    approveReturn(requestId, dispositionsBody(clientOwnedUnitId, "POSTED_TO_COMPANY"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void approvingWithFittingDispositionsSucceedsAndTheyShowOnTheRequest() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    JsonNode created =
        postReturnAndParse(
            testerToken,
            "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"],\"returnedSimCardIds\":[\"%s\"]}"
                .formatted(smartphoneId, simCardId));
    UUID requestId = requestIdOf(created);
    UUID smartphoneUnitId = returnedUnitId(created, "smartphoneId", smartphoneId);
    UUID simCardUnitId = returnedUnitId(created, "simCardId", simCardId);

    approveReturn(
            requestId,
            ("{\"dispositions\":[{\"returnedUnitId\":\"%s\",\"disposition\":\"POSTED_TO_COMPANY\"},"
                    + "{\"returnedUnitId\":\"%s\",\"disposition\":\"CANCELLED\"}]}")
                .formatted(smartphoneUnitId, simCardUnitId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        // Units are ordered by creation (findByRequestIdOrderByCreatedAtAsc), and
        // ReturnRequestDetailsHandler builds every named Smartphone before every named SIM Card.
        .andExpect(jsonPath("$.returnedUnits[0].smartphoneId").value(smartphoneId.toString()))
        .andExpect(jsonPath("$.returnedUnits[0].disposition").value("POSTED_TO_COMPANY"))
        .andExpect(jsonPath("$.returnedUnits[1].simCardId").value(simCardId.toString()))
        .andExpect(jsonPath("$.returnedUnits[1].disposition").value("CANCELLED"));
  }

  @Test
  void approvingASecondTimeIsRefusedSoDispositionsCannotBeChanged() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    JsonNode created = postReturnAndParse(testerToken, smartphoneOnlyBody(smartphoneId));
    UUID requestId = requestIdOf(created);
    UUID unitId = returnedUnitId(created, "smartphoneId", smartphoneId);
    approveReturn(requestId, dispositionsBody(unitId, "POSTED_TO_COMPANY")).andExpect(status().isOk());

    approveReturn(requestId, dispositionsBody(unitId, "POSTED_TO_COMPANY")).andExpect(status().isConflict());
  }

  @Test
  void anAgentOrTesterGets403SettingADisposition() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    JsonNode created = postReturnAndParse(testerToken, smartphoneOnlyBody(smartphoneId));
    UUID requestId = requestIdOf(created);
    UUID unitId = returnedUnitId(created, "smartphoneId", smartphoneId);
    String body = dispositionsBody(unitId, "POSTED_TO_COMPANY");

    mockMvc
        .perform(
            post("/api/requests/" + requestId + "/approve")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/requests/" + requestId + "/approve")
                .header("Authorization", "Bearer " + testerToken)
                .contentType(APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  // --- AC: completing asks for an effective cancellation date per cancelled SIM Card ---------------

  @Test
  void completingIsRefusedWithoutACancellationDateForEachCancelledSimCard() throws Exception {
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    JsonNode created = postReturnAndParse(testerToken, simCardOnlyBody(simCardId));
    UUID requestId = requestIdOf(created);
    UUID unitId = returnedUnitId(created, "simCardId", simCardId);
    approveReturn(requestId, dispositionsBody(unitId, "CANCELLED")).andExpect(status().isOk());
    patchReturnStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchReturnStatus(requestId, "{\"status\":\"COMPLETED\"}").andExpect(status().isBadRequest());
  }

  @Test
  void completingACancelledSimCardRetiresUninstallsAndKeepsItsEffectiveDate() throws Exception {
    UUID smartphoneId = createClientOwnedSmartphone("Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    installSimCard(simCardId, smartphoneId);
    JsonNode created = postReturnAndParse(testerToken, simCardOnlyBody(simCardId));
    UUID requestId = requestIdOf(created);
    UUID unitId = returnedUnitId(created, "simCardId", simCardId);
    approveReturn(requestId, dispositionsBody(unitId, "CANCELLED")).andExpect(status().isOk());
    patchReturnStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchReturnStatus(
            requestId,
            "{\"status\":\"COMPLETED\",\"simCardCancellations\":[{\"simCardId\":\"%s\",\"effectiveDate\":\"2026-08-15\"}]}"
                .formatted(simCardId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    JsonNode simCard = simCardById(simCardId);
    assertThat(simCard.get("status").asText()).isEqualTo("RETIRED");
    assertThat(simCard.get("cancellationEffectiveDate").asText()).isEqualTo("2026-08-15");
    assertThat(simCard.has("installedInSmartphoneId")).isFalse();
  }

  // --- AC (agent-stock): Kept in Stock moves a unit into the Contract's Agent's Stock ------------

  @Test
  void approvingWithKeptInStockDispositionsSucceedsForBothUnitKinds() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    JsonNode created =
        postReturnAndParse(
            testerToken,
            "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"],\"returnedSimCardIds\":[\"%s\"]}"
                .formatted(smartphoneId, simCardId));
    UUID requestId = requestIdOf(created);
    UUID smartphoneUnitId = returnedUnitId(created, "smartphoneId", smartphoneId);
    UUID simCardUnitId = returnedUnitId(created, "simCardId", simCardId);

    approveReturn(
            requestId,
            ("{\"dispositions\":[{\"returnedUnitId\":\"%s\",\"disposition\":\"KEPT_IN_STOCK\"},"
                    + "{\"returnedUnitId\":\"%s\",\"disposition\":\"KEPT_IN_STOCK\"}]}")
                .formatted(smartphoneUnitId, simCardUnitId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.returnedUnits[0].disposition").value("KEPT_IN_STOCK"))
        .andExpect(jsonPath("$.returnedUnits[1].disposition").value("KEPT_IN_STOCK"));
  }

  @Test
  void completingMovesAKeptInStockSmartphoneToTheAgentsStockUninstalledAndOffTheFleet() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, SEEDED_US_CARRIER_ID);
    installSimCard(simCardId, smartphoneId);
    JsonNode created = postReturnAndParse(testerToken, smartphoneOnlyBody(smartphoneId));
    UUID requestId = requestIdOf(created);
    UUID unitId = returnedUnitId(created, "smartphoneId", smartphoneId);
    approveReturn(requestId, dispositionsBody(unitId, "KEPT_IN_STOCK")).andExpect(status().isOk());
    patchReturnStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchReturnStatus(requestId, "{\"status\":\"COMPLETED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // Off the Contract's Fleet entirely (ticket AC: "appears on no Fleet") — not merely retired.
    assertThat(fleetSmartphoneIds()).doesNotContain(smartphoneId.toString());
    // Its installed SIM Card stays in the Fleet, uninstalled (mirrors the Posted dispositions).
    JsonNode simCard = simCardById(simCardId);
    assertThat(simCard.get("status").asText()).isEqualTo("ACTIVE");
    assertThat(simCard.has("installedInSmartphoneId")).isFalse();

    JsonNode stock = readStock(agentToken);
    JsonNode stockUnit = stockUnitById(stock, smartphoneId);
    assertThat(stockUnit.get("kind").asText()).isEqualTo("SMARTPHONE");
    assertThat(stockUnit.get("status").asText()).isEqualTo("ACTIVE");
    assertThat(stockUnit.get("model").asText()).isEqualTo("Pixel 8");
    assertThat(stockUnit.get("fromContractId").asText()).isEqualTo(contractId.toString());
  }

  @Test
  void completingMovesAKeptInStockPostpaidSimCardToStockAndOutOfTheBaseAmount() throws Exception {
    UUID smartphoneId = createClientOwnedSmartphone("Pixel 8");
    UUID simCardId = createPostpaidSimCard("+1-555-0199", "45.00");
    installSimCard(simCardId, smartphoneId);

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseAmount").value(45.00));

    JsonNode created = postReturnAndParse(testerToken, simCardOnlyBody(simCardId));
    UUID requestId = requestIdOf(created);
    UUID unitId = returnedUnitId(created, "simCardId", simCardId);
    approveReturn(requestId, dispositionsBody(unitId, "KEPT_IN_STOCK")).andExpect(status().isOk());
    patchReturnStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
    patchReturnStatus(requestId, "{\"status\":\"COMPLETED\"}").andExpect(status().isOk());

    // Fleet base amount: falls out of the contract-scoped query naturally (a Stock SIM has no
    // Contract) — ticket AC: "counts on no invoice base amount".
    mockMvc
        .perform(get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseAmount").value(0));

    assertThat(fleetSimCardIds()).doesNotContain(simCardId.toString());
    JsonNode stock = readStock(agentToken);
    JsonNode stockUnit = stockUnitById(stock, simCardId);
    assertThat(stockUnit.get("kind").asText()).isEqualTo("SIM_CARD");
    assertThat(stockUnit.get("monthlyFeeAmount").asDouble()).isEqualTo(45.00);
    assertThat(stockUnit.has("installedInSmartphoneId")).isFalse();
  }

  @Test
  void aKeptInStockUnitCannotBeNamedAsAnotherRequestsTarget() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    JsonNode created = postReturnAndParse(testerToken, smartphoneOnlyBody(smartphoneId));
    UUID requestId = requestIdOf(created);
    UUID unitId = returnedUnitId(created, "smartphoneId", smartphoneId);
    approveReturn(requestId, dispositionsBody(unitId, "KEPT_IN_STOCK")).andExpect(status().isOk());
    patchReturnStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
    patchReturnStatus(requestId, "{\"status\":\"COMPLETED\"}").andExpect(status().isOk());

    // A Stock unit is no longer "on this Contract" — a Reboot naming it as its target is refused
    // exactly like an unknown id (ticket AC: "can't be the target of a Request").
    postReturnRequest(testerToken, "{\"type\":\"REBOOT\",\"targetSmartphoneId\":\"%s\"}".formatted(smartphoneId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void completingPostsACompanyOwnedSmartphoneToCompanyAndRetiresIt() throws Exception {
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    JsonNode created = postReturnAndParse(testerToken, smartphoneOnlyBody(smartphoneId));
    UUID requestId = requestIdOf(created);
    UUID unitId = returnedUnitId(created, "smartphoneId", smartphoneId);
    approveReturn(requestId, dispositionsBody(unitId, "POSTED_TO_COMPANY")).andExpect(status().isOk());
    patchReturnStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());

    patchReturnStatus(requestId, "{\"status\":\"COMPLETED\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    assertThat(smartphoneStatus(smartphoneId)).isEqualTo("RETIRED");
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

  private String smartphoneOnlyBody(UUID smartphoneId) {
    return "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(smartphoneId);
  }

  private String simCardOnlyBody(UUID simCardId) {
    return "{\"type\":\"RETURN\",\"returnedSimCardIds\":[\"%s\"]}".formatted(simCardId);
  }

  private String dispositionsBody(UUID returnedUnitId, String disposition) {
    return "{\"dispositions\":[{\"returnedUnitId\":\"%s\",\"disposition\":\"%s\"}]}".formatted(returnedUnitId, disposition);
  }

  /** Submits a Return and returns just its Request id, for a test that never needs its body again. */
  private UUID submitReturn(String json) throws Exception {
    return requestIdOf(postReturnAndParse(testerToken, json));
  }

  /** Submits a Return and returns its full parsed creation response, including its returnedUnits. */
  private JsonNode postReturnAndParse(String token, String json) throws Exception {
    MvcResult result = postReturnRequest(token, json).andExpect(status().isCreated()).andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private UUID requestIdOf(JsonNode request) {
    return UUID.fromString(request.get("id").asText());
  }

  /** The {@code ReturnedUnit} row id of the unit whose {@code field} equals {@code value}, e.g. "smartphoneId". */
  private UUID returnedUnitId(JsonNode request, String field, UUID value) {
    for (JsonNode unit : request.get("returnedUnits")) {
      if (unit.has(field) && unit.get(field).asText().equals(value.toString())) {
        return UUID.fromString(unit.get("id").asText());
      }
    }
    throw new IllegalStateException("No returned unit with " + field + "=" + value);
  }

  private UUID createPostpaidSimCard(String number, String price) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/sim-cards")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(postpaidSimCardJson(managerToken, contractId, number, price)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private java.util.List<String> fleetSmartphoneIds() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/contracts/" + contractId + "/smartphones").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode smartphones = objectMapper.readTree(result.getResponse().getContentAsString());
    java.util.List<String> ids = new java.util.ArrayList<>();
    smartphones.forEach(smartphone -> ids.add(smartphone.get("id").asText()));
    return ids;
  }

  private java.util.List<String> fleetSimCardIds() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode simCards = objectMapper.readTree(result.getResponse().getContentAsString());
    java.util.List<String> ids = new java.util.ArrayList<>();
    simCards.forEach(simCard -> ids.add(simCard.get("id").asText()));
    return ids;
  }

  private JsonNode readStock(String token) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/stock").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode stockUnitById(JsonNode stock, UUID unitId) {
    for (JsonNode unit : stock) {
      if (unit.get("id").asText().equals(unitId.toString())) {
        return unit;
      }
    }
    throw new IllegalStateException("No Stock unit with id " + unitId);
  }

  private ResultActions approveReturn(UUID requestId, String json) throws Exception {
    var request =
        post("/api/requests/" + requestId + "/approve").header("Authorization", "Bearer " + managerToken);
    if (json != null) {
      request = request.contentType(APPLICATION_JSON).content(json);
    }
    return mockMvc.perform(request);
  }
}
