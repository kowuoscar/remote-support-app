package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A Topup Fee logged from a Topup Option (carrier-catalog spec, Fee changes; topup-fee-from-option
 * ticket). The Option only suggests the amount: the Fee keeps whatever amount the Agent submits,
 * and a reference to the Option. The seeded Agent (agent@example.com) is in the United States,
 * where AT&T sells the seeded "Prepaid Refill 25" Option.
 */
@Import(OtherTenantFixture.class)
class TopupFeeFromOptionApiTest extends IntegrationTest {

  private static final UUID SEEDED_ATT = UUID.fromString("c0000000-0000-0000-0000-000000000001");
  private static final UUID SEEDED_ATT_REFILL_25 =
      UUID.fromString("d0000000-0000-0000-0000-000000000001");

  @Autowired private OtherTenantFixture otherTenantFixture;

  private String managerToken;
  private String agentToken;
  private UUID contractId;
  private UUID testerId;
  // reboot-and-topup-details ticket: a Topup Request/Fee now names the SIM Card it tops up — on
  // the seeded AT&T Carrier, matching SEEDED_ATT_REFILL_25's own Carrier.
  private UUID simCardOnAtt;

  @BeforeEach
  void aContractOfTheSeededAgentWithOneTester() throws Exception {
    managerToken = managerToken();
    agentToken = agentToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    testerId =
        idOf(
            postJson(
                    "/api/clients/" + clientId + "/testers",
                    managerToken,
                    Map.of("username", "priya.raman@aurora.example", "password", "Passw0rd!23"))
                .andExpect(status().isCreated())
                .andReturn());
    simCardOnAtt = createSimCard(managerToken, contractId, SEEDED_ATT);
  }

  @Test
  void aTopupFeeFromAnOptionKeepsTheOptionAndTheAdjustedAmount() throws Exception {
    logFee("TOPUP", "27.50", SEEDED_ATT_REFILL_25)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.feeType").value("TOPUP"))
        .andExpect(jsonPath("$.amount").value(27.50))
        .andExpect(jsonPath("$.topupOptionId").value(SEEDED_ATT_REFILL_25.toString()))
        .andExpect(jsonPath("$.topupOptionName").value("Prepaid Refill 25"));

    mockMvc
        .perform(get(feesUrl()).header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].amount").value(27.50))
        .andExpect(jsonPath("$[0].topupOptionId").value(SEEDED_ATT_REFILL_25.toString()));
  }

  @Test
  void aTopupFeeAgainstAnExistingTopupRequestCanNameAnOption() throws Exception {
    // reboot-and-topup-details ticket: submitting a Topup Request now requires its target SIM
    // Card and, since this SIM Card's Carrier (AT&T) has an active Option, that Option too.
    String testerToken = loginAs("priya.raman@aurora.example", "Passw0rd!23");
    UUID requestId =
        idOf(
            postJson(
                    "/api/contracts/" + contractId + "/requests",
                    testerToken,
                    Map.of(
                        "type", "TOPUP",
                        "targetSimCardId", simCardOnAtt,
                        "topupOptionId", SEEDED_ATT_REFILL_25))
                .andExpect(status().isCreated())
                .andReturn());

    postJson(
            feesUrl(),
            agentToken,
            Map.of(
                "requestId", requestId,
                "feeType", "TOPUP",
                "amount", "25.00",
                "topupOptionId", SEEDED_ATT_REFILL_25))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.requestId").value(requestId.toString()))
        .andExpect(jsonPath("$.topupOptionId").value(SEEDED_ATT_REFILL_25.toString()));
  }

  @Test
  void aTopupFeeWithNoOptionStillWorksWhenTheCarrierHasNoActiveOption() throws Exception {
    // reboot-and-topup-details ticket: naming no Option is only still allowed when the target SIM
    // Card's Carrier truly has none active — unlike AT&T (SEEDED_ATT), which does.
    UUID carrierWithNoOptions = createCarrierWithNoOptions(managerToken, contractId);
    UUID simCardWithNoOptions = createSimCard(managerToken, contractId, carrierWithNoOptions);

    Map<String, Object> body = new HashMap<>();
    body.put("feeType", "TOPUP");
    body.put("amount", "12.00");
    body.put("testerId", testerId);
    body.put("targetSimCardId", simCardWithNoOptions);
    body.put("description", "Cash top-up at kiosk");

    postJson(feesUrl(), agentToken, body)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.amount").value(12.00))
        .andExpect(jsonPath("$.topupOptionId").doesNotExist())
        .andExpect(jsonPath("$.topupOptionName").doesNotExist());
  }

  @Test
  void anOptionIsRefusedOnAFeeThatIsNotATopupFee() throws Exception {
    logFee("OTHER", "40.00", SEEDED_ATT_REFILL_25).andExpect(status().isBadRequest());
    assertNoFeeLogged();
  }

