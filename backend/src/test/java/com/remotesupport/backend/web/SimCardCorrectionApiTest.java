package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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

  static Stream<Arguments> blankOrMissingNumberBodies() {
    return Stream.of(
        Arguments.of("a blank number", "{\"number\":\"   \"}"),
        Arguments.of("an empty number", "{\"number\":\"\"}"),
        Arguments.of("a null number", "{\"number\":null}"),
        Arguments.of("no number", "{}"));
  }

  @ParameterizedTest(name = "{0} is refused as NUMBER_BLANK and leaves the number unchanged")
  @MethodSource("blankOrMissingNumberBodies")
  void aBlankOrMissingNumberIsRefusedAndLeavesTheNumberUnchanged(String ignoredCase, String body)
      throws Exception {
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simCardId + "/number")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NUMBER_BLANK"))
        .andExpect(jsonPath("$.message").value("Enter a number."));
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

  // --- Postpaid plan (ticket correct-a-postpaid-plan) -----------------------------------------

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ClientInvoiceRepository clientInvoiceRepository;

  @PersistenceContext private EntityManager entityManager;

  /** Fixture for a SIM from before the Carrier catalog: rewrite the row, then forget the cached entity. */
  private void rewriteSim(String assignments, UUID simId) {
    entityManager.flush();
    jdbcTemplate.update("UPDATE sim_cards SET " + assignments + " WHERE id = ?", simId);
    entityManager.clear();
  }

  private record PostpaidSim(UUID simId, UUID carrierId, UUID planId) {}

  private PostpaidSim addPostpaidSim(String number, String price) throws Exception {
    UUID carrierId = createCarrierWithNoOptions(managerToken, contractId);
    UUID planId = createPostpaidPlan(managerToken, carrierId, "Plan " + UUID.randomUUID(), price);
    UUID sim =
        idOf(
            mockMvc
                .perform(
                    post("/api/contracts/" + contractId + "/sim-cards")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(APPLICATION_JSON)
                        .content(
                            "{\"number\":\"%s\",\"carrierId\":\"%s\",\"flavor\":\"POSTPAID\",\"postpaidPlanId\":\"%s\"}"
                                .formatted(number, carrierId, planId)))
                .andExpect(status().isCreated()));
    return new PostpaidSim(sim, carrierId, planId);
  }

  private ResultActions correctPlan(UUID contract, UUID sim, String token, UUID planId) throws Exception {
    MockHttpServletRequestBuilder request =
        patch("/api/contracts/" + contract + "/sim-cards/" + sim + "/postpaid-plan")
            .contentType(APPLICATION_JSON)
            .content(planId == null ? "{}" : "{\"postpaidPlanId\":\"%s\"}".formatted(planId));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return mockMvc.perform(request);
  }

  private void fleetShowsPlan(UUID sim, UUID planId, String fee) throws Exception {
    mockMvc
        .perform(get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='%s')].postpaidPlanId".formatted(sim)).value(planId.toString()))
        .andExpect(jsonPath("$[?(@.id=='%s')].monthlyFeeAmount".formatted(sim)).value(Double.parseDouble(fee)));
  }

  private JsonNode json(ResultActions actions) throws Exception {
    return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
  }

  private JsonNode agentClientInvoice() throws Exception {
    return json(
        mockMvc
            .perform(
                get("/api/contracts/" + contractId + "/client-invoice")
                    .header("Authorization", "Bearer " + agentToken()))
            .andExpect(status().isOk()));
  }

  private double fees() throws Exception {
    return json(
            mockMvc
                .perform(
                    get("/api/agents/" + SEEDED_AGENT_ID + "/invoice").header("Authorization", "Bearer " + agentToken()))
                .andExpect(status().isOk()))
        .get("localSupportFees")
        .asDouble();
  }

  @Test
  void aManagerAndAnyTesterOfTheClientCorrectThePlanAndTheFeeFollowsThePlansPrice() throws Exception {
    PostpaidSim sim = addPostpaidSim("+1-555-0801", "30.00");
    UUID cheaper = createPostpaidPlan(managerToken, sim.carrierId(), "Lite", "12.50");
    UUID dearer = createPostpaidPlan(managerToken, sim.carrierId(), "Max", "45.00");

    correctPlan(contractId, sim.simId(), managerToken, cheaper)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.postpaidPlanId").value(cheaper.toString()))
        .andExpect(jsonPath("$.monthlyFeeAmount").value(12.50));
    fleetShowsPlan(sim.simId(), cheaper, "12.50");

    String tester = createTesterAndLogin(managerToken, clientId, "plan." + UUID.randomUUID() + "@simcorr.example");
    correctPlan(contractId, sim.simId(), tester, dearer)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.monthlyFeeAmount").value(45.00));
    fleetShowsPlan(sim.simId(), dearer, "45.00");
  }

  @Test
  void aTesterOfAnotherClientAndAnyAgentAreRefusedNoTokenIs401AndAnotherTenantIs404() throws Exception {
    PostpaidSim sim = addPostpaidSim("+1-555-0802", "30.00");
    UUID other = createPostpaidPlan(managerToken, sim.carrierId(), "Other", "9.00");
    UUID otherClient = createClient(managerToken, "Other Plan Client " + UUID.randomUUID());
    String stranger = createTesterAndLogin(managerToken, otherClient, "s." + UUID.randomUUID() + "@simcorr.example");

    correctPlan(contractId, sim.simId(), stranger, other).andExpect(status().isForbidden());
    correctPlan(contractId, sim.simId(), agentToken(), other).andExpect(status().isForbidden());
    correctPlan(contractId, sim.simId(), null, other).andExpect(status().isUnauthorized());
    OtherTenantFixture.ContractWithSmartphone foreign = otherTenantFixture.smartphoneOnContractInAnotherTenant();
    correctPlan(foreign.contractId(), UUID.randomUUID(), managerToken, other).andExpect(status().isNotFound());
    fleetShowsPlan(sim.simId(), sim.planId(), "30.00");
  }

  @Test
  void aPlanOnAPrepaidSimIsRefused() throws Exception {
    UUID plan = createPostpaidPlan(managerToken, carrierFor(managerToken, contractId), "P", "10.00");
    correctPlan(contractId, simCardId, managerToken, plan)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PREPAID_HAS_NO_PLAN"));
  }

  private record PlanFixture(PostpaidSim sim, UUID foreignPlan, UUID archivedPlan) {}

  static Stream<Arguments> refusedPlans() {
    return Stream.of(
        Arguments.of("another Carrier's plan", (Function<PlanFixture, UUID>) PlanFixture::foreignPlan, "PLAN_NOT_ON_CARRIER"),
        Arguments.of("an unknown plan", (Function<PlanFixture, UUID>) f -> UUID.randomUUID(), "PLAN_NOT_ON_CARRIER"),
        Arguments.of("no plan id", (Function<PlanFixture, UUID>) f -> null, "PLAN_NOT_ON_CARRIER"),
        Arguments.of("an archived plan", (Function<PlanFixture, UUID>) PlanFixture::archivedPlan, "PLAN_ARCHIVED"));
  }

  @ParameterizedTest(name = "{0} is refused as {2} and the SIM is unchanged")
  @MethodSource("refusedPlans")
  void aRefusedPlanLeavesTheSimUnchanged(
      String ignoredCase, Function<PlanFixture, UUID> choice, String expectedCode) throws Exception {
    PostpaidSim sim = addPostpaidSim("+1-555-0803", "30.00");
    UUID foreignCarrier = createCarrierWithNoOptions(managerToken, contractId);
    UUID foreignPlan = createPostpaidPlan(managerToken, foreignCarrier, "Foreign", "5.00");
    UUID archived = createPostpaidPlan(managerToken, sim.carrierId(), "Legacy", "6.00");
    mockMvc
        .perform(
            post("/api/carriers/" + sim.carrierId() + "/postpaid-plans/" + archived + "/archive")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());

    correctPlan(contractId, sim.simId(), managerToken, choice.apply(new PlanFixture(sim, foreignPlan, archived)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(expectedCode));
    fleetShowsPlan(sim.simId(), sim.planId(), "30.00");
  }

  @Test
  void aSimWhoseCarrierIsArchivedOrAbsentTakesNoPlan() throws Exception {
    PostpaidSim archivedCarrierSim = addPostpaidSim("+1-555-0804", "30.00");
    UUID plan = createPostpaidPlan(managerToken, archivedCarrierSim.carrierId(), "Second", "8.00");
    archiveCarrier(managerToken, archivedCarrierSim.carrierId());
    correctPlan(contractId, archivedCarrierSim.simId(), managerToken, plan)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CARRIER_NOT_USABLE"));

    PostpaidSim noCarrierSim = addPostpaidSim("+1-555-0805", "30.00");
    rewriteSim("carrier_id = NULL, postpaid_plan_id = NULL", noCarrierSim.simId());
    correctPlan(contractId, noCarrierSim.simId(), managerToken, plan)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CARRIER_NOT_USABLE"));
  }

  @Test
  void aPreCatalogPostpaidSimWithACarrierIsGivenAPlanAndItsFeeBecomesThePlansPrice() throws Exception {
    PostpaidSim sim = addPostpaidSim("+1-555-0806", "30.00");
    rewriteSim("postpaid_plan_id = NULL", sim.simId());
    UUID newPlan = createPostpaidPlan(managerToken, sim.carrierId(), "Catalog", "21.00");

    correctPlan(contractId, sim.simId(), managerToken, newPlan)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.monthlyFeeAmount").value(21.00));
    fleetShowsPlan(sim.simId(), newPlan, "21.00");
  }

  @Test
  void aLaterRepricingOfThePlanLeavesTheCorrectedFeeAlone() throws Exception {
    PostpaidSim sim = addPostpaidSim("+1-555-0807", "30.00");
    UUID plan = createPostpaidPlan(managerToken, sim.carrierId(), "Reprice", "20.00");
    correctPlan(contractId, sim.simId(), managerToken, plan).andExpect(status().isOk());

    mockMvc
        .perform(
            patch("/api/carriers/" + sim.carrierId() + "/postpaid-plans/" + plan)
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Reprice\",\"price\":99.00}"))
        .andExpect(status().isOk());
    fleetShowsPlan(sim.simId(), plan, "20.00");
  }

  @Test
  void savingThePlanAlreadyHeldLogsNothingWhileAChangeLogsOneEventWithPlansAndFees() throws Exception {
    PostpaidSim sim = addPostpaidSim("+1-555-0808", "30.00");
    UUID plan = createPostpaidPlan(managerToken, sim.carrierId(), "Audited", "14.00");
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger audit = (Logger) LoggerFactory.getLogger("AUDIT");
    audit.addAppender(appender);
    try {
      correctPlan(contractId, sim.simId(), managerToken, sim.planId()).andExpect(status().isOk());
      assertThat(planCorrections(appender)).isEmpty();
      fleetShowsPlan(sim.simId(), sim.planId(), "30.00");

      correctPlan(contractId, sim.simId(), managerToken, plan).andExpect(status().isOk());
      assertThat(planCorrections(appender))
          .singleElement()
          .satisfies(
              line ->
                  assertThat(line)
                      .contains("entityId=" + sim.simId())
                      .contains("contractId=" + contractId)
                      .contains("oldPostpaidPlanId=" + sim.planId())
                      .contains("newPostpaidPlanId=" + plan)
                      .contains("oldMonthlyFeeAmount=30.00")
                      .contains("newMonthlyFeeAmount=14.00")
                      .contains("role=MANAGER")
                      .contains("actorUserId=")
                      .contains("tenantId=" + SEEDED_TENANT_ID));
    } finally {
      audit.detachAppender(appender);
    }
  }

  private static List<String> planCorrections(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .filter(m -> m.contains("action=simCardPostpaidPlanCorrected"))
        .toList();
  }

  @Test
  void aDraftClientInvoiceAndTheDraftAgentInvoiceMoveByThePriceDifference() throws Exception {
    PostpaidSim sim = addPostpaidSim("+1-555-0810", "30.00");
    UUID plan = createPostpaidPlan(managerToken, sim.carrierId(), "Moves", "42.00");
    double feesBefore = fees();
    assertThat(agentClientInvoice().get("baseAmount").asDouble()).isEqualTo(30.00);

    correctPlan(contractId, sim.simId(), managerToken, plan).andExpect(status().isOk());

    assertThat(agentClientInvoice().get("baseAmount").asDouble()).isEqualTo(42.00);
    assertThat(agentClientInvoice().get("basePostpaidSims").get(0).get("amount").asDouble()).isEqualTo(42.00);
    assertThat(fees()).isEqualTo(feesBefore + 12.00);
  }

  @Test
  void anEarlierNeverSentDraftMovesToo() throws Exception {
    PostpaidSim sim = addPostpaidSim("+1-555-0811", "30.00");
    UUID plan = createPostpaidPlan(managerToken, sim.carrierId(), "Late", "35.00");
    UUID invoiceId = UUID.fromString(agentClientInvoice().get("id").asText());
    ClientInvoice invoice = clientInvoiceRepository.findById(invoiceId).orElseThrow();
    invoice.setBillingMonth(invoice.getBillingMonth().minusMonths(1));
    clientInvoiceRepository.saveAndFlush(invoice);

    correctPlan(contractId, sim.simId(), managerToken, plan).andExpect(status().isOk());

    mockMvc
        .perform(get("/api/client-invoices/" + invoiceId).header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.baseAmount").value(35.00));
  }

  @Test
  void aStoredLineDraftASentInvoiceAndASentBackInvoiceKeepTheirAmounts() throws Exception {
    PostpaidSim stored = addPostpaidSim("+1-555-0812", "30.00");
    UUID plan = createPostpaidPlan(managerToken, stored.carrierId(), "Ignored", "50.00");
    mockMvc
        .perform(
            put("/api/contracts/" + contractId + "/client-invoice/lines")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"kind\":\"POSTPAID_SIM\",\"sourceId\":\"%s\",\"amount\":31.00}".formatted(stored.simId())))
        .andExpect(status().isOk());

    correctPlan(contractId, stored.simId(), managerToken, plan).andExpect(status().isOk());
    assertThat(agentClientInvoice().get("baseAmount").asDouble()).isEqualTo(31.00);

    JsonNode sent =
        json(
            mockMvc
                .perform(
                    post("/api/contracts/" + contractId + "/client-invoice/send")
                        .header("Authorization", "Bearer " + agentToken()))
                .andExpect(status().isOk()));
    UUID invoiceId = UUID.fromString(sent.get("id").asText());
    UUID other = createPostpaidPlan(managerToken, stored.carrierId(), "After send", "70.00");
    correctPlan(contractId, stored.simId(), managerToken, other).andExpect(status().isOk());
    assertThat(agentClientInvoice().get("baseAmount").asDouble()).isEqualTo(31.00);

    mockMvc
        .perform(
            post("/api/client-invoices/" + invoiceId + "/send-back")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"Check the plan\"}"))
        .andExpect(status().isOk());
    UUID third = createPostpaidPlan(managerToken, stored.carrierId(), "After send back", "80.00");
    correctPlan(contractId, stored.simId(), managerToken, third).andExpect(status().isOk());
    assertThat(agentClientInvoice().get("baseAmount").asDouble()).isEqualTo(31.00);
  }

  @Test
  void aSentApprovedAndPaidAgentInvoiceKeepItsFeesWhenThePlanIsCorrected() throws Exception {
    PostpaidSim sim = addPostpaidSim("+1-555-0813", "30.00");
    UUID plan = createPostpaidPlan(managerToken, sim.carrierId(), "After agent send", "60.00");
    UUID agentInvoiceId =
        UUID.fromString(
            json(
                    mockMvc
                        .perform(
                            post("/api/agents/" + SEEDED_AGENT_ID + "/invoice/send")
                                .header("Authorization", "Bearer " + agentToken()))
                        .andExpect(status().isOk()))
                .get("id")
                .asText());
    double frozen = fees();

    correctPlan(contractId, sim.simId(), managerToken, plan).andExpect(status().isOk());
    assertThat(fees()).isEqualTo(frozen);

    for (String action : List.of("approve", "paid")) {
      mockMvc
          .perform(
              post("/api/agent-invoices/" + agentInvoiceId + "/" + action)
                  .header("Authorization", "Bearer " + managerToken)
                  .contentType(APPLICATION_JSON)
                  .content(""))
          .andExpect(status().isOk());
      UUID next = createPostpaidPlan(managerToken, sim.carrierId(), "After " + action, "77.00");
      correctPlan(contractId, sim.simId(), managerToken, next).andExpect(status().isOk());
      assertThat(fees()).isEqualTo(frozen);
    }
  }

  private UUID idOf(ResultActions result) throws Exception {
    return UUID.fromString(
        objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText());
  }
}
