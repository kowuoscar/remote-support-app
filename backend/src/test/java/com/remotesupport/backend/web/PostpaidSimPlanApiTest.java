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
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A new Postpaid SIM names a Postpaid Plan, and its monthly fee is copied from the Plan's price
 * (carrier-catalog spec, SIM Card changes and Why copy the price; postpaid-sim-plan ticket). The
 * same rule holds on every SIM-creation path, so each case runs once per {@link Path}.
 */
class PostpaidSimPlanApiTest extends IntegrationTest {

  /** The four ways a SIM Card comes into a Fleet. */
  enum Path {
    MANAGER_ADDS_TO_FLEET,
    AGENT_COMPLETES_PROVISION_SIM_REQUEST,
    AGENT_LOGS_PROVISION_SIM_REQUEST_COMPLETED,
    AGENT_LOGS_PROVISION_SIM_FEE
  }

  private String managerToken;
  private String agentToken;
  private String testerToken;
  private UUID contractId;
  private UUID testerId;
  private UUID carrierId;

  @BeforeEach
  void setUp() throws Exception {
    managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    testerToken = createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");
    agentToken = agentToken();
    testerId = findTesterId();
    carrierId = createCarrier(managerToken, Country.UNITED_STATES, "Mint Mobile");
  }

  @ParameterizedTest
  @EnumSource(Path.class)
  void aPostpaidSimsMonthlyFeeIsCopiedFromItsPlan(Path path) throws Exception {
    UUID planId = createPostpaidPlan(managerToken, carrierId, "Unlimited 30", "30.00");

    create(path, "POSTPAID", "\"postpaidPlanId\":\"" + planId + "\"").andExpect(status().is2xxSuccessful());

    fleet()
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].flavor").value("POSTPAID"))
        .andExpect(jsonPath("$[0].monthlyFeeAmount").value(30.00))
        .andExpect(jsonPath("$[0].postpaidPlanId").value(planId.toString()))
        .andExpect(jsonPath("$[0].postpaidPlanName").value("Unlimited 30"))
        .andExpect(jsonPath("$[0].postpaidPlanArchived").value(false));
  }

  @ParameterizedTest
  @EnumSource(Path.class)
  void aPostpaidSimWithoutAPlanIsRefused(Path path) throws Exception {
    create(path, "POSTPAID", null).andExpect(status().isBadRequest());
    assertFleetIsEmpty();
  }

  @ParameterizedTest
  @EnumSource(Path.class)
  void aPrepaidSimNamingAPlanIsRefused(Path path) throws Exception {
    UUID planId = createPostpaidPlan(managerToken, carrierId, "Unlimited 30", "30.00");

    create(path, "PREPAID", "\"postpaidPlanId\":\"" + planId + "\"").andExpect(status().isBadRequest());
    assertFleetIsEmpty();
  }

  @ParameterizedTest
  @EnumSource(Path.class)
  void aPlanOfAnotherCarrierIsRefused(Path path) throws Exception {
    UUID otherCarrier = createCarrier(managerToken, Country.UNITED_STATES, "Cricket Wireless");
    UUID otherCarriersPlan = createPostpaidPlan(managerToken, otherCarrier, "Cricket Unlimited", "40.00");

    create(path, "POSTPAID", "\"postpaidPlanId\":\"" + otherCarriersPlan + "\"")
        .andExpect(status().isBadRequest());
    assertFleetIsEmpty();
  }

  @ParameterizedTest
  @EnumSource(Path.class)
  void anArchivedPlanIsRefused(Path path) throws Exception {
    UUID planId = createPostpaidPlan(managerToken, carrierId, "Legacy 20", "20.00");
    mockMvc
        .perform(
            post("/api/carriers/" + carrierId + "/postpaid-plans/" + planId + "/archive")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());

    create(path, "POSTPAID", "\"postpaidPlanId\":\"" + planId + "\"").andExpect(status().isBadRequest());
    assertFleetIsEmpty();
  }

  @Test
  void aPrepaidSimCarriesNoPlanAndNoMonthlyFee() throws Exception {
    create(Path.MANAGER_ADDS_TO_FLEET, "PREPAID", null).andExpect(status().isCreated());

    fleet()
        .andExpect(jsonPath("$[0].monthlyFeeAmount").doesNotExist())
        .andExpect(jsonPath("$[0].postpaidPlanId").doesNotExist())
        .andExpect(jsonPath("$[0].postpaidPlanName").doesNotExist());
  }

  @Test
  void aFreeTypedMonthlyFeeIsIgnoredInFavourOfThePlansPrice() throws Exception {
    UUID planId = createPostpaidPlan(managerToken, carrierId, "Unlimited 30", "30.00");

    postJsonText(
            "/api/contracts/" + contractId + "/sim-cards",
            managerToken,
            """
            {"number":"+1-555-0143","carrierId":"%s","flavor":"POSTPAID","postpaidPlanId":"%s","monthlyFeeAmount":5.00}
            """
                .formatted(carrierId, planId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.monthlyFeeAmount").value(30.00));
  }

  @Test
  void repricingAPlanLeavesASimCardAlreadyOnItAndItsClientInvoiceUntouched() throws Exception {
    UUID planId = createPostpaidPlan(managerToken, carrierId, "Unlimited 30", "30.00");
    create(Path.MANAGER_ADDS_TO_FLEET, "POSTPAID", "\"postpaidPlanId\":\"" + planId + "\"")
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            patch("/api/carriers/" + carrierId + "/postpaid-plans/" + planId)
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"name":"Unlimited 30","price":99.00}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.price").value(99.00));

    fleet().andExpect(jsonPath("$[0].monthlyFeeAmount").value(30.00));

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseAmount").value(30.00));
  }

  @Test
  void aSentClientInvoicesTotalDoesNotMoveWhenThePlanIsRepriced() throws Exception {
    UUID planId = createPostpaidPlan(managerToken, carrierId, "Unlimited 30", "30.00");
    create(Path.MANAGER_ADDS_TO_FLEET, "POSTPAID", "\"postpaidPlanId\":\"" + planId + "\"")
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/client-invoice/send")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAmount").value(30.00));

    mockMvc
        .perform(
            patch("/api/carriers/" + carrierId + "/postpaid-plans/" + planId)
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"name":"Unlimited 30","price":99.00}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/client-invoice").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseAmount").value(30.00))
        .andExpect(jsonPath("$.totalAmount").value(30.00));
  }

  @Test
  void aSimCardOnAPlanArchivedLaterStillShowsItsNameMarkedArchived() throws Exception {
    UUID planId = createPostpaidPlan(managerToken, carrierId, "Unlimited 30", "30.00");
    create(Path.MANAGER_ADDS_TO_FLEET, "POSTPAID", "\"postpaidPlanId\":\"" + planId + "\"")
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/carriers/" + carrierId + "/postpaid-plans/" + planId + "/archive")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk());

    fleet()
        .andExpect(jsonPath("$[0].postpaidPlanName").value("Unlimited 30"))
        .andExpect(jsonPath("$[0].postpaidPlanArchived").value(true))
        .andExpect(jsonPath("$[0].monthlyFeeAmount").value(30.00));
  }

  @Test
  void provisioningAPostpaidSimLogsItsPlanAndCopiedFee() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      UUID planId = createPostpaidPlan(managerToken, carrierId, "Unlimited 30", "30.00");
      create(Path.AGENT_LOGS_PROVISION_SIM_FEE, "POSTPAID", "\"postpaidPlanId\":\"" + planId + "\"")
          .andExpect(status().isCreated());

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=FLEET_ITEM_PROVISIONED");
      Assertions.assertThat(logged).contains("postpaidPlanId=" + planId);
      Assertions.assertThat(logged).contains("monthlyFeeAmount=30.00");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  private void assertFleetIsEmpty() throws Exception {
    fleet().andExpect(jsonPath("$.length()").value(0));
  }

  /**
   * Creates a SIM Card on the Carrier set up for this test through {@code path}; {@code planField}
   * is an extra JSON member or null.
   */
  private ResultActions create(Path path, String flavor, String planField) throws Exception {
    String simCard =
        "{\"number\":\"+1-555-0142\",\"carrierId\":\"%s\",\"flavor\":\"%s\"%s}"
            .formatted(carrierId, flavor, planField == null ? "" : "," + planField);
    return switch (path) {
      case MANAGER_ADDS_TO_FLEET -> postJsonText("/api/contracts/" + contractId + "/sim-cards", managerToken, simCard);
      case AGENT_COMPLETES_PROVISION_SIM_REQUEST -> {
        UUID requestId = submitProvisionSimRequest();
        patchStatus(requestId, "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
        yield patchStatus(requestId, "{\"status\":\"COMPLETED\",\"newSimCard\":" + simCard + "}");
      }
      case AGENT_LOGS_PROVISION_SIM_REQUEST_COMPLETED ->
          postJsonText(
              "/api/contracts/" + contractId + "/requests",
              agentToken,
              "{\"type\":\"PROVISION_SIM\",\"testerId\":\"%s\",\"startingStatus\":\"COMPLETED\",\"newSimCard\":%s}"
                  .formatted(testerId, simCard));
      case AGENT_LOGS_PROVISION_SIM_FEE ->
          postJsonText(
              "/api/contracts/" + contractId + "/fees",
              agentToken,
              "{\"feeType\":\"PROVISION_SIM\",\"amount\":15.00,\"testerId\":\"%s\",\"newSimCard\":%s}"
                  .formatted(testerId, simCard));
    };
  }

  private ResultActions fleet() throws Exception {
    return mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/sim-cards").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk());
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

  private UUID submitProvisionSimRequest() throws Exception {
    MvcResult result =
        postJsonText("/api/contracts/" + contractId + "/requests", testerToken, "{\"type\":\"PROVISION_SIM\"}")
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
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