  @Test
  void anOptionOfAnotherCountrysCarrierIsRefused() throws Exception {
    UUID orange = createCarrier("FRANCE", "Orange");
    UUID orangeOption = createOption(orange, "Recharge 20", "20.00");

    logFee("TOPUP", "20.00", orangeOption).andExpect(status().isBadRequest());
    assertNoFeeLogged();
  }

  @Test
  void anOptionOfAnotherTenantIsRefused() throws Exception {
    UUID foreignOption = otherTenantFixture.topupOptionInAnotherTenant();

    logFee("TOPUP", "20.00", foreignOption).andExpect(status().isBadRequest());
    assertNoFeeLogged();
  }

  @Test
  void anArchivedOptionIsRefused() throws Exception {
    UUID option = createOption(SEEDED_ATT, "Weekend Pass", "8.00");
    postJson("/api/carriers/" + SEEDED_ATT + "/topup-options/" + option + "/archive", agentToken, Map.of())
        .andExpect(status().isOk());

    logFee("TOPUP", "8.00", option).andExpect(status().isBadRequest());
    assertNoFeeLogged();
  }

  @Test
  void anOptionOfAnArchivedCarrierIsRefused() throws Exception {
    UUID carrier = createCarrier("UNITED_STATES", "Boost Mobile");
    UUID option = createOption(carrier, "Refill 30", "30.00");
    postJson("/api/carriers/" + carrier + "/archive", agentToken, Map.of()).andExpect(status().isOk());

    logFee("TOPUP", "30.00", option).andExpect(status().isBadRequest());
    assertNoFeeLogged();
  }

  @Test
  void editingAnOptionsPriceLeavesAnExistingFeeUnchanged() throws Exception {
    UUID option = createOption(SEEDED_ATT, "Refill 40", "40.00");
    logFee("TOPUP", "40.00", option).andExpect(status().isCreated());

    mockMvc
        .perform(
            patch("/api/carriers/" + SEEDED_ATT + "/topup-options/" + option)
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Refill 40", "price", "45.00"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(get(feesUrl()).header("Authorization", "Bearer " + agentToken))
        .andExpect(jsonPath("$[0].amount").value(40.00))
        .andExpect(jsonPath("$[0].topupOptionId").value(option.toString()));
  }

  @Test
  void theFeeLoggedAuditEntryNamesTheOption() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);
    try {
      logFee("TOPUP", "26.00", SEEDED_ATT_REFILL_25).andExpect(status().isCreated());

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertThat(logged)
          .contains("action=FEE_LOGGED")
          .contains("amount=26.00")
          .contains("topupOptionId=" + SEEDED_ATT_REFILL_25);
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  @Test
  void aLoggedTopupFeeAppearsInTheContractsFeeListWithItsOptionName() throws Exception {
    // Used to assert this against the seeded demo Topup Fee (V26 migration); that row is gone
    // (trim-seed-to-test-baseline ticket), so this builds its own via the fixture already set up
    // in @BeforeEach instead.
    logFee("TOPUP", "25.00", SEEDED_ATT_REFILL_25).andExpect(status().isCreated());

    mockMvc
        .perform(get(feesUrl()).header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.feeType == 'TOPUP')].topupOptionName").value(hasItem("Prepaid Refill 25")));
  }

  private String feesUrl() {
    return "/api/contracts/" + contractId + "/fees";
  }

  /**
   * A proactive Fee, the way the log-Fee dialog sends it. A Topup Fee also names the target SIM
   * Card its auto-created linking Request requires (reboot-and-topup-details ticket) — the seeded
   * AT&amp;T SIM Card, whose Carrier is exactly SEEDED_ATT_REFILL_25's own.
   */
  private ResultActions logFee(String feeType, String amount, UUID topupOptionId) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("feeType", feeType);
    body.put("amount", amount);
    body.put("testerId", testerId);
    if (topupOptionId != null) {
      body.put("topupOptionId", topupOptionId);
    }
    if ("TOPUP".equals(feeType)) {
      body.put("targetSimCardId", simCardOnAtt);
    }
    return postJson(feesUrl(), agentToken, body);
  }

  private void assertNoFeeLogged() throws Exception {
    mockMvc
        .perform(get(feesUrl()).header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  private UUID createCarrier(String country, String name) throws Exception {
    return idOf(
        postJson("/api/carriers", managerToken, Map.of("country", country, "name", name))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private UUID createOption(UUID carrierId, String name, String price) throws Exception {
    return idOf(
        postJson(
                "/api/carriers/" + carrierId + "/topup-options",
                managerToken,
                Map.of("name", name, "price", price))
            .andExpect(status().isCreated())
            .andReturn());
  }

  private UUID idOf(MvcResult result) throws Exception {
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }
}
