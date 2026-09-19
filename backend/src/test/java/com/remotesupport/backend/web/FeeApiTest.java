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

  private UUID submitRequest(String testerToken, UUID contractId, String type) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/requests")
                    .header("Authorization", "Bearer " + testerToken)
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"type":"%s"}
                        """.formatted(type)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

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

  // --- AC: Fee creation for each eligible Request type -----------------------------------------

  @ParameterizedTest
  @ValueSource(strings = {"TOPUP", "REPAIR"})
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
                    {"requestId":"%s","feeType":"REPAIR","amount":10.00}
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

    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/fees")
                    .header("Authorization", "Bearer " + agentToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"feeType":"TOPUP","amount":25.00,"testerId":"%s","description":"Top-up at kiosk"}
                        """
                            .formatted(testerId)))
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
  void aProactiveProvisionSmartphoneFeeAlsoAddsTheUnitToTheFleet() throws Exception {
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
                     "newSmartphone":{"model":"iPhone 15","serial":"SN-9001"}}
                    """
                        .formatted(testerId)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/smartphones")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].serial").value("SN-9001"))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"));
  }

  // --- AC: completing a Provision Request adds/retires the correct Fleet items -----------------

  @Test
  void completingATesterRaisedProvisionSmartphoneRequestAddsTheUnitAndRetiresTheReplacedOne()
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

    String agentToken = agentToken();
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
                .content(
                    """
                    {"status":"COMPLETED","newSmartphone":{"model":"iPhone 15","serial":"SN-NEW-1"},
                     "replacesSmartphoneId":"%s"}
                    """
                        .formatted(oldSmartphoneId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // Regression (fleet-management): the Fleet view reflects both the addition and the retirement.
    MvcResult fleetResult =
        mockMvc
            .perform(
                get("/api/contracts/" + contractId + "/smartphones")
                    .header("Authorization", "Bearer " + agentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andReturn();

    JsonNode fleet = objectMapper.readTree(fleetResult.getResponse().getContentAsString());
    boolean oldRetired = false;
    boolean newActive = false;
    for (JsonNode phone : fleet) {
      if (phone.get("serial").asText().equals("SN-OLD-1")) {
        oldRetired = "RETIRED".equals(phone.get("status").asText());
      }
      if (phone.get("serial").asText().equals("SN-NEW-1")) {
        newActive = "ACTIVE".equals(phone.get("status").asText());
      }
    }
    assertThat(oldRetired).as("old smartphone retired").isTrue();
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

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"status":"COMPLETED","newSimCard":{"number":"+1-555-0199","carrierId":"%s","flavor":"PREPAID"}}
                    """.formatted(SEEDED_US_CARRIER_ID)))
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
  void completingAProvisionSmartphoneRequestWithoutNewSmartphoneDetailsIsRejected() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");
    UUID requestId = submitRequest(testerToken, contractId, "PROVISION_SMARTPHONE");

    String agentToken = agentToken();
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
        .andExpect(status().isBadRequest());
  }

  @Test
  void anAgentProactivelyLoggingAProvisionSmartphoneRequestStartingCompletedProvisionsTheUnit()
      throws Exception {
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
                    {"type":"PROVISION_SMARTPHONE","testerId":"%s","startingStatus":"COMPLETED",
                     "newSmartphone":{"model":"Pixel 9","serial":"SN-PROACTIVE-1"}}
                    """
                        .formatted(testerId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/smartphones")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].serial").value("SN-PROACTIVE-1"));
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
