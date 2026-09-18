package com.remotesupport.backend.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.support.IntegrationTest;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MvcResult;

/**
 * A SIM Card's Installed-in Smartphone, set/moved/cleared from the Fleet page
 * (sim-installed-in-smartphone ticket: spec.md Solution — Fleet model "Installed in"). Mirrors
 * {@code SmartphoneApiTest}'s serial-control tests for the role matrix and
 * {@code SimCardCarrierApiTest} for shared fixture helpers.
 */
class SimCardInstalledInApiTest extends IntegrationTest {

  private UUID createSmartphone(String managerToken, UUID contractId, String model) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/smartphones")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"model":"%s"}
                        """.formatted(model)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createSimCard(String managerToken, UUID contractId, String number) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/sim-cards")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(postpaidSimCardJson(managerToken, contractId, number, "10.00")))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private void setInstalledIn(String token, UUID contractId, UUID simCardId, UUID smartphoneId, int expectedStatus)
      throws Exception {
    String body = smartphoneId == null ? "{}" : "{\"smartphoneId\":\"" + smartphoneId + "\"}";
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simCardId + "/installed-in")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(body))
        .andExpect(status().is(expectedStatus));
  }

  @Test
  void theContractsAgentOrTheManagerCanInstallASimCardIntoASmartphoneButATesterCannot()
      throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Kessler & Vance LLP");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID smartphoneId = createSmartphone(managerToken, contractId, "iPhone 14");
    UUID simCardId = createSimCard(managerToken, contractId, "+1-555-0111");

    String testerToken =
        createTesterAndLogin(managerToken, clientId, "clara.vance@kesslervance.example", "Passw0rd!23");
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simCardId + "/installed-in")
                .header("Authorization", "Bearer " + testerToken)
                .contentType(APPLICATION_JSON)
                .content("{\"smartphoneId\":\"" + smartphoneId + "\"}"))
        .andExpect(status().isForbidden());

    String agentToken = agentToken();
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simCardId + "/installed-in")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("{\"smartphoneId\":\"" + smartphoneId + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.installedInSmartphoneId").value(smartphoneId.toString()))
        .andExpect(jsonPath("$.installedInSmartphoneModel").value("iPhone 14"));

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simCardId + "/installed-in")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.installedInSmartphoneId").doesNotExist());
  }

  @Test
  void anAgentOnAnotherContractCannotSetInstalledIn() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID agentId = createAgent(managerToken, "Camille Duforet", Country.FRANCE);
    UUID contractId = createContract(managerToken, clientId, agentId);
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, "+33-1-5550100");

    // agentToken() resolves to the seeded Agent, who owns no Contract here.
    setInstalledIn(agentToken(), contractId, simCardId, smartphoneId, 403);
  }

  @Test
  void aSmartphoneCanHoldAtMostTwoSimCardsAndRefusesAThird() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID first = createSimCard(managerToken, contractId, "+1-555-0201");
    UUID second = createSimCard(managerToken, contractId, "+1-555-0202");
    UUID third = createSimCard(managerToken, contractId, "+1-555-0203");

    setInstalledIn(managerToken, contractId, first, smartphoneId, 200);
    setInstalledIn(managerToken, contractId, second, smartphoneId, 200);
    setInstalledIn(managerToken, contractId, third, smartphoneId, 409);
  }

  @Test
  void movingASimCardToAnotherSmartphoneFreesItsOldSlot() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID phoneA = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID phoneB = createSmartphone(managerToken, contractId, "iPhone 15");
    UUID simCardId = createSimCard(managerToken, contractId, "+1-555-0301");

    setInstalledIn(managerToken, contractId, simCardId, phoneA, 200);
    setInstalledIn(managerToken, contractId, simCardId, phoneB, 200);

    // phoneA's freed slot can now take a fresh SIM Card without tripping the two-SIM check.
    UUID another = createSimCard(managerToken, contractId, "+1-555-0302");
    setInstalledIn(managerToken, contractId, another, phoneA, 200);
  }

  @Test
  void aSmartphoneFromAnotherContractIsRefused() throws Exception {
    String managerToken = managerToken();
    UUID clientA = createClient(managerToken, "Bright Path Clinics");
    UUID contractA = createContract(managerToken, clientA, SEEDED_AGENT_ID);
    UUID simCardId = createSimCard(managerToken, contractA, "+1-555-0401");

    UUID clientB = createClient(managerToken, "Solene Cosmetics");
    UUID contractB = createContract(managerToken, clientB, SEEDED_AGENT_ID);
    UUID otherSmartphoneId = createSmartphone(managerToken, contractB, "Pixel 8");

    setInstalledIn(managerToken, contractA, simCardId, otherSmartphoneId, 400);
  }

  @Test
  void aRetiredSmartphoneIsRefusedAsATarget() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, "+1-555-0501");

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/smartphones/" + smartphoneId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"RETIRED"}
                    """))
        .andExpect(status().isOk());

    setInstalledIn(managerToken, contractId, simCardId, smartphoneId, 400);
  }

  @Test
  void aRetiredSimCardCannotBeInstalled() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, "+1-555-0601");

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simCardId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"RETIRED"}
                    """))
        .andExpect(status().isOk());

    setInstalledIn(managerToken, contractId, simCardId, smartphoneId, 400);
  }

  @Test
  void retiringASmartphoneClearsTheLinkOnItsSimCards() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Kessler & Vance LLP");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, "+1-555-0701");
    setInstalledIn(managerToken, contractId, simCardId, smartphoneId, 200);

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/smartphones/" + smartphoneId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"RETIRED"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/sim-cards")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(postpaidSimCardJson(managerToken, contractId, "+1-555-0702", "9.00")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/contracts/" + contractId + "/sim-cards")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(simCardId.toString()))
        .andExpect(jsonPath("$[0].installedInSmartphoneId").doesNotExist());
  }

  @Test
  void retiringASimCardClearsItsOwnLink() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Bright Path Clinics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, "+1-555-0801");
    setInstalledIn(managerToken, contractId, simCardId, smartphoneId, 200);

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simCardId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"RETIRED"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.installedInSmartphoneId").doesNotExist());

    // The freed slot on the Smartphone can now take a fresh SIM Card.
    UUID replacement = createSimCard(managerToken, contractId, "+1-555-0802");
    setInstalledIn(managerToken, contractId, replacement, smartphoneId, 200);
    UUID second = createSimCard(managerToken, contractId, "+1-555-0803");
    setInstalledIn(managerToken, contractId, second, smartphoneId, 200);
  }

  @Test
  void installingASimCardLogsAnAuditEntryAndUninstallingLogsAnother() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Solene Cosmetics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID smartphoneId = createSmartphone(managerToken, contractId, "Pixel 8");
    UUID simCardId = createSimCard(managerToken, contractId, "+1-555-0901");

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      setInstalledIn(managerToken, contractId, simCardId, smartphoneId, 200);
      setInstalledIn(managerToken, contractId, simCardId, null, 200);

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=SIM_CARD_INSTALLED");
      Assertions.assertThat(logged).contains("action=SIM_CARD_UNINSTALLED");
      Assertions.assertThat(logged).contains("entity=SimCard entityId=" + simCardId);
      Assertions.assertThat(logged).contains("smartphoneId=" + smartphoneId);
      Assertions.assertThat(logged).contains("requestId=null");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }
}
