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
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Correcting a SIM Card's number (correct-a-fleet-unit spec, ticket correct-a-sim-card-number):
 * who may, the trim-then-length rule, the same-value no-op, and where the corrected number shows.
 */
@Import(OtherTenantFixture.class)
class SimCardCorrectionApiTest extends IntegrationTest {

  private static final String ORIGINAL = "+44 7700 900100";

  @Autowired private OtherTenantFixture otherTenantFixture;

  private String managerToken;
  private UUID clientId;
  private UUID contractId;
  private UUID simCardId;

  @BeforeEach
  void setUp() throws Exception {
    managerToken = managerToken();
    clientId = createClient(managerToken, "Sim Corrections Client " + UUID.randomUUID());
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    simCardId = addPrepaidSim(ORIGINAL);
  }

  private UUID addPrepaidSim(String number) throws Exception {
    UUID carrierId = createCarrierWithNoOptions(managerToken, contractId);
    String json =
        "{\"number\":\"%s\",\"carrierId\":\"%s\",\"flavor\":\"PREPAID\"}".formatted(number, carrierId);
    return idOf(
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/sim-cards")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(json))
            .andExpect(status().isCreated()));
  }

  private ResultActions correct(UUID contract, UUID sim, String token, String number) throws Exception {
    MockHttpServletRequestBuilder request =
        patch("/api/contracts/" + contract + "/sim-cards/" + sim + "/number")
            .contentType(APPLICATION_JSON)
            .content("{\"number\":\"%s\"}".formatted(number));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return mockMvc.perform(request);
  }

