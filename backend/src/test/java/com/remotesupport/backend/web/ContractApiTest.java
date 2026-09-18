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
import com.remotesupport.backend.dto.ContractCreateRequest;
import com.remotesupport.backend.support.IntegrationTest;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Contract creation and listing (manager-entity-setup ticket): a Contract links exactly one
 * Client and one Agent, its currency copied from the Agent's currency at creation time
 * (spec.md Core entities). Neither side is unique: a Client may hold several Contracts and so
 * may an Agent.
 */
class ContractApiTest extends IntegrationTest {

  @Test
  void managerCanCreateAContractWithCurrencyCopiedFromTheAgent() throws Exception {
    String token = managerToken();
    UUID clientId = createClient(token, "Aurora Retail Group");
    UUID agentId = createAgent(token, "Camille Duforet", Country.FRANCE);

    mockMvc
        .perform(
            post("/api/contracts")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ContractCreateRequest(clientId, agentId))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.clientId").value(clientId.toString()))
        .andExpect(jsonPath("$.agentId").value(agentId.toString()))
        .andExpect(jsonPath("$.country").value("FRANCE"))
        .andExpect(jsonPath("$.currency").value("EUR"));

    // +2 for the seeded Demo Client's Contracts (V17, V18) that every Manager-scoped listing
    // includes.
    mockMvc
        .perform(get("/api/contracts").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
  }

  @Test
  void aClientCanHoldMultipleContractsAndAnAgentCanHoldMultipleContracts() throws Exception {
    String token = managerToken();
    UUID client = createClient(token, "Meridian Logistics");
    UUID agentFrance = createAgent(token, "Marta Solano", Country.SPAIN);
    UUID agentMexico = createAgent(token, "Luis Bautista", Country.MEXICO);
    UUID otherClient = createClient(token, "Kessler & Vance LLP");

    // Same Client, two different Agents.
    mockMvc
        .perform(
            post("/api/contracts")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ContractCreateRequest(client, agentFrance))))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/contracts")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ContractCreateRequest(client, agentMexico))))
        .andExpect(status().isCreated());

