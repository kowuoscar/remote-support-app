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
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.support.IntegrationTest;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MvcResult;

/**
 * SIM Card create/list/status-transition on a Contract's Fleet (fleet-management ticket: spec.md
 * Solution — "SIM Card: flavor Postpaid (carries a fixed monthly fee...) or Prepaid (no monthly
 * fee). Status Active or Retired"). Manager-only create; view/status-change scoped to the
 * Contract's own Agent/Client, mirroring SmartphoneApiTest.
 */
class SimCardApiTest extends IntegrationTest {

  private UUID createPostpaidSim(String managerToken, UUID contractId, String number, String fee)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/sim-cards")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"number":"%s","flavor":"POSTPAID","monthlyFeeAmount":%s}
                        """
                            .formatted(number, fee)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  @Test
  void managerCanAddAPostpaidSimCardToAContractsFleetAndSeeItListed() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/sim-cards")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"number":"+1-555-0100","carrier":"Verizon","flavor":"POSTPAID","monthlyFeeAmount":25.00}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.number").value("+1-555-0100"))
        .andExpect(jsonPath("$.carrier").value("Verizon"))
        .andExpect(jsonPath("$.flavor").value("POSTPAID"))
        .andExpect(jsonPath("$.monthlyFeeAmount").value(25.00))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/sim-cards")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].number").value("+1-555-0100"));
  }

  @Test
  void managerCanAddAPrepaidSimCardWithNoMonthlyFee() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/sim-cards")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"number":"+1-555-0200","flavor":"PREPAID"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.flavor").value("PREPAID"))
        .andExpect(jsonPath("$.monthlyFeeAmount").doesNotExist());
  }

  @Test
  void aPostpaidSimCardRequiresAMonthlyFeeAndAPrepaidOneMustNotHaveOne() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Kessler & Vance LLP");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/sim-cards")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"number":"+1-555-0300","flavor":"POSTPAID"}
                    """))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/sim-cards")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"number":"+1-555-0400","flavor":"PREPAID","monthlyFeeAmount":10.00}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void agentAndTesterCannotAddASimCard() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    for (String token : new String[] {agentToken(), testerToken()}) {
      mockMvc
          .perform(
              post("/api/contracts/" + contractId + "/sim-cards")
                  .header("Authorization", "Bearer " + token)
                  .contentType(APPLICATION_JSON)
                  .content(
                      """
                      {"number":"+1-555-0500","flavor":"PREPAID"}
                      """))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void anAgentCanViewAndChangeStatusOnlyOnTheirOwnContract() throws Exception {
    String managerToken = managerToken();
    UUID ownClient = createClient(managerToken, "Bright Path Clinics");
    UUID ownContract = createContract(managerToken, ownClient, SEEDED_AGENT_ID);
    UUID simId = createPostpaidSim(managerToken, ownContract, "+63-2-5550100", "12.50");

    UUID otherClient = createClient(managerToken, "Solene Cosmetics");
    UUID otherAgent = createAgent(managerToken, "Camille Duforet", Country.FRANCE);
    UUID otherContract = createContract(managerToken, otherClient, otherAgent);
    UUID otherSimId = createPostpaidSim(managerToken, otherContract, "+33-1-5550100", "9.00");

    String agentToken = agentToken();

    mockMvc
        .perform(
            get("/api/contracts/" + ownContract + "/sim-cards").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mockMvc
        .perform(
            patch("/api/contracts/" + ownContract + "/sim-cards/" + simId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"RETIRED"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RETIRED"));

    mockMvc
        .perform(
            get("/api/contracts/" + otherContract + "/sim-cards")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            patch("/api/contracts/" + otherContract + "/sim-cards/" + otherSimId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"RETIRED"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void aTesterCanViewButNotChangeStatusOnTheirOwnClientsContractOnly() throws Exception {
    String managerToken = managerToken();
    UUID ownClient = createClient(managerToken, "Aurora Retail Group");
    UUID agentId = createAgent(managerToken, "Owen Whitfield", Country.UNITED_KINGDOM);
    UUID ownContract = createContract(managerToken, ownClient, agentId);
    UUID simId = createPostpaidSim(managerToken, ownContract, "+44-20-55501", "18.00");

    UUID otherClient = createClient(managerToken, "Meridian Logistics");
    UUID otherContract = createContract(managerToken, otherClient, agentId);

    String testerToken =
        createTesterAndLogin(managerToken, ownClient, "nadia.okafor@aurora.example", "Passw0rd!23");

    mockMvc
        .perform(
            get("/api/contracts/" + ownContract + "/sim-cards")
                .header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mockMvc
        .perform(
            patch("/api/contracts/" + ownContract + "/sim-cards/" + simId + "/status")
                .header("Authorization", "Bearer " + testerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"RETIRED"}
                    """))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/contracts/" + otherContract + "/sim-cards")
                .header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void statusTogglesBetweenActiveAndRetiredAndRejectsANoOpTransition() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Kessler & Vance LLP");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID simId = createPostpaidSim(managerToken, contractId, "+34-91-5550100", "8.50");

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"RETIRED"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RETIRED"));

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"ACTIVE"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/sim-cards/" + simId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"ACTIVE"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void addingASimCardLogsAnAuditEntry() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      String managerToken = managerToken();
      UUID clientId = createClient(managerToken, "Solene Cosmetics");
      UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
      createPostpaidSim(managerToken, contractId, "+33-1-5550999", "11.00");

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=CREATE");
      Assertions.assertThat(logged).contains("entity=SimCard");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  @Test
  void changingStatusLogsAnAuditEntryWithOldAndNewStatus() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      String managerToken = managerToken();
      UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
      UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
      UUID simId = createPostpaidSim(managerToken, contractId, "+44-20-5550999", "14.00");

      mockMvc.perform(
          patch("/api/contracts/" + contractId + "/sim-cards/" + simId + "/status")
              .header("Authorization", "Bearer " + managerToken)
              .contentType(APPLICATION_JSON)
              .content("""
                  {"status":"RETIRED"}
                  """));

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=STATUS_CHANGE");
      Assertions.assertThat(logged).contains("entity=SimCard");
      Assertions.assertThat(logged).contains("oldStatus=ACTIVE");
      Assertions.assertThat(logged).contains("newStatus=RETIRED");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }
}