  private void fleetShowsNumber(String token, String number) throws Exception {
    mockMvc
        .perform(get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='%s')].number".formatted(simCardId)).value(number));
  }

  private String primaryContactToken() throws Exception {
    String username = "primary." + UUID.randomUUID() + "@simcorr.example";
    String created =
        mockMvc
            .perform(
                post("/api/clients/" + clientId + "/testers")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content("{\"username\":\"%s\",\"isPrimaryContact\":true}".formatted(username)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return loginAs(username, objectMapper.readTree(created).get("password").asText());
  }

  @Test
  void aManagerCorrectsTheNumberTrimmedAndTheFleetShowsIt() throws Exception {
    correct(contractId, simCardId, managerToken, "  +44 7700 900123  ")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value("+44 7700 900123"));

    fleetShowsNumber(managerToken, "+44 7700 900123");
  }

  @Test
  void anyTesterOfTheContractsClientCorrectsTheNumberPrimaryContactOrNot() throws Exception {
    String plain = createTesterAndLogin(managerToken, clientId, "plain." + UUID.randomUUID() + "@simcorr.example");
    correct(contractId, simCardId, plain, "+1 111")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value("+1 111"));
    fleetShowsNumber(plain, "+1 111");

    String primary = primaryContactToken();
    correct(contractId, simCardId, primary, "+1 222").andExpect(status().isOk());
    fleetShowsNumber(primary, "+1 222");
  }

  @Test
  void aTesterOfAnotherClientAndAnyAgentAreRefused() throws Exception {
    UUID otherClient = createClient(managerToken, "Other Sim Corrections Client " + UUID.randomUUID());
    String stranger =
        createTesterAndLogin(managerToken, otherClient, "stranger." + UUID.randomUUID() + "@simcorr.example");

    correct(contractId, simCardId, stranger, "Hacked").andExpect(status().isForbidden());
    correct(contractId, simCardId, agentToken(), "Hacked").andExpect(status().isForbidden());
    fleetShowsNumber(managerToken, ORIGINAL);
  }

  @Test
  void noTokenIs401AndAnotherTenantsContractIs404() throws Exception {
    correct(contractId, simCardId, null, "X").andExpect(status().isUnauthorized());

    OtherTenantFixture.ContractWithSmartphone foreign = otherTenantFixture.smartphoneOnContractInAnotherTenant();
    correct(foreign.contractId(), UUID.randomUUID(), managerToken, "X").andExpect(status().isNotFound());
  }

  @Test
  void aTesterStillGets403OnStatusAndInstalledIn() throws Exception {
    String tester = createTesterAndLogin(managerToken, clientId, "t." + UUID.randomUUID() + "@simcorr.example");

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simCardId + "/status")
                .header("Authorization", "Bearer " + tester)
                .contentType(APPLICATION_JSON)
                .content("{\"status\":\"RETIRED\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simCardId + "/installed-in")
                .header("Authorization", "Bearer " + tester)
                .contentType(APPLICATION_JSON)
                .content("{\"smartphoneId\":null}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void aBlankOrMissingNumberIsRefusedAndLeavesTheNumberUnchanged() throws Exception {
    correct(contractId, simCardId, managerToken, "   ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NUMBER_BLANK"));
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simCardId + "/number")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NUMBER_BLANK"));
    fleetShowsNumber(managerToken, ORIGINAL);
  }

  @Test
  void aNumberOf64CharactersAfterTrimmingSucceedsAnd65IsRefused() throws Exception {
    correct(contractId, simCardId, managerToken, " " + "1".repeat(64) + " ").andExpect(status().isOk());
    fleetShowsNumber(managerToken, "1".repeat(64));

    correct(contractId, simCardId, managerToken, "2".repeat(65))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NUMBER_TOO_LONG"));
    fleetShowsNumber(managerToken, "1".repeat(64));
  }

  @Test
  void savingTheNumberAlreadyHeldLogsNothingWhileAChangeLogsOneEvent() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger audit = (Logger) LoggerFactory.getLogger("AUDIT");
    audit.addAppender(appender);
    try {
      correct(contractId, simCardId, managerToken, " " + ORIGINAL + " ").andExpect(status().isOk());
      assertThat(corrections(appender)).isEmpty();

      correct(contractId, simCardId, managerToken, "+44 7700 900123").andExpect(status().isOk());
      assertThat(corrections(appender))
          .singleElement()
          .satisfies(
              line ->
                  assertThat(line)
                      .contains("entityId=" + simCardId)
                      .contains("contractId=" + contractId)
                      .contains("oldNumber=" + ORIGINAL)
                      .contains("newNumber=+44 7700 900123")
                      .contains("role=MANAGER")
                      .contains("actorUserId=")
                      .contains("tenantId=" + SEEDED_TENANT_ID));
    } finally {
      audit.detachAppender(appender);
    }
  }

  private static List<String> corrections(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .filter(m -> m.contains("action=simCardNumberCorrected"))
        .toList();
  }

  @Test
  void aCorrectedNumberShowsOnARequestAReturnedUnitAndAgentStock() throws Exception {
    String tester = createTesterAndLogin(managerToken, clientId, "req." + UUID.randomUUID() + "@simcorr.example");
    UUID phoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID swapId =
        idOf(
            postRequest(
                    contractId,
                    tester,
                    "{\"type\":\"SIM_SWAP\",\"targetSimCardId\":\"%s\",\"targetSmartphoneId\":\"%s\"}"
                        .formatted(simCardId, phoneId))
                .andExpect(status().isCreated()));
    ResultActions returned =
        postRequest(contractId, tester, "{\"type\":\"RETURN\",\"returnedSimCardIds\":[\"%s\"]}".formatted(simCardId))
            .andExpect(status().isCreated());
    JsonNode returnBody = objectMapper.readTree(returned.andReturn().getResponse().getContentAsString());
    UUID returnId = UUID.fromString(returnBody.get("id").asText());
    UUID unitId = UUID.fromString(returnBody.get("returnedUnits").get(0).get("id").asText());

    correct(contractId, simCardId, managerToken, "+44 7700 900123").andExpect(status().isOk());

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/requests").header("Authorization", "Bearer " + tester))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='%s')].targetSimCardNumber".formatted(swapId)).value("+44 7700 900123"))
        .andExpect(
            jsonPath("$[?(@.id=='%s')].returnedUnits[0].simCardNumber".formatted(returnId))
                .value("+44 7700 900123"));

    mockMvc
        .perform(
            post("/api/requests/" + returnId + "/approve")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"dispositions\":[{\"returnedUnitId\":\"%s\",\"disposition\":\"KEPT_IN_STOCK\"}]}"
                        .formatted(unitId)))
        .andExpect(status().isOk());
    patchStatus(contractId, returnId, agentToken(), "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
    patchStatus(contractId, returnId, agentToken(), "{\"status\":\"COMPLETED\"}").andExpect(status().isOk());

    mockMvc
        .perform(get("/api/stock").header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='%s')].number".formatted(simCardId)).value("+44 7700 900123"));
  }

  @Test
  void aCorrectedNumberShowsOnASentInvoiceLineWithTheAmountUnchanged() throws Exception {
    UUID postpaidId =
        idOf(
            mockMvc
                .perform(
                    post("/api/contracts/" + contractId + "/sim-cards")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(APPLICATION_JSON)
                        .content(postpaidSimCardJson(managerToken, contractId, "+1-555-0700", "18.00")))
                .andExpect(status().isCreated()));
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/client-invoice/send")
                .header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isOk());

    correct(contractId, postpaidId, managerToken, "+1-555-0799").andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/client-invoice")
                .header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.basePostpaidSims[0].number").value("+1-555-0799"))
        .andExpect(jsonPath("$.basePostpaidSims[0].amount").value(18.00))
        .andExpect(jsonPath("$.baseAmount").value(18.00));
  }

  private UUID idOf(ResultActions result) throws Exception {
    return UUID.fromString(
        objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText());
  }
}
