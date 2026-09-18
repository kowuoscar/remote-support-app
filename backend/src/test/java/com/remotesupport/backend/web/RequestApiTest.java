package com.remotesupport.backend.web;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Request submission and visibility (tester-request-submission ticket: spec.md Solution's
 * Request entity, and user stories 16, 30-31). Mirrors {@link SmartphoneApiTest}'s pattern:
 * Requests are nested under a Contract exactly like Fleet, so "a Tester sees every Request raised
 * by anyone at their Client across every Contract" is exercised as one GET per Contract (the same
 * shape the frontend uses), not a separate Client-wide endpoint.
 */
class RequestApiTest extends IntegrationTest {

  // Other now requires a description; every other type still submits with none, exactly as
  // before. reboot-and-topup-details ticket: Reboot/Topup now each require their own target unit
  // — a fresh fixture per call, since a Smartphone/SIM Card can't be shared across Requests here.
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

  /** Looks up a Tester's id by username via the Contract-scoped testers listing. */
  private UUID findTesterId(String callerToken, UUID contractId, String username) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/contracts/" + contractId + "/testers")
                    .header("Authorization", "Bearer " + callerToken))
            .andExpect(status().isOk())
            .andReturn();
    for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString())) {
      if (username.equals(node.get("username").asText())) {
        return UUID.fromString(node.get("id").asText());
      }
    }
    throw new IllegalStateException("No tester named " + username + " found on contract " + contractId);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"REBOOT", "TOPUP", "SIM_SWAP", "PROVISION_SMARTPHONE", "PROVISION_SIM", "OTHER"})
  void testerCanSubmitEachRequestTypeAndItStartsSubmitted(String type) throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");

    // A description is required only for Other, but harmless to give for every type — this covers
    // both "every type accepts an optional description" and Other's own requirement in one loop.
    // reboot-and-topup-details ticket: Reboot/Topup also need their own target unit.
    // provision-request-details ticket: Provision Smartphone/SIM also need their own details.
    Map<String, Object> body = new HashMap<>();
    body.put("type", type);
    body.put("description", "Details for the agent");
    if ("REBOOT".equals(type)) {
      body.put("targetSmartphoneId", createSmartphone(managerToken, contractId, "Fixture Phone"));
    } else if ("TOPUP".equals(type)) {
      body.put("targetSimCardId", createTopupTargetSimCard(managerToken, contractId));
    } else if ("PROVISION_SMARTPHONE".equals(type)) {
      body.put("requestedModel", "Fixture Model");
    } else if ("PROVISION_SIM".equals(type)) {
      body.put("requestedFlavor", "PREPAID");
      body.put("requestedCarrierId", SEEDED_US_CARRIER_ID);
    }

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + testerToken)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value(type))
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.contractId").value(contractId.toString()))
        .andExpect(jsonPath("$.raisedByUsername").value("priya.raman@aurora.example"))
        .andExpect(jsonPath("$.description").value("Details for the agent"));
  }

  @Test
  void anOtherRequestIsRefusedWithoutADescriptionOnBothTheTesterAndTheAgentPath() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + testerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"type":"OTHER"}
                    """))
        .andExpect(status().isBadRequest());

    // A blank description is refused exactly like a missing one.
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + testerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"type":"OTHER","description":"   "}
                    """))
        .andExpect(status().isBadRequest());

    String agentToken = agentToken();
    UUID testerId = findTesterId(agentToken, contractId, "priya.raman@aurora.example");
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"type":"OTHER","testerId":"%s"}
                    """.formatted(testerId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anyTesterAtTheSameClientSeesEveryRequestRaisedByAnyoneAtThatClient() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    String firstTesterToken =
        createTesterAndLogin(managerToken, clientId, "owen.reyes@meridian.example", "Passw0rd!23");
    submitRequest(firstTesterToken, contractId, "REBOOT");

    String secondTesterToken =
        createTesterAndLogin(managerToken, clientId, "lena.frost@meridian.example", "Passw0rd!23");
    submitRequest(secondTesterToken, contractId, "TOPUP");

    // The second Tester sees both Requests — their own and the first Tester's — not just their own.
    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + secondTesterToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].raisedByUsername").value("owen.reyes@meridian.example"))
        .andExpect(jsonPath("$[1].raisedByUsername").value("lena.frost@meridian.example"));
  }

  @Test
  void anyTesterAtTheSameClientSeesRequestsAcrossAllOfThatClientsContracts() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Kessler & Vance LLP");
    UUID firstContract = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID otherAgentId = createAgent(managerToken, "Priya Nair", Country.PHILIPPINES);
    UUID secondContract = createContract(managerToken, clientId, otherAgentId);

    String testerToken =
        createTesterAndLogin(managerToken, clientId, "helena.voss@kessler.example", "Passw0rd!23");
    submitRequest(testerToken, firstContract, "REBOOT");
    submitRequest(testerToken, secondContract, "OTHER");

    mockMvc
        .perform(
            get("/api/contracts/" + firstContract + "/requests")
                .header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].type").value("REBOOT"));

    mockMvc
        .perform(
            get("/api/contracts/" + secondContract + "/requests")
                .header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].type").value("OTHER"));
  }

  @Test
  void agentSeesIncomingRequestsForTheirOwnContractsFilteredByContract() throws Exception {
    String managerToken = managerToken();
    UUID ownClient = createClient(managerToken, "Bright Path Clinics");
    UUID ownContract = createContract(managerToken, ownClient, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, ownClient, "marco.diaz@brightpath.example", "Passw0rd!23");
    submitRequest(testerToken, ownContract, "TOPUP");

    UUID otherClient = createClient(managerToken, "Solene Cosmetics");
    UUID otherAgentId = createAgent(managerToken, "Priya Nair", Country.PHILIPPINES);
    UUID otherContract = createContract(managerToken, otherClient, otherAgentId);
    String otherTesterToken =
        createTesterAndLogin(managerToken, otherClient, "elise.fabron@solene.example", "Passw0rd!23");
    submitRequest(otherTesterToken, otherContract, "REBOOT");

    String agentToken = agentToken();

    mockMvc
        .perform(
            get("/api/contracts/" + ownContract + "/requests")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].type").value("TOPUP"));

    // The Agent cannot see Requests on a Contract that isn't theirs.
    mockMvc
        .perform(
            get("/api/contracts/" + otherContract + "/requests")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerSeesRequestsOnAnyContractInTheTenant() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "charlotte.finch@harborfinch.example", "Passw0rd!23");
    submitRequest(testerToken, contractId, "SIM_SWAP");

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].type").value("SIM_SWAP"));
  }

  @Test
  void aTesterCannotSubmitARequestAgainstAnotherClientsContract() throws Exception {
    String managerToken = managerToken();
    UUID ownClient = createClient(managerToken, "Solene Cosmetics");
    createTesterAndLogin(managerToken, ownClient, "irrelevant@solene.example", "Passw0rd!23");

    UUID otherClient = createClient(managerToken, "Meridian Logistics");
    UUID otherContract = createContract(managerToken, otherClient, SEEDED_AGENT_ID);

    String testerToken =
        createTesterAndLogin(managerToken, ownClient, "elise.fabron@solene.example", "Passw0rd!23");

    mockMvc
        .perform(
            post("/api/contracts/" + otherContract + "/requests")
                .header("Authorization", "Bearer " + testerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"type":"REBOOT"}
                    """))
        .andExpect(status().isForbidden());
  }

  // Superseded by agent-request-fulfillment: an Agent can now create a Request too (Agent-authored,
  // proactive), so a bare POST from an Agent no longer 403s — see
  // agentLoggingARequestWithoutATesterIdIsRejected (400, missing testerId) and
  // anAgentOnADifferentContractCannotLogARequestOnIt (403, wrong Contract) below for the Agent
  // creation boundaries that replace this test.

  @Test
  void submittingARequestAgainstAnUnknownContractReturnsNotFound() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "owen.reyes@meridian.example", "Passw0rd!23");

    mockMvc
        .perform(
            post("/api/contracts/" + UUID.randomUUID() + "/requests")
                .header("Authorization", "Bearer " + testerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"type":"REBOOT"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void submittingARequestLogsAnAuditEntryWithContractTypeAndActor() throws Exception {
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

      submitRequest(testerToken, contractId, "PROVISION_SIM");

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=REQUEST_SUBMITTED");
      Assertions.assertThat(logged).contains("entity=Request");
      Assertions.assertThat(logged).contains("contractId=" + contractId);
      Assertions.assertThat(logged).contains("requestType=PROVISION_SIM");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  // --- agent-request-fulfillment: status transitions ------------------------------------------

  @Test
  void agentProgressesARequestFromSubmittedThroughInProgressToCompleted() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, "TOPUP");

    String agentToken = agentToken();

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"IN_PROGRESS"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"COMPLETED"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // COMPLETED is terminal — reject moving backwards.
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"IN_PROGRESS"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void agentCancelsARequestWithAReasonButNotWithoutOne() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "owen.reyes@meridian.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, "REBOOT");

    String agentToken = agentToken();

    // Missing reason is rejected.
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"CANCELLED"}
                    """))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"CANCELLED","cancellationReason":"Tester no longer needs this"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.cancellationReason").value("Tester no longer needs this"));

    // CANCELLED is terminal.
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"IN_PROGRESS"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void aTesterCannotChangeARequestsStatus() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Bright Path Clinics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "marco.diaz@brightpath.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, "TOPUP");

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + testerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"IN_PROGRESS"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void anAgentOnADifferentContractCannotChangeStatus() throws Exception {
    String managerToken = managerToken();
    UUID ownClient = createClient(managerToken, "Kessler & Vance LLP");
    UUID ownContract = createContract(managerToken, ownClient, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, ownClient, "helena.voss@kessler.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, ownContract, "REBOOT");

    UUID otherClient = createClient(managerToken, "Solene Cosmetics");
    UUID otherAgentId = createAgent(managerToken, "Priya Nair", Country.PHILIPPINES);
    UUID otherContract = createContract(managerToken, otherClient, otherAgentId);
    String otherTesterToken =
        createTesterAndLogin(managerToken, otherClient, "elise.fabron@solene.example", "Passw0rd!23");
    UUID otherRequestId = submitRequest(otherTesterToken, otherContract, "REBOOT");

    String agentToken = agentToken();

    // The seeded Agent's own Contract works...
    mockMvc
        .perform(
            patch("/api/contracts/" + ownContract + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"IN_PROGRESS"}
                    """))
        .andExpect(status().isOk());

    // ...but a Request on a Contract that isn't theirs is rejected.
    mockMvc
        .perform(
            patch("/api/contracts/" + otherContract + "/requests/" + otherRequestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"IN_PROGRESS"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void changingStatusLogsAnAuditEntryWithOldAndNewStatusAndActor() throws Exception {
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
          patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
              .header("Authorization", "Bearer " + agentToken())
              .contentType(APPLICATION_JSON)
              .content("""
                  {"status":"IN_PROGRESS"}
                  """));

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=STATUS_CHANGE");
      Assertions.assertThat(logged).contains("entity=Request");
      Assertions.assertThat(logged).contains("entityId=" + requestId);
      Assertions.assertThat(logged).contains("oldStatus=SUBMITTED");
      Assertions.assertThat(logged).contains("newStatus=IN_PROGRESS");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  // --- agent-request-fulfillment: Agent-authored / proactive creation -------------------------

  @Test
  void agentLogsARequestOnATestersBehalfStartingSubmitted() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");

    String agentToken = agentToken();
    UUID testerId = findTesterId(agentToken, contractId, "priya.raman@aurora.example");
    UUID targetSimCardId = createTopupTargetSimCard(managerToken, contractId);

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"TOPUP","testerId":"%s","targetSimCardId":"%s","description":"Top-up needed"}
                    """
                        .formatted(testerId, targetSimCardId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.agentAuthored").value(true))
        .andExpect(jsonPath("$.raisedByUsername").value("priya.raman@aurora.example"))
        .andExpect(jsonPath("$.loggedByUsername").value(AGENT_USERNAME));
  }

  @Test
  void agentLogsARequestOnATestersBehalfStartingImmediatelyCompleted() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    createTesterAndLogin(managerToken, clientId, "owen.reyes@meridian.example", "Passw0rd!23");

    String agentToken = agentToken();
    UUID testerId = findTesterId(agentToken, contractId, "owen.reyes@meridian.example");

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"OTHER","testerId":"%s","startingStatus":"COMPLETED","description":"Screen replacement"}
                    """
                        .formatted(testerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.agentAuthored").value(true));
  }

  @Test
  void agentLoggingARequestWithoutATesterIdIsRejected() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Bright Path Clinics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"type":"TOPUP"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void agentLoggingARequestWithAnInvalidStartingStatusIsRejected() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Kessler & Vance LLP");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    createTesterAndLogin(managerToken, clientId, "helena.voss@kessler.example", "Passw0rd!23");

    String agentToken = agentToken();
    UUID testerId = findTesterId(agentToken, contractId, "helena.voss@kessler.example");

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"TOPUP","testerId":"%s","startingStatus":"IN_PROGRESS"}
                    """
                        .formatted(testerId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anAgentOnADifferentContractCannotLogARequestOnIt() throws Exception {
    String managerToken = managerToken();
    UUID otherClient = createClient(managerToken, "Solene Cosmetics");
    UUID otherAgentId = createAgent(managerToken, "Priya Nair", Country.PHILIPPINES);
    UUID otherContract = createContract(managerToken, otherClient, otherAgentId);
    createTesterAndLogin(managerToken, otherClient, "elise.fabron@solene.example", "Passw0rd!23");

    String agentToken = agentToken();

    mockMvc
        .perform(
            post("/api/contracts/" + otherContract + "/requests")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"type":"TOPUP","testerId":"%s"}
                    """.formatted(UUID.randomUUID())))
        .andExpect(status().isForbidden());
  }

  @Test
  void loggingARequestByAgentLogsAnAuditEntryWithStartingStatus() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      String managerToken = managerToken();
      UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
      UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
      createTesterAndLogin(managerToken, clientId, "charlotte.finch@harborfinch.example", "Passw0rd!23");

      String agentToken = agentToken();
      UUID testerId = findTesterId(agentToken, contractId, "charlotte.finch@harborfinch.example");

      // fee-logging-and-provisioning ticket: completing a Provision SIM Request — even
      // immediately, via an Agent-proactive creation — requires the new unit's details.
      // provision-request-details ticket: submission-time details (flavor/Carrier) plus, since
      // this one starts immediately Completed, the SIM number too.
      mockMvc.perform(
          post("/api/contracts/" + contractId + "/requests")
              .header("Authorization", "Bearer " + agentToken)
              .contentType(APPLICATION_JSON)
              .content(
                  """
                  {"type":"PROVISION_SIM","testerId":"%s","startingStatus":"COMPLETED",
                   "requestedFlavor":"PREPAID","requestedCarrierId":"%s","simCardNumber":"+1-555-0177"}
                  """
                      .formatted(testerId, SEEDED_US_CARRIER_ID)));

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=REQUEST_LOGGED_BY_AGENT");
      Assertions.assertThat(logged).contains("entity=Request");
      Assertions.assertThat(logged).contains("contractId=" + contractId);
      Assertions.assertThat(logged).contains("requestType=PROVISION_SIM");
      Assertions.assertThat(logged).contains("startingStatus=COMPLETED");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  // --- Regression: the Tester-visible list still reflects Agent-authored Requests/status -----

  @Test
  void testersStillSeeAgentAuthoredRequestsAndTheirStatusChanges() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");

    String agentToken = agentToken();
    UUID testerId = findTesterId(agentToken, contractId, "priya.raman@aurora.example");

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/requests")
                    .header("Authorization", "Bearer " + agentToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"type":"OTHER","testerId":"%s","description":"Screen replacement"}
                        """
                            .formatted(testerId)))
            .andExpect(status().isCreated())
            .andReturn();
    UUID requestId =
        UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText());

    // The Tester sees the Agent-authored Request in their own list.
    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].type").value("OTHER"))
        .andExpect(jsonPath("$[0].agentAuthored").value(true))
        .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
        .andExpect(jsonPath("$[0].description").value("Screen replacement"));

    // Once the Agent progresses it, the Tester's list reflects the new status.
    mockMvc.perform(
        patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
            .header("Authorization", "Bearer " + agentToken)
            .contentType(APPLICATION_JSON)
            .content("""
                {"status":"IN_PROGRESS"}
                """));

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));
  }
}
