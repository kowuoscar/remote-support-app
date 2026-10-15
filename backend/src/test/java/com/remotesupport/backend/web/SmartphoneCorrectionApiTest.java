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
import com.remotesupport.backend.support.OtherTenantFixture;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
  void aTesterOfTheContractsClientIsRefusedTheModelPrimaryContactOrNotAndItIsUnchanged() throws Exception {
    String plain = createTesterAndLogin(managerToken, clientId, "plain." + UUID.randomUUID() + "@corr.example");
    correct(contractId, smartphoneId, plain, "Pixel 8").andExpect(status().isForbidden());

    String primary = primaryContactToken();
    correct(contractId, smartphoneId, primary, "Pixel 9").andExpect(status().isForbidden());
    fleetShowsModel(managerToken, "Pixle 8");
  }

  @Test
  void aTesterOfAnotherClientIsRefusedAndAnAgentOnAnotherAgentsContractToo() throws Exception {
    UUID otherClient = createClient(managerToken, "Other Corrections Client " + UUID.randomUUID());
    String stranger =
        createTesterAndLogin(managerToken, otherClient, "stranger." + UUID.randomUUID() + "@corr.example");

    correct(contractId, smartphoneId, stranger, "Hacked").andExpect(status().isForbidden());
    UUID otherContract =
        createContract(managerToken, clientId, createAgent(managerToken, "Other Agent", Country.FRANCE));
    UUID otherPhone = createSmartphone(managerToken, otherContract, "Not yours");
    correct(otherContract, otherPhone, agentToken(), "Hacked").andExpect(status().isForbidden());
    fleetShowsModel(managerToken, "Pixle 8");
  }

  @Test
  void theContractsOwnAgentCorrectsTheModelAndTheFleetShowsIt() throws Exception {
    correct(contractId, smartphoneId, agentToken(), " Pixel 8 Pro ")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model").value("Pixel 8 Pro"));

    fleetShowsModel(agentToken(), "Pixel 8 Pro");
  }

  @Test
  void aUnitInTheAgentsOwnStockSentUnderTheirContractsPathIs404AndUnchanged() throws Exception {
    UUID inStock = smartphoneInAgentStock();

    correct(contractId, inStock, agentToken(), "Hacked").andExpect(status().isNotFound());
    correctOwner(contractId, inStock, agentToken(), "CLIENT").andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/stock").header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='%s')].model".formatted(inStock)).value("Stocked"));
  }

  /** A Company phone returned, approved as kept in stock and completed: now in the Agent's stock. */
  private UUID smartphoneInAgentStock() throws Exception {
    UUID phone = createSmartphone(managerToken, contractId, "Stocked");
    JsonNode body =
        objectMapper.readTree(
            postRequest(
                    contractId,
                    clientTester(),
                    "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(phone))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    UUID returnId = UUID.fromString(body.get("id").asText());
    String unitId = body.get("returnedUnits").get(0).get("id").asText();
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
    return phone;
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

  static Stream<Arguments> blankOrMissingModelBodies() {
    return Stream.of(
        Arguments.of("a blank model", "{\"model\":\"   \"}"),
        Arguments.of("an empty model", "{\"model\":\"\"}"),
        Arguments.of("a null model", "{\"model\":null}"),
        Arguments.of("no model", "{}"));
  }

  @ParameterizedTest(name = "{0} is refused as MODEL_BLANK and leaves the model unchanged")
  @MethodSource("blankOrMissingModelBodies")
  void aBlankOrMissingModelIsRefusedAndLeavesTheModelUnchanged(String ignoredCase, String body)
      throws Exception {
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/smartphones/" + smartphoneId + "/model")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MODEL_BLANK"))
        .andExpect(jsonPath("$.message").value("Enter a model."));
    fleetShowsModel(managerToken, "Pixle 8");
  }

  @Test
  void aMissingOwnerIsRefusedAsOwnerInvalid() throws Exception {
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/smartphones/" + smartphoneId + "/owner")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("OWNER_INVALID"))
        .andExpect(jsonPath("$.message").value("Choose Client or Company."));
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

  // --- owner (ticket correct-a-smartphone-owner) ----------------------------------------------

  private ResultActions correctOwner(UUID contract, UUID phone, String token, String owner) throws Exception {
    MockHttpServletRequestBuilder request =
        patch("/api/contracts/" + contract + "/smartphones/" + phone + "/owner")
            .contentType(APPLICATION_JSON)
            .content("{\"owner\":\"%s\"}".formatted(owner));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return mockMvc.perform(request);
  }

  private void fleetShowsOwner(String token, UUID phone, String owner) throws Exception {
    mockMvc
        .perform(get("/api/contracts/" + contractId + "/smartphones").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='%s')].owner".formatted(phone)).value(owner));
  }

  private UUID returnOf(String token, UUID phone) throws Exception {
    return idOf(
        postRequest(
                contractId,
                token,
                "{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(phone))
            .andExpect(status().isCreated()));
  }

  private String clientTester() throws Exception {
    return createTesterAndLogin(managerToken, clientId, "own." + UUID.randomUUID() + "@corr.example");
  }

  @Test
  void aManagerCorrectsTheOwnerInBothDirectionsAndATesterOfTheOwnClientIsRefused() throws Exception {
    fleetShowsOwner(managerToken, smartphoneId, "COMPANY");
    correctOwner(contractId, smartphoneId, managerToken, "CLIENT")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.owner").value("CLIENT"));
    fleetShowsOwner(managerToken, smartphoneId, "CLIENT");

    String tester = clientTester();
    correctOwner(contractId, smartphoneId, tester, "COMPANY").andExpect(status().isForbidden());
    fleetShowsOwner(managerToken, smartphoneId, "CLIENT");
  }

  @Test
  void aTesterOfAnotherClientAndAnAgentOfAnotherContractCannotCorrectTheOwnerAndNoTokenOrOtherTenantIsRefused()
      throws Exception {
    UUID otherClient = createClient(managerToken, "Other Owner Client " + UUID.randomUUID());
    String stranger =
        createTesterAndLogin(managerToken, otherClient, "stranger." + UUID.randomUUID() + "@corr.example");

    correctOwner(contractId, smartphoneId, stranger, "CLIENT").andExpect(status().isForbidden());
    UUID otherContract =
        createContract(managerToken, clientId, createAgent(managerToken, "Other Agent", Country.FRANCE));
    UUID otherPhone = createSmartphone(managerToken, otherContract, "Not yours");
    correctOwner(otherContract, otherPhone, agentToken(), "CLIENT").andExpect(status().isForbidden());
    correctOwner(contractId, smartphoneId, null, "CLIENT").andExpect(status().isUnauthorized());
    OtherTenantFixture.ContractWithSmartphone foreign = otherTenantFixture.smartphoneOnContractInAnotherTenant();
    correctOwner(foreign.contractId(), foreign.smartphoneId(), managerToken, "CLIENT")
        .andExpect(status().isNotFound());
    fleetShowsOwner(managerToken, smartphoneId, "COMPANY");
  }

  @Test
  void anOwnerChangeIsRefusedWhileAReturnNamingThePhoneIsPendingApprovalSubmittedOrInProgress() throws Exception {
    String tester = clientTester();

    // Pending Approval: a Company-owned phone's Return.
    returnOf(tester, smartphoneId);
    correctOwner(contractId, smartphoneId, managerToken, "CLIENT")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SMARTPHONE_ON_OPEN_RETURN"));
    fleetShowsOwner(managerToken, smartphoneId, "COMPANY");

    // Submitted, then In Progress: a Client-owned phone's Return.
    UUID clientPhone = createClientOwnedSmartphone("Pixel 7");
    UUID submitted = returnOf(tester, clientPhone);
    correctOwner(contractId, clientPhone, managerToken, "COMPANY")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SMARTPHONE_ON_OPEN_RETURN"));
    patchStatus(contractId, submitted, agentToken(), "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
    correctOwner(contractId, clientPhone, managerToken, "COMPANY")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SMARTPHONE_ON_OPEN_RETURN"));
    fleetShowsOwner(managerToken, clientPhone, "CLIENT");
  }

  @Test
  void theOwnerCanBeCorrectedOnceTheReturnIsCancelledCompletedOrRejected() throws Exception {
    String tester = clientTester();

    UUID cancelledPhone = createSmartphone(managerToken, contractId, "Cancelled one");
    UUID cancelled = returnOf(tester, cancelledPhone);
    patchStatus(
            contractId,
            cancelled,
            agentToken(),
            "{\"status\":\"CANCELLED\",\"cancellationReason\":\"No longer needed\"}").andExpect(status().isOk());
    correctOwner(contractId, cancelledPhone, managerToken, "CLIENT").andExpect(status().isOk());

    UUID rejectedPhone = createSmartphone(managerToken, contractId, "Rejected one");
    UUID rejected = returnOf(tester, rejectedPhone);
    rejectAsManager(managerToken, rejected, "Not now");
    correctOwner(contractId, rejectedPhone, managerToken, "CLIENT").andExpect(status().isOk());

    UUID completedPhone = createClientOwnedSmartphone("Completed one");
    UUID completed = returnOf(tester, completedPhone);
    patchStatus(contractId, completed, agentToken(), "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
    patchStatus(contractId, completed, agentToken(), "{\"status\":\"COMPLETED\"}").andExpect(status().isOk());
    correctOwner(contractId, completedPhone, managerToken, "COMPANY").andExpect(status().isOk());
  }

  @Test
  void theContractsOwnAgentCorrectsTheOwnerButNotWhileAReturnNamesThePhone() throws Exception {
    correctOwner(contractId, smartphoneId, agentToken(), "CLIENT")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.owner").value("CLIENT"));
    fleetShowsOwner(agentToken(), smartphoneId, "CLIENT");

    UUID company = createSmartphone(managerToken, contractId, "Company one");
    returnOf(clientTester(), company);
    correctOwner(contractId, company, agentToken(), "CLIENT")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SMARTPHONE_ON_OPEN_RETURN"));
    fleetShowsOwner(managerToken, company, "COMPANY");
  }

  @Test
  void anAgentsCorrectionsLogOneEventEachWithRoleAgentAndANoOpLogsNone() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger audit = (Logger) LoggerFactory.getLogger("AUDIT");
    audit.addAppender(appender);
    try {
      correct(contractId, smartphoneId, agentToken(), "Pixle 8").andExpect(status().isOk());
      correctOwner(contractId, smartphoneId, agentToken(), "COMPANY").andExpect(status().isOk());
      assertThat(corrections(appender)).isEmpty();
      assertThat(ownerEvents(appender)).isEmpty();

      correct(contractId, smartphoneId, agentToken(), "Pixel 8").andExpect(status().isOk());
      correctOwner(contractId, smartphoneId, agentToken(), "CLIENT").andExpect(status().isOk());
      assertThat(corrections(appender))
          .singleElement()
          .satisfies(
              line ->
                  assertThat(line)
                      .contains("contractId=" + contractId)
                      .contains("entityId=" + smartphoneId)
                      .contains("oldModel=Pixle 8")
                      .contains("newModel=Pixel 8")
                      .contains("role=AGENT"));
      assertThat(ownerEvents(appender))
          .singleElement()
          .satisfies(
              line ->
                  assertThat(line)
                      .contains("oldOwner=COMPANY")
                      .contains("newOwner=CLIENT")
                      .contains("role=AGENT"));
    } finally {
      audit.detachAppender(appender);
    }
  }

  @Test
  void aReturnRaisedAfterTheCorrectionFollowsTheCorrectedOwnersPath() throws Exception {
    String tester = clientTester();
    UUID toClient = createSmartphone(managerToken, contractId, "Was company");
    correctOwner(contractId, toClient, managerToken, "CLIENT").andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + tester)
                .contentType(APPLICATION_JSON)
                .content("{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(toClient)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.returnedUnits[0].disposition").value("POSTED_TO_CLIENT"));

    UUID toCompany = createClientOwnedSmartphone("Was client");
    correctOwner(contractId, toCompany, managerToken, "COMPANY").andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + tester)
                .contentType(APPLICATION_JSON)
                .content("{\"type\":\"RETURN\",\"returnedSmartphoneIds\":[\"%s\"]}".formatted(toCompany)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
  }

  @Test
  void savingTheOwnerAlreadyHeldIsA200EvenOnAPhoneWithAnOpenReturn() throws Exception {
    correctOwner(contractId, smartphoneId, managerToken, "COMPANY")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.owner").value("COMPANY"));
    returnOf(clientTester(), smartphoneId);
    correctOwner(contractId, smartphoneId, managerToken, "COMPANY").andExpect(status().isOk());
  }

  @Test
  void anOwnerChangeLogsOneEventAndAnUnchangedOwnerLogsNone() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger audit = (Logger) LoggerFactory.getLogger("AUDIT");
    audit.addAppender(appender);
    try {
      correctOwner(contractId, smartphoneId, managerToken, "COMPANY").andExpect(status().isOk());
      assertThat(ownerEvents(appender)).isEmpty();

      correctOwner(contractId, smartphoneId, managerToken, "CLIENT").andExpect(status().isOk());
      assertThat(ownerEvents(appender))
          .singleElement()
          .satisfies(
              line ->
                  assertThat(line)
                      .contains("entityId=" + smartphoneId)
                      .contains("contractId=" + contractId)
                      .contains("oldOwner=COMPANY")
                      .contains("newOwner=CLIENT")
                      .contains("role=MANAGER")
                      .contains("tenantId=" + SEEDED_TENANT_ID));
    } finally {
      audit.detachAppender(appender);
    }
  }

  private static List<String> ownerEvents(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .filter(m -> m.contains("action=smartphoneOwnerCorrected"))
        .toList();
  }

  private UUID createClientOwnedSmartphone(String model) throws Exception {
    UUID id = createSmartphone(managerToken, contractId, model);
    correctOwner(contractId, id, managerToken, "CLIENT").andExpect(status().isOk());
    return id;
  }
}
