package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Fee;
import com.remotesupport.backend.domain.FeeType;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Fee creation and its traceability rule (spec.md Solution's Fee entity; fee-logging-and-
 * provisioning ticket). Mirrors {@link RequestApiTest}'s pattern: Fees are nested under a
 * Contract exactly like Requests and Fleet.
 */
class FeeApiTest extends IntegrationTest {

  @Autowired private FeeRepository feeRepository;
  @Autowired private ContractRepository contractRepository;

  // Other now requires a description; every other type still submits with none, exactly as
  // before. reboot-and-topup-details ticket: Reboot/Topup now each require their own target unit.
  // provision-request-details ticket: Provision Smartphone/SIM each require their own details too.
  private UUID submitRequest(String testerToken, UUID contractId, String type) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("type", type);
    if ("OTHER".equals(type)) {
      body.put("description", "Screen replacement");
    } else if ("REBOOT".equals(type)) {
      body.put("targetSmartphoneId", createSmartphone(managerToken(), contractId, "Fixture Phone"));
    } else if ("TOPUP".equals(type)) {
      body.put("targetSimCardId", createTopupTargetSimCard(managerToken(), contractId));
      body.put("description", "Top-up needed");
    } else if ("PROVISION_SMARTPHONE".equals(type)) {
      body.put("requestedModel", "Fixture Model");
    } else if ("PROVISION_SIM".equals(type)) {
      body.put("requestedFlavor", "PREPAID");
      body.put("requestedCarrierId", SEEDED_US_CARRIER_ID);
    } else if ("SIM_SWAP".equals(type)) {
      // sim-swap-moves ticket: a SIM Swap now needs a move.
      body.put("targetSmartphoneId", createSmartphone(managerToken(), contractId, "Fixture Phone"));
      body.put("targetSimCardId", createSimCard(managerToken(), contractId, SEEDED_US_CARRIER_ID));
    } else if ("REPLACE_SMARTPHONE".equals(type)) {
      body.put("targetSmartphoneId", createSmartphone(managerToken(), contractId, "Fixture Phone To Replace"));
    } else if ("REPLACE_SIM".equals(type)) {
      body.put("targetSimCardId", createSimCard(managerToken(), contractId, SEEDED_US_CARRIER_ID));
    }
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/requests")
                    .header("Authorization", "Bearer " + testerToken)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  // --- AC: Fee creation for each eligible Request type -----------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {"TOPUP", "OTHER"})
  void agentLogsAFeeAgainstAnExistingRequestOfAnEligibleType(String type) throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, type);

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requestId":"%s","feeType":"%s","amount":45.00,"description":"Covers callout"}
                    """
                        .formatted(requestId, type)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.requestId").value(requestId.toString()))
        .andExpect(jsonPath("$.feeType").value(type))
        .andExpect(jsonPath("$.amount").value(45.00))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.description").value("Covers callout"))
        .andExpect(jsonPath("$.billingMonth").isNotEmpty());
  }

  @Test
  void agentLogsAProvisionSmartphoneFeeAgainstAnExistingRequest() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "owen.reyes@meridian.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, "PROVISION_SMARTPHONE");
    approveAsManager(managerToken, requestId);

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requestId":"%s","feeType":"PROVISION_SMARTPHONE","amount":120.00}
                    """
                        .formatted(requestId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.feeType").value("PROVISION_SMARTPHONE"));
  }

  // replace-requests ticket AC: "A Fee can be logged against a Replace Request".
  @Test
  void agentLogsAReplaceSmartphoneFeeAgainstAnExistingRequest() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "charlotte.finch@harborfinch.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, "REPLACE_SMARTPHONE");
    approveAsManager(managerToken, requestId);

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requestId":"%s","feeType":"REPLACE_SMARTPHONE","amount":130.00}
                    """
                        .formatted(requestId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.feeType").value("REPLACE_SMARTPHONE"));
  }

  @Test
  void agentLogsAReplaceSimFeeAgainstAnExistingRequest() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Kessler & Vance LLP");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "helena.voss@kessler.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, "REPLACE_SIM");
    approveAsManager(managerToken, requestId);

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requestId":"%s","feeType":"REPLACE_SIM","amount":12.00}
                    """
                        .formatted(requestId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.feeType").value("REPLACE_SIM"));
  }

  @Test
  void feeCurrencyIsAlwaysInheritedFromTheContractRegardlessOfWhatTheClientRunsElsewhere() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Bright Path Clinics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "marco.diaz@brightpath.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, "TOPUP");

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requestId":"%s","feeType":"TOPUP","amount":10.00}
                    """
                        .formatted(requestId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currency").value("USD"));
  }

  // --- AC: Reboot and a like-for-like SIM Swap never accept a Fee ------------------------------

  @Test
  void loggingAFeeAgainstARebootRequestIsRejected() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "charlotte.finch@harborfinch.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, "REBOOT");

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requestId":"%s","feeType":"TOPUP","amount":10.00}
                    """
                        .formatted(requestId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void loggingAFeeAgainstALikeForLikeSimSwapRequestIsRejected() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Kessler & Vance LLP");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "helena.voss@kessler.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, "SIM_SWAP");

    // A like-for-like swap: no provisioning, no Fee — even attempting a PROVISION_SIM Fee against
    // the Swap Request itself is rejected. A swap that really did require a new physical SIM is
    // logged as its own proactive Provision SIM Fee instead (see the proactive tests below).
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requestId":"%s","feeType":"PROVISION_SIM","amount":15.00}
                    """
                        .formatted(requestId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void feeTypeMustMatchTheLinkedRequestsType() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Solene Cosmetics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "elise.fabron@solene.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, "TOPUP");

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requestId":"%s","feeType":"OTHER","amount":10.00}
                    """
                        .formatted(requestId)))
        .andExpect(status().isBadRequest());
  }

  // --- AC: a proactive Fee (no pre-existing Request) auto-creates its linking Request ----------

  @Test
  void aProactiveFeeAutoCreatesItsLinkingRequestCompletedAndAgentAuthored() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");

    String agentToken = agentToken();
    UUID testerId = findTesterId(agentToken, contractId, "priya.raman@aurora.example");
    UUID targetSimCardId = createTopupTargetSimCard(managerToken, contractId);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/fees")
                    .header("Authorization", "Bearer " + agentToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"feeType":"TOPUP","amount":25.00,"testerId":"%s","targetSimCardId":"%s",
                         "description":"Top-up at kiosk"}
                        """
                            .formatted(testerId, targetSimCardId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.feeType").value("TOPUP"))
            .andExpect(jsonPath("$.requestType").value("TOPUP"))
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
    UUID requestId = UUID.fromString(body.get("requestId").asText());

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(requestId.toString()))
        .andExpect(jsonPath("$[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$[0].agentAuthored").value(true))
        .andExpect(jsonPath("$[0].type").value("TOPUP"));
  }

  @Test
  void aProactiveFeeWithoutATesterIdIsRejected() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"feeType":"TOPUP","amount":25.00}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void aProactiveProvisionSmartphoneFeeIsRefusedTheAgentLogsTheRequestInstead() throws Exception {
    // manager-approves-requests ticket AC: "A proactive Fee for one of the four types is refused
    // with a message telling the Agent to log the Request instead" — supersedes this test's former
    // "a proactive Provision Smartphone Fee also adds the unit to the Fleet" scenario, which the
    // spec's Fees-and-approval rule no longer allows.
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Bright Path Clinics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    createTesterAndLogin(managerToken, clientId, "marco.diaz@brightpath.example", "Passw0rd!23");

    String agentToken = agentToken();
    UUID testerId = findTesterId(agentToken, contractId, "marco.diaz@brightpath.example");

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"feeType":"PROVISION_SMARTPHONE","amount":150.00,"testerId":"%s",
                     "requestedModel":"iPhone 15"}
                    """
                        .formatted(testerId)))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/smartphones")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    // Logging the Request instead still works, once approved and completed.
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/requests")
                    .header("Authorization", "Bearer " + agentToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"type":"PROVISION_SMARTPHONE","testerId":"%s","requestedModel":"iPhone 15"}
                        """
                            .formatted(testerId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andReturn();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    approveAsManager(managerToken, requestId);
    mockMvc.perform(
        patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
            .header("Authorization", "Bearer " + agentToken)
            .contentType(APPLICATION_JSON)
            .content("""
                {"status":"IN_PROGRESS"}
                """));
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"COMPLETED"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/smartphones")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].model").value("iPhone 15"))
        .andExpect(jsonPath("$[0].serial").isEmpty())
        .andExpect(jsonPath("$[0].status").value("ACTIVE"));
  }

  // --- AC: completing a Provision Request adds/retires the correct Fleet items -----------------

  @Test
  void completingATesterRaisedProvisionSmartphoneRequestAddsACompanyOwnedUnitAndNoLongerReplacesOne()
      throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    UUID oldSmartphoneId =
        UUID.fromString(
            objectMapper
                .readTree(
                    mockMvc
                        .perform(
                            post("/api/contracts/" + contractId + "/smartphones")
                                .header("Authorization", "Bearer " + managerToken)
                                .contentType(APPLICATION_JSON)
                                .content("""
                                    {"model":"iPhone 13","serial":"SN-OLD-1"}
                                    """))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asText());

    String testerToken =
        createTesterAndLogin(managerToken, clientId, "charlotte.finch@harborfinch.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, "PROVISION_SMARTPHONE");
    approveAsManager(managerToken, requestId);

    String agentToken = agentToken();
    mockMvc.perform(
        patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
            .header("Authorization", "Bearer " + agentToken)
            .contentType(APPLICATION_JSON)
            .content("""
                {"status":"IN_PROGRESS"}
                """));

    // provision-request-details ticket AC: "A Provision Request no longer accepts a unit to
    // replace" — a stray replacesSmartphoneId in the completion body is simply not read for a
    // Request that already carries its own requestedModel from submission.
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"status":"COMPLETED","replacesSmartphoneId":"%s"}
                    """
                        .formatted(oldSmartphoneId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // Regression (fleet-management): the Fleet view shows the new unit added, and the older one
    // untouched — Provision no longer retires anything (spec.md: "that is what Replace is for").
    MvcResult fleetResult =
        mockMvc
            .perform(
                get("/api/contracts/" + contractId + "/smartphones")
                    .header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andReturn();

    JsonNode fleet = objectMapper.readTree(fleetResult.getResponse().getContentAsString());
    boolean oldStillActive = false;
    boolean newActive = false;
    for (JsonNode phone : fleet) {
      if (phone.get("id").asText().equals(oldSmartphoneId.toString())) {
        oldStillActive = "ACTIVE".equals(phone.get("status").asText());
      }
      if ("Fixture Model".equals(phone.get("model").asText())) {
        newActive = "ACTIVE".equals(phone.get("status").asText());
      }
    }
    assertThat(oldStillActive).as("old smartphone left untouched").isTrue();
    assertThat(newActive).as("new smartphone active").isTrue();
  }

  @Test
  void completingAProvisionSimRequestWithoutARetiredUnitOnlyAddsTheNewOne() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Solene Cosmetics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "elise.fabron@solene.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, "PROVISION_SIM");
    approveAsManager(managerToken, requestId);

    String agentToken = agentToken();
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"status":"IN_PROGRESS"}
                    """))
        .andExpect(status().isOk());

    // provision-request-details ticket AC: completing asks only for the SIM number — Carrier and
    // flavor already came from submitRequest's own requestedFlavor/requestedCarrierId.
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"COMPLETED","simCardNumber":"+1-555-0199"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/sim-cards")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].number").value("+1-555-0199"))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"));
  }

  @Test
  void completingATesterRaisedProvisionSmartphoneRequestNeedsNoNewSmartphoneDetails() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, "PROVISION_SMARTPHONE");
    approveAsManager(managerToken, requestId);

    String agentToken = agentToken();
    mockMvc.perform(
        patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
            .header("Authorization", "Bearer " + agentToken)
            .contentType(APPLICATION_JSON)
            .content("""
                {"status":"IN_PROGRESS"}
                """));

    // provision-request-details ticket AC: "Completing a Provision Smartphone needs no Agent
    // input" — the model already came from submitRequest's own requestedModel.
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"COMPLETED"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/smartphones")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].model").value("Fixture Model"))
        .andExpect(jsonPath("$[0].serial").isEmpty());
  }

  @Test
  void anAgentProactivelyLoggingAProvisionSmartphoneRequestAlwaysStartsPendingApproval() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    createTesterAndLogin(managerToken, clientId, "owen.reyes@meridian.example", "Passw0rd!23");

    String agentToken = agentToken();
    UUID testerId = findTesterId(agentToken, contractId, "owen.reyes@meridian.example");

    // manager-approves-requests ticket: an Agent-proactive Provision Request can no longer start
    // Completed (spec.md Lifecycle) — asking for it is refused outright.
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"PROVISION_SMARTPHONE","testerId":"%s","startingStatus":"COMPLETED",
                     "requestedModel":"Pixel 9"}
                    """
                        .formatted(testerId)))
        .andExpect(status().isBadRequest());

    // Once approved and progressed, completion still provisions the unit with no further input.
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/requests")
                    .header("Authorization", "Bearer " + agentToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"type":"PROVISION_SMARTPHONE","testerId":"%s","requestedModel":"Pixel 9"}
                        """
                            .formatted(testerId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andReturn();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    approveAsManager(managerToken, requestId);
    mockMvc.perform(
        patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
            .header("Authorization", "Bearer " + agentToken)
            .contentType(APPLICATION_JSON)
            .content("""
                {"status":"IN_PROGRESS"}
                """));
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"COMPLETED"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/smartphones")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].model").value("Pixel 9"))
        .andExpect(jsonPath("$[0].serial").isEmpty());
  }

  // --- Access control ----------------------------------------------------------------------------

  @Test
  void aTesterCannotLogAFee() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Kessler & Vance LLP");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "helena.voss@kessler.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, "TOPUP");

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/fees")
                .header("Authorization", "Bearer " + testerToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requestId":"%s","feeType":"TOPUP","amount":10.00}
                    """
                        .formatted(requestId)))
        .andExpect(status().isForbidden());
  }

  @Test
  void anAgentOnADifferentContractCannotLogAFeeOnIt() throws Exception {
    String managerToken = managerToken();
    UUID otherClient = createClient(managerToken, "Solene Cosmetics");
    UUID otherAgentId = createAgent(managerToken, "Priya Nair", com.remotesupport.backend.domain.Country.PHILIPPINES);
    UUID otherContract = createContract(managerToken, otherClient, otherAgentId);
    createTesterAndLogin(managerToken, otherClient, "elise.fabron@solene.example", "Passw0rd!23");

    mockMvc
        .perform(
            post("/api/contracts/" + otherContract + "/fees")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"feeType":"TOPUP","amount":10.00,"testerId":"%s"}
                    """
                        .formatted(UUID.randomUUID())))
        .andExpect(status().isForbidden());
  }

  // --- Observability -------------------------------------------------------------------------

  @Test
  void loggingAFeeLogsAnAuditEntryWithContractRequestAmountAndActor() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      String managerToken = managerToken();
      UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
      UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
      String testerToken =
          createTesterAndLogin(managerToken, clientId, "charlotte.finch@harborfinch.example", "Passw0rd!23");
      UUID requestId = submitRequest(testerToken, contractId, "TOPUP");

      mockMvc.perform(
          post("/api/contracts/" + contractId + "/fees")
              .header("Authorization", "Bearer " + agentToken())
              .contentType(APPLICATION_JSON)
              .content(
                  """
                  {"requestId":"%s","feeType":"TOPUP","amount":33.00}
                  """
                      .formatted(requestId)));

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=FEE_LOGGED");
      Assertions.assertThat(logged).contains("entity=Fee");
      Assertions.assertThat(logged).contains("contractId=" + contractId);
      Assertions.assertThat(logged).contains("requestId=" + requestId);
      Assertions.assertThat(logged).contains("amount=33.00");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  @Test
  void provisioningAFleetItemLogsAnAuditEntryWithContractAndRequest() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      String managerToken = managerToken();
      UUID clientId = createClient(managerToken, "Bright Path Clinics");
      UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
      String testerToken =
          createTesterAndLogin(managerToken, clientId, "marco.diaz@brightpath.example", "Passw0rd!23");
      UUID requestId = submitRequest(testerToken, contractId, "PROVISION_SMARTPHONE");
      approveAsManager(managerToken, requestId);

      mockMvc.perform(
          patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
              .header("Authorization", "Bearer " + agentToken())
              .contentType(APPLICATION_JSON)
              .content("""
                  {"status":"IN_PROGRESS"}
                  """));
      mockMvc.perform(
          patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
              .header("Authorization", "Bearer " + agentToken())
              .contentType(APPLICATION_JSON)
              .content(
                  """
                  {"status":"COMPLETED","newSmartphone":{"model":"iPhone 15","serial":"SN-AUDIT-1"}}
                  """));

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=FLEET_ITEM_PROVISIONED");
      Assertions.assertThat(logged).contains("entity=Smartphone");
      Assertions.assertThat(logged).contains("contractId=" + contractId);
      Assertions.assertThat(logged).contains("requestId=" + requestId);
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  // --- AC: every Fee is only ever reachable through its linking Request ------------------------

  /**
   * Bypasses FeeController entirely and writes straight to the repository, exactly the kind of
   * write path the ticket's traceability AC has to hold up against ("there is no way to create an
   * untraceable Fee"). {@code Fee#request} is {@code @ManyToOne(optional = false)} with a
   * database-level {@code NOT NULL} FK (V9 migration); Hibernate's own not-null check on the
   * association rejects this before a flush even reaches Postgres, and if that check were ever
   * relaxed the database constraint would still refuse the row.
   */
  @Test
  void constructingAFeeWithNoRequestThroughTheRepositoryDirectlyIsRejected() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Kessler & Vance LLP");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    Contract contract = contractRepository.findById(contractId).orElseThrow();

    Fee orphanFee = new Fee();
    orphanFee.setId(UUID.randomUUID());
    orphanFee.setTenant(contract.getTenant());
    orphanFee.setContract(contract);
    orphanFee.setRequest(null);
    orphanFee.setFeeType(FeeType.TOPUP);
    orphanFee.setAmount(new java.math.BigDecimal("10.00"));
    orphanFee.setCurrency(contract.getCurrency());
    orphanFee.setBillingMonth(LocalDate.now().withDayOfMonth(1));
    orphanFee.setCreatedAt(Instant.now());

    assertThatThrownBy(() -> feeRepository.saveAndFlush(orphanFee)).isInstanceOf(RuntimeException.class);
  }
}
