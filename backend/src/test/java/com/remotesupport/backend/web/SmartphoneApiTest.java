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
 * Smartphone create/list/status-transition on a Contract's Fleet (fleet-management ticket:
 * spec.md Solution — "Smartphone: status Active -> In Repair -> Active, or Retired when
 * replaced"). Manager-only create; view/status-change scoped to the Contract's own Agent/Client,
 * mirroring spec.md Access control.
 */
class SmartphoneApiTest extends IntegrationTest {

  private UUID createSmartphone(String managerToken, UUID contractId, String model, String serial)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/smartphones")
                    .header("Authorization", "Bearer " + managerToken)
                    .contentType(APPLICATION_JSON)
                    .content(
                        """
                        {"model":"%s","serial":"%s"}
                        """
                            .formatted(model, serial)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  @Test
  void managerCanAddASmartphoneToAContractsFleetAndSeeItListed() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID agentId = createAgent(managerToken, "Camille Duforet", Country.FRANCE);
    UUID contractId = createContract(managerToken, clientId, agentId);

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/smartphones")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"model":"iPhone 14","serial":"SN-12345","assignedTo":"Front desk"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.model").value("iPhone 14"))
        .andExpect(jsonPath("$.serial").value("SN-12345"))
        .andExpect(jsonPath("$.assignedTo").value("Front desk"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/smartphones")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].model").value("iPhone 14"));
  }

  @Test
  void addingASmartphoneToAnUnknownContractReturnsNotFound() throws Exception {
    String managerToken = managerToken();

    mockMvc
        .perform(
            post("/api/contracts/" + UUID.randomUUID() + "/smartphones")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"model":"Pixel 8","serial":"SN-999"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void agentAndTesterCannotAddASmartphone() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    for (String token : new String[] {agentToken(), testerToken()}) {
      mockMvc
          .perform(
              post("/api/contracts/" + contractId + "/smartphones")
                  .header("Authorization", "Bearer " + token)
                  .contentType(APPLICATION_JSON)
                  .content(
                      """
                      {"model":"Pixel 8","serial":"SN-777"}
                      """))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void anAgentCanViewAndChangeStatusOnlyOnTheirOwnContract() throws Exception {
    String managerToken = managerToken();
    UUID ownClient = createClient(managerToken, "Kessler & Vance LLP");
    UUID ownContract = createContract(managerToken, ownClient, SEEDED_AGENT_ID);
    UUID smartphoneId = createSmartphone(managerToken, ownContract, "iPhone 14", "SN-111");

    UUID otherClient = createClient(managerToken, "Bright Path Clinics");
    UUID otherAgent = createAgent(managerToken, "Priya Nair", Country.PHILIPPINES);
    UUID otherContract = createContract(managerToken, otherClient, otherAgent);
    UUID otherSmartphoneId = createSmartphone(managerToken, otherContract, "Pixel 8", "SN-222");

    String agentToken = agentToken();

    mockMvc
        .perform(
            get("/api/contracts/" + ownContract + "/smartphones")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mockMvc
        .perform(
            patch("/api/contracts/" + ownContract + "/smartphones/" + smartphoneId + "/status")
                .header("Authorization", "Bearer " + agentToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"IN_REPAIR"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_REPAIR"));

    mockMvc
        .perform(
            get("/api/contracts/" + otherContract + "/smartphones")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            patch("/api/contracts/" + otherContract + "/smartphones/" + otherSmartphoneId + "/status")
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
    UUID ownClient = createClient(managerToken, "Harbor & Finch Realty");
    UUID agentId = createAgent(managerToken, "Owen Whitfield", Country.UNITED_KINGDOM);
    UUID ownContract = createContract(managerToken, ownClient, agentId);
    UUID smartphoneId = createSmartphone(managerToken, ownContract, "iPhone 14", "SN-333");

    UUID otherClient = createClient(managerToken, "Solene Cosmetics");
    UUID otherContract = createContract(managerToken, otherClient, agentId);

    String testerToken =
        createTesterAndLogin(managerToken, ownClient, "charlotte.finch@harborfinch.example", "Passw0rd!23");

    mockMvc
        .perform(
            get("/api/contracts/" + ownContract + "/smartphones")
                .header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mockMvc
        .perform(
            patch("/api/contracts/" + ownContract + "/smartphones/" + smartphoneId + "/status")
                .header("Authorization", "Bearer " + testerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"IN_REPAIR"}
                    """))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/contracts/" + otherContract + "/smartphones")
                .header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void statusFollowsActiveInRepairActiveOrRetiredAndRejectsInvalidTransitions() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID smartphoneId = createSmartphone(managerToken, contractId, "iPhone 14", "SN-444");

    // ACTIVE -> IN_REPAIR -> ACTIVE
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/smartphones/" + smartphoneId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"IN_REPAIR"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_REPAIR"));

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/smartphones/" + smartphoneId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"ACTIVE"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    // ACTIVE -> RETIRED
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/smartphones/" + smartphoneId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"RETIRED"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RETIRED"));

    // RETIRED is terminal.
    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/smartphones/" + smartphoneId + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"ACTIVE"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void changingStatusOfAnUnknownSmartphoneReturnsNotFound() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    mockMvc
        .perform(
            patch("/api/contracts/" + contractId + "/smartphones/" + UUID.randomUUID() + "/status")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"status":"RETIRED"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void addingASmartphoneLogsAnAuditEntry() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      String managerToken = managerToken();
      UUID clientId = createClient(managerToken, "Bright Path Clinics");
      UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
      createSmartphone(managerToken, contractId, "iPhone 14", "SN-555");

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=CREATE");
      Assertions.assertThat(logged).contains("entity=Smartphone");
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
      UUID clientId = createClient(managerToken, "Solene Cosmetics");
      UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
      UUID smartphoneId = createSmartphone(managerToken, contractId, "iPhone 14", "SN-666");

      mockMvc.perform(
          patch("/api/contracts/" + contractId + "/smartphones/" + smartphoneId + "/status")
              .header("Authorization", "Bearer " + managerToken)
              .contentType(APPLICATION_JSON)
              .content("""
                  {"status":"IN_REPAIR"}
                  """));

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=STATUS_CHANGE");
      Assertions.assertThat(logged).contains("entity=Smartphone");
      Assertions.assertThat(logged).contains("oldStatus=ACTIVE");
      Assertions.assertThat(logged).contains("newStatus=IN_REPAIR");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }
}