    // Same Agent (agentFrance), a different Client.
    mockMvc
        .perform(
            post("/api/contracts")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new ContractCreateRequest(otherClient, agentFrance))))
        .andExpect(status().isCreated());

    // +2 for the seeded Demo Client's Contracts (V17, V18) that every Manager-scoped listing
    // includes.
    mockMvc
        .perform(get("/api/contracts").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5));
  }

  @Test
  void creatingAContractWithAnUnknownClientOrAgentReturnsNotFound() throws Exception {
    String token = managerToken();
    UUID agentId = createAgent(token, "Owen Whitfield", Country.UNITED_KINGDOM);
    UUID unknown = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/contracts")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ContractCreateRequest(unknown, agentId))))
        .andExpect(status().isNotFound());

    UUID clientId = createClient(token, "Solene Cosmetics");
    mockMvc
        .perform(
            post("/api/contracts")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ContractCreateRequest(clientId, unknown))))
        .andExpect(status().isNotFound());
  }

  @Test
  void agentAndTesterCannotCreateContracts() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Harbor & Finch Realty");
    UUID agentId = createAgent(managerToken, "Owen Whitfield", Country.UNITED_KINGDOM);

    for (String token : new String[] {agentToken(), testerToken()}) {
      mockMvc
          .perform(
              post("/api/contracts")
                  .header("Authorization", "Bearer " + token)
                  .contentType(APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(new ContractCreateRequest(clientId, agentId))))
          .andExpect(status().isForbidden());
    }
  }

  /**
   * fleet-management ticket: an Agent needs to see their own Contracts to switch between them
   * when viewing Fleet, but never another Agent's (spec.md Access control: "every actor sees
   * only their own Contracts").
   */
  @Test
  void anAgentSeesOnlyTheirOwnContractsWhenListing() throws Exception {
    String managerToken = managerToken();
    UUID ownClient = createClient(managerToken, "Aurora Retail Group");
    UUID otherClient = createClient(managerToken, "Meridian Logistics");
    UUID otherAgent = createAgent(managerToken, "Someone Else", Country.SPAIN);

    createContract(managerToken, ownClient, SEEDED_AGENT_ID);
    createContract(managerToken, otherClient, otherAgent);

    mockMvc
        .perform(get("/api/contracts").header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].clientId").value(ownClient.toString()))
        .andExpect(jsonPath("$[0].agentId").value(SEEDED_AGENT_ID.toString()));
  }

  /** Same as above, for a Tester and their Client's Contracts. */
  @Test
  void aTesterSeesOnlyTheirOwnClientsContractsWhenListing() throws Exception {
    String managerToken = managerToken();
    UUID ownClient = createClient(managerToken, "Kessler & Vance LLP");
    UUID otherClient = createClient(managerToken, "Bright Path Clinics");
    UUID agentId = createAgent(managerToken, "Priya Nair", Country.PHILIPPINES);

    createContract(managerToken, ownClient, agentId);
    createContract(managerToken, otherClient, agentId);

    String testerToken =
        createTesterAndLogin(managerToken, ownClient, "helena.voss@kessler.example", "Passw0rd!23");

    mockMvc
        .perform(get("/api/contracts").header("Authorization", "Bearer " + testerToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].clientId").value(ownClient.toString()));
  }

  @Test
  void anUnlinkedAgentOrTesterSeesNoContracts() throws Exception {
    // The seeded tester@example.com login has no Tester row (no Client link); the seeded
    // agent@example.com resolves to a real Agent (V5) but that Agent holds no Contracts yet.
    mockMvc
        .perform(get("/api/contracts").header("Authorization", "Bearer " + agentToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    mockMvc
        .perform(get("/api/contracts").header("Authorization", "Bearer " + testerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  /**
   * Counterpart to the above: demo.tester@example.com (V17 migration) is the seeded login that
   * *is* linked to a Client/Contract/Fleet, for local manual testing without first creating
   * fixtures through the Manager UI. Regression test for the seed-data gap where the only seeded
   * Tester login was the deliberately-unlinked one above, leaving no way to see a Fleet or submit
   * a Request without first acting as the Manager.
   *
   * <p>The demo Client holds a second Contract (V18) so the Client Portal's ContractSwitcher has
   * something to switch between locally; both show up here, ordered by creation.
   */
  @Test
  void theSeededDemoTesterSeesItsContractFleetAndCanSubmitARequest() throws Exception {
    String demoTesterToken = demoTesterToken();

    mockMvc
        .perform(get("/api/contracts").header("Authorization", "Bearer " + demoTesterToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(SEEDED_DEMO_CONTRACT_ID.toString()))
        .andExpect(jsonPath("$[0].clientId").value(SEEDED_DEMO_CLIENT_ID.toString()))
        .andExpect(jsonPath("$[1].clientId").value(SEEDED_DEMO_CLIENT_ID.toString()));

    mockMvc
        .perform(
            get("/api/contracts/" + SEEDED_DEMO_CONTRACT_ID + "/smartphones")
                .header("Authorization", "Bearer " + demoTesterToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mockMvc
        .perform(
            get("/api/contracts/" + SEEDED_DEMO_CONTRACT_ID + "/sim-cards")
                .header("Authorization", "Bearer " + demoTesterToken))
        .andExpect(status().isOk())
        // V17's Postpaid SIM, plus V27's Postpaid SIM on a seeded Postpaid Plan.
        .andExpect(jsonPath("$.length()").value(2));

    // V17's seeded Smartphone on this Contract (bbbbbbbb-...), Active — reboot-and-topup-details
    // ticket: a Reboot Request now requires naming the Smartphone it reboots.
    mockMvc
        .perform(
            post("/api/contracts/" + SEEDED_DEMO_CONTRACT_ID + "/requests")
                .header("Authorization", "Bearer " + demoTesterToken)
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"type":"REBOOT","targetSmartphoneId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("SUBMITTED"))
        .andExpect(jsonPath("$.raisedByUsername").value(DEMO_TESTER_USERNAME));
  }

  @Test
  void creatingAContractLogsAnAuditEntry() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      String token = managerToken();
      UUID clientId = createClient(token, "Bright Path Clinics");
      UUID agentId = createAgent(token, "Priya Nair", Country.PHILIPPINES);

      mockMvc.perform(
          post("/api/contracts")
              .header("Authorization", "Bearer " + token)
              .contentType(APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(new ContractCreateRequest(clientId, agentId))));

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=CREATE");
      Assertions.assertThat(logged).contains("entity=Contract");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }
}
