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
import com.remotesupport.backend.support.OtherTenantFixture;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Every new SIM Card names a Carrier from the catalog (carrier-catalog spec, SIM Card changes;
 * sim-card-carrier ticket). The same rule holds on every SIM-creation path, so each case runs once
 * per {@link Path}: the Carrier is required, must belong to the Contract's Agent's Country, and
 * must not be archived.
 */
@Import(OtherTenantFixture.class)
class SimCardCarrierApiTest extends IntegrationTest {

  /** The four ways a SIM Card comes into a Fleet. */
  enum Path {
    MANAGER_ADDS_TO_FLEET,
    AGENT_COMPLETES_PROVISION_SIM_REQUEST,
    AGENT_LOGS_PROVISION_SIM_REQUEST_COMPLETED,
    AGENT_LOGS_PROVISION_SIM_FEE
  }

  @Autowired private OtherTenantFixture otherTenantFixture;

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

  @ParameterizedTest
  @EnumSource(Path.class)
  void aSimCardNamingACarrierOfTheContractsCountryIsCreatedAndShowsTheCarriersName(Path path)
      throws Exception {
    create(path, SEEDED_US_CARRIER_ID).andExpect(status().is2xxSuccessful());

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].carrierId").value(SEEDED_US_CARRIER_ID.toString()))
        .andExpect(jsonPath("$[0].carrierName").value("Verizon"))
        .andExpect(jsonPath("$[0].carrierArchived").value(false));
  }

  @ParameterizedTest
  @EnumSource(Path.class)
  void aSimCardWithoutACarrierIsRefused(Path path) throws Exception {
    create(path, null).andExpect(status().isBadRequest());
    assertFleetHasNoSimCard();
  }

  @ParameterizedTest
  @EnumSource(Path.class)
  void aCarrierOfAnotherCountryIsRefused(Path path) throws Exception {
    UUID britishCarrier = createCarrier(managerToken, Country.UNITED_KINGDOM, "Vodafone UK");

    create(path, britishCarrier).andExpect(status().isBadRequest());
    assertFleetHasNoSimCard();
  }

  @ParameterizedTest
  @EnumSource(Path.class)
  void anArchivedCarrierIsRefused(Path path) throws Exception {
    UUID archived = createCarrier(managerToken, Country.UNITED_STATES, "Cricket Wireless");
    archiveCarrier(managerToken, archived);

    create(path, archived).andExpect(status().isBadRequest());
    assertFleetHasNoSimCard();
  }

  @ParameterizedTest
  @EnumSource(Path.class)
  void anotherTenantsCarrierIsRefused(Path path) throws Exception {
    UUID foreign = otherTenantFixture.carrierInAnotherTenant();

    create(path, foreign).andExpect(status().isBadRequest());
    assertFleetHasNoSimCard();
  }

  @Test
  void aSimCardOnACarrierArchivedLaterStillShowsItsNameMarkedArchived() throws Exception {
    UUID carrier = createCarrier(managerToken, Country.UNITED_STATES, "Mint Mobile");
    create(Path.MANAGER_ADDS_TO_FLEET, carrier).andExpect(status().isCreated());
    archiveCarrier(managerToken, carrier);

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].carrierName").value("Mint Mobile"))
        .andExpect(jsonPath("$[0].carrierArchived").value(true));
  }

  @Test
  void provisioningASimCardLogsItsCarrier() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      create(Path.AGENT_LOGS_PROVISION_SIM_FEE, SEEDED_US_CARRIER_ID).andExpect(status().isCreated());

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=FLEET_ITEM_PROVISIONED");
      Assertions.assertThat(logged).contains("entity=SimCard");
      Assertions.assertThat(logged).contains("carrierId=" + SEEDED_US_CARRIER_ID);
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  /**
   * Creates a Prepaid SIM Card through {@code path}; {@code carrierId} is {@code null} for "no
   * Carrier at all". provision-request-details ticket: for the three Request-based paths, the
   * Carrier is now a submission-time detail (Manager's path is untouched — it still names a
   * Carrier directly on {@code SimCardCreateRequest}), so this asserts the exact same rule
   * ({@link com.remotesupport.backend.web.SimCardFactory#requireUsableCarrier}, shared by both
   * {@code SimCardController} and {@code ProvisionSimRequestDetailsHandler}) at whichever point it
   * now runs.
   */
  private ResultActions create(Path path, UUID carrierId) throws Exception {
    String carrierField = carrierId == null ? "" : ",\"carrierId\":\"" + carrierId + "\"";
    String requestedFields =
        ",\"requestedFlavor\":\"PREPAID\"" + (carrierId == null ? "" : ",\"requestedCarrierId\":\"" + carrierId + "\"");
    return switch (path) {
      case MANAGER_ADDS_TO_FLEET -> {
        String simCard = "{\"number\":\"+1-555-0142\",\"flavor\":\"PREPAID\"" + carrierField + "}";
        yield postJsonText("/api/contracts/" + contractId + "/sim-cards", managerToken, simCard);
      }
      case AGENT_COMPLETES_PROVISION_SIM_REQUEST -> {
        // The Tester's submission itself may be the outcome under test (e.g. no Carrier at all),
        // so its own result — not the later completion PATCH — is what a failed one surfaces.
        ResultActions submitResult =
            postJsonText(
                "/api/contracts/" + contractId + "/requests",
                testerToken,
                "{\"type\":\"PROVISION_SIM\"" + requestedFields + "}");
        MvcResult submitMvc = submitResult.andReturn();
        if (submitMvc.getResponse().getStatus() != 201) {
          yield submitResult;
        }
        UUID requestId =
            UUID.fromString(objectMapper.readTree(submitMvc.getResponse().getContentAsString()).get("id").asText());
        patchStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
        yield patchStatus(requestId, "{\"status\":\"COMPLETED\",\"simCardNumber\":\"+1-555-0142\"}");
      }
      case AGENT_LOGS_PROVISION_SIM_REQUEST_COMPLETED ->
          postJsonText(
              "/api/contracts/" + contractId + "/requests",
              agentToken,
              ("{\"type\":\"PROVISION_SIM\",\"testerId\":\"%s\",\"startingStatus\":\"COMPLETED\","
                      + "\"simCardNumber\":\"+1-555-0142\"%s}")
                  .formatted(testerId, requestedFields));
      case AGENT_LOGS_PROVISION_SIM_FEE ->
          postJsonText(
              "/api/contracts/" + contractId + "/fees",
              agentToken,
              ("{\"feeType\":\"PROVISION_SIM\",\"amount\":15.00,\"testerId\":\"%s\","
                      + "\"simCardNumber\":\"+1-555-0142\"%s}")
                  .formatted(testerId, requestedFields));
    };
  }

  private void assertFleetHasNoSimCard() throws Exception {
    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  private ResultActions postJsonText(String url, String token, String json) throws Exception {
    return mockMvc.perform(
        post(url).header("Authorization", "Bearer " + token).contentType(APPLICATION_JSON).content(json));
  }

  private ResultActions patchStatus(UUID requestId, String json) throws Exception {
    return mockMvc.perform(
        patch("/api/contracts/" + contractId + "/requests/" + requestId + "/status")
            .header("Authorization", "Bearer " + agentToken)
            .contentType(APPLICATION_JSON)
            .content(json));
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
