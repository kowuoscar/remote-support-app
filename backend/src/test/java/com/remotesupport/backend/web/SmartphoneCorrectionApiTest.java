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
 * Correcting a Smartphone's model (correct-a-fleet-unit spec, ticket correct-a-smartphone-model):
 * who may, the trim-then-length rule, the same-value no-op, and where the corrected model shows.
 */
@Import(OtherTenantFixture.class)
class SmartphoneCorrectionApiTest extends IntegrationTest {

  @Autowired private OtherTenantFixture otherTenantFixture;

  private String managerToken;
  private UUID clientId;
  private UUID contractId;
  private UUID smartphoneId;

  @BeforeEach
  void setUp() throws Exception {
    managerToken = managerToken();
    clientId = createClient(managerToken, "Corrections Client " + UUID.randomUUID());
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    smartphoneId = createSmartphone(managerToken, contractId, "Pixle 8");
  }

  private ResultActions correct(UUID contract, UUID phone, String token, String model) throws Exception {
    MockHttpServletRequestBuilder request =
        patch("/api/contracts/" + contract + "/smartphones/" + phone + "/model")
            .contentType(APPLICATION_JSON)
            .content("{\"model\":\"%s\"}".formatted(model));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return mockMvc.perform(request);
  }

  private void fleetShowsModel(String token, String model) throws Exception {
    mockMvc
        .perform(get("/api/contracts/" + contractId + "/smartphones").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].model").value(model));
  }

  private String primaryContactToken() throws Exception {
    String username = "primary." + UUID.randomUUID() + "@corr.example";
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
  void aManagerCorrectsTheModelTrimmedAndTheFleetShowsIt() throws Exception {
    correct(contractId, smartphoneId, managerToken, "  Pixel 8 Pro  ")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model").value("Pixel 8 Pro"));

    fleetShowsModel(managerToken, "Pixel 8 Pro");
  }

  @Test
  void anyTesterOfTheContractsClientCorrectsTheModelPrimaryContactOrNot() throws Exception {
    String plain = createTesterAndLogin(managerToken, clientId, "plain." + UUID.randomUUID() + "@corr.example");
    correct(contractId, smartphoneId, plain, "Pixel 8")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model").value("Pixel 8"));
    fleetShowsModel(plain, "Pixel 8");

    String primary = primaryContactToken();
    correct(contractId, smartphoneId, primary, "Pixel 9").andExpect(status().isOk());
    fleetShowsModel(primary, "Pixel 9");
  }

  @Test
  void aTesterOfAnotherClientAndAnyAgentAreRefused() throws Exception {
    UUID otherClient = createClient(managerToken, "Other Corrections Client " + UUID.randomUUID());
    String stranger =
        createTesterAndLogin(managerToken, otherClient, "stranger." + UUID.randomUUID() + "@corr.example");

    correct(contractId, smartphoneId, stranger, "Hacked").andExpect(status().isForbidden());
    correct(contractId, smartphoneId, agentToken(), "Hacked").andExpect(status().isForbidden());
    fleetShowsModel(managerToken, "Pixle 8");
  }

  @Test
  void noTokenIs401AndAnotherTenantsContractIs404() throws Exception {
    correct(contractId, smartphoneId, null, "X").andExpect(status().isUnauthorized());

    OtherTenantFixture.ContractWithSmartphone foreign = otherTenantFixture.smartphoneOnContractInAnotherTenant();
    correct(foreign.contractId(), foreign.smartphoneId(), managerToken, "X").andExpect(status().isNotFound());
  }

  @Test
  void aTesterStillGets403OnStatusAndSerial() throws Exception {
    String tester = createTesterAndLogin(managerToken, clientId, "t." + UUID.randomUUID() + "@corr.example");

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/smartphones/" + smartphoneId + "/status")
                .header("Authorization", "Bearer " + tester)
                .contentType(APPLICATION_JSON)
                .content("{\"status\":\"IN_REPAIR\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/smartphones/" + smartphoneId + "/serial")
                .header("Authorization", "Bearer " + tester)
                .contentType(APPLICATION_JSON)
                .content("{\"serial\":\"SN-1\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void aBlankOrMissingModelIsRefusedAndLeavesTheModelUnchanged() throws Exception {
    correct(contractId, smartphoneId, managerToken, "   ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MODEL_BLANK"));
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/smartphones/" + smartphoneId + "/model")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MODEL_BLANK"));
    fleetShowsModel(managerToken, "Pixle 8");
  }

  @Test
  void aModelOf255CharactersAfterTrimmingSucceedsAnd256IsRefused() throws Exception {
    correct(contractId, smartphoneId, managerToken, " " + "a".repeat(255) + " ").andExpect(status().isOk());
    fleetShowsModel(managerToken, "a".repeat(255));

    correct(contractId, smartphoneId, managerToken, "b".repeat(256))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MODEL_TOO_LONG"));
    fleetShowsModel(managerToken, "a".repeat(255));
  }

  @Test
  void savingTheModelAlreadyHeldLogsNothingWhileAChangeLogsOneEvent() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger audit = (Logger) LoggerFactory.getLogger("AUDIT");
    audit.addAppender(appender);
    try {
      correct(contractId, smartphoneId, managerToken, " Pixle 8 ").andExpect(status().isOk());
      assertThat(corrections(appender)).isEmpty();

      correct(contractId, smartphoneId, managerToken, "Pixel 8").andExpect(status().isOk());
      assertThat(corrections(appender))
          .singleElement()
          .satisfies(
              line ->
                  assertThat(line)
                      .contains("entityId=" + smartphoneId)
                      .contains("contractId=" + contractId)
                      .contains("oldModel=Pixle 8")
                      .contains("newModel=Pixel 8")
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
        .filter(m -> m.contains("action=smartphoneModelCorrected"))
        .toList();
  }

  @Test
  void aCorrectedModelShowsOnARequestAndAReturnedUnitWhileTheRequestedModelStays() throws Exception {
    String tester = createTesterAndLogin(managerToken, clientId, "req." + UUID.randomUUID() + "@corr.example");
    UUID replaceId =
        idOf(
            postRequest(
                    contractId,
                    tester,
                    "{\"type\":\"REPLACE_SMARTPHONE\",\"targetSmartphoneId\":\"%s\",\"requestedModel\":\"Pixel 9\"}"
                        .formatted(smartphoneId))
                .andExpect(status().isCreated()));
    UUID otherPhone = createSmartphone(managerToken, contractId, "Galaxy S2");
    UUID returnId =
        idOf(
            postRequest(
                    contractId,
                    tester,
                    "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(otherPhone))
                .andExpect(status().isCreated()));

    correct(contractId, smartphoneId, managerToken, "Pixel 8").andExpect(status().isOk());
    correct(contractId, otherPhone, managerToken, "Galaxy S24").andExpect(status().isOk());

    mockMvc
        .perform(get("/api/contracts/" + contractId + "/requests").header("Authorization", "Bearer " + tester))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='%s')].targetSmartphoneModel".formatted(replaceId)).value("Pixel 8"))
        .andExpect(jsonPath("$[?(@.id=='%s')].requestedModel".formatted(replaceId)).value("Pixel 9"))
        .andExpect(
            jsonPath("$[?(@.id=='%s')].returnedUnits[0].smartphoneModel".formatted(returnId))
                .value("Galaxy S24"));
  }

  private UUID idOf(ResultActions result) throws Exception {
    return UUID.fromString(
        objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText());
  }
}
