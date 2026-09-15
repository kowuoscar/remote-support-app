package com.remotesupport.backend.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Request submission and visibility (tester-request-submission ticket: spec.md Solution's
 * Request entity, and user stories 16, 30-31). Mirrors {@link SmartphoneApiTest}'s pattern:
 * Requests are nested under a Contract exactly like Fleet, so "a Tester sees every Request raised
 * by anyone at their Client across every Contract" is exercised as one GET per Contract (the same
 * shape the frontend uses), not a separate Client-wide endpoint.
 */
class RequestApiTest extends IntegrationTest {

  private UUID submitRequest(String testerToken, UUID contractId, String type) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/contracts/" + contractId + "/requests")
                    .header("Authorization", "Bearer " + testerToken)
                    .contentType(APPLICATION_JSON)
                    .content("""
                        {"type":"%s"}
                        """.formatted(type)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"REBOOT", "TOPUP", "SIM_SWAP", "PROVISION_SMARTPHONE", "PROVISION_SIM", "REPAIR"})
  void testerCanSubmitEachRequestTypeAndItStartsSubmitted(String type) throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "priya.raman@aurora.example", "Passw0rd!23");

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + testerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"type":"%s"}
                    """.formatted(type)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value(type))
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.contractId").value(contractId.toString()))
        .andExpect(jsonPath("$.raisedByUsername").value("priya.raman@aurora.example"));
  }

  @Test
  void anyTesterAtTheSameClientSeesEveryRequestRaisedByAnyoneAtThatClient() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    String firstTesterToken =
        createTesterAndLogin(managerToken, clientId, "owen.reyes@meridian.example", "Passw0rd!23");
    submitRequest(firstTesterToken, contractId, "REBOOT");

    String secondTesterToken =
        createTesterAndLogin(managerToken, clientId, "lena.frost@meridian.example", "Passw0rd!23");
    submitRequest(secondTesterToken, contractId, "TOPUP");

    // The second Tester sees both Requests — their own and the first Tester's — not just their own.
    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + secondTesterToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].raisedByUsername").value("owen.reyes@meridian.example"))
        .andExpect(jsonPath("$[1].raisedByUsername").value("lena.frost@meridian.example"));
  }

  @Test
  void anyTesterAtTheSameClientSeesRequestsAcrossAllOfThatClientsContracts() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Kessler & Vance LLP");
    UUID firstContract = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    UUID otherAgentId = createAgent(managerToken, "Priya Nair", Country.PHILIPPINES);
    UUID secondContract = createContract(managerToken, clientId, otherAgentId);

    String testerToken =
        createTesterAndLogin(managerToken, clientId, "helena.voss@kessler.example", "Passw0rd!23");
    submitRequest(testerToken, firstContract, "REBOOT");
    submitRequest(testerToken, secondContract, "REPAIR");

    mockMvc
        .perform(
            get("/api/contracts/" + firstContract + "/requests")
                .header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].type").value("REBOOT"));

    mockMvc
        .perform(
            get("/api/contracts/" + secondContract + "/requests")
                .header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].type").value("REPAIR"));
  }

  @Test
  void agentSeesIncomingRequestsForTheirOwnContractsFilteredByContract() throws Exception {
    String managerToken = managerToken();
    UUID ownClient = createClient(managerToken, "Bright Path Clinics");
    UUID ownContract = createContract(managerToken, ownClient, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, ownClient, "marco.diaz@brightpath.example", "Passw0rd!23");
    submitRequest(testerToken, ownContract, "TOPUP");

    UUID otherClient = createClient(managerToken, "Solene Cosmetics");
    UUID otherAgentId = createAgent(managerToken, "Priya Nair", Country.PHILIPPINES);
    UUID otherContract = createContract(managerToken, otherClient, otherAgentId);
    String otherTesterToken =
        createTesterAndLogin(managerToken, otherClient, "elise.fabron@solene.example", "Passw0rd!23");
    submitRequest(otherTesterToken, otherContract, "REBOOT");

    String agentToken = agentToken();

    mockMvc
        .perform(
            get("/api/contracts/" + ownContract + "/requests")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].type").value("TOPUP"));

    // The Agent cannot see Requests on a Contract that isn't theirs.
    mockMvc
        .perform(
            get("/api/contracts/" + otherContract + "/requests")
                .header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerSeesRequestsOnAnyContractInTheTenant() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "charlotte.finch@harborfinch.example", "Passw0rd!23");
    submitRequest(testerToken, contractId, "SIM_SWAP");

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + managerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].type").value("SIM_SWAP"));
  }

  @Test
  void aTesterCannotSubmitARequestAgainstAnotherClientsContract() throws Exception {
    String managerToken = managerToken();
    UUID ownClient = createClient(managerToken, "Solene Cosmetics");
    createTesterAndLogin(managerToken, ownClient, "irrelevant@solene.example", "Passw0rd!23");

    UUID otherClient = createClient(managerToken, "Meridian Logistics");
    UUID otherContract = createContract(managerToken, otherClient, SEEDED_AGENT_ID);

    String testerToken =
        createTesterAndLogin(managerToken, ownClient, "elise.fabron@solene.example", "Passw0rd!23");

    mockMvc
        .perform(
            post("/api/contracts/" + otherContract + "/requests")
                .header("Authorization", "Bearer " + testerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"type":"REBOOT"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void anAgentCannotSubmitARequest() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Aurora Retail Group");
    UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);

    mockMvc
        .perform(
            post("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + agentToken())
                .contentType(APPLICATION_JSON)
                .content("""
                    {"type":"REBOOT"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void submittingARequestAgainstAnUnknownContractReturnsNotFound() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Meridian Logistics");
    String testerToken =
        createTesterAndLogin(managerToken, clientId, "owen.reyes@meridian.example", "Passw0rd!23");

    mockMvc
        .perform(
            post("/api/contracts/" + UUID.randomUUID() + "/requests")
                .header("Authorization", "Bearer " + testerToken)
                .contentType(APPLICATION_JSON)
                .content("""
                    {"type":"REBOOT"}
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void submittingARequestLogsAnAuditEntryWithContractTypeAndActor() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      String managerToken = managerToken();
      UUID clientId = createClient(managerToken, "Bright Path Clinics");
      UUID contractId = createContract(managerToken, clientId, SEEDED_AGENT_ID);
      String testerToken =
          createTesterAndLogin(managerToken, clientId, "marco.diaz@brightpath.example", "Passw0rd!23");

      submitRequest(testerToken, contractId, "PROVISION_SIM");

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=REQUEST_SUBMITTED");
      Assertions.assertThat(logged).contains("entity=Request");
      Assertions.assertThat(logged).contains("contractId=" + contractId);
      Assertions.assertThat(logged).contains("requestType=PROVISION_SIM");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }
}
