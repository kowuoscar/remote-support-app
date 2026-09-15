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
import com.remotesupport.backend.dto.AgentCreateRequest;
import com.remotesupport.backend.dto.ClientCreateRequest;
import com.remotesupport.backend.dto.ContractCreateRequest;
import com.remotesupport.backend.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Contract creation and listing (manager-entity-setup ticket): a Contract links exactly one
 * Client and one Agent, its currency copied from the Agent's currency at creation time
 * (spec.md Core entities). Neither side is unique: a Client may hold several Contracts and so
 * may an Agent.
 */
class ContractApiTest extends IntegrationTest {

  private UUID createClient(String token, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/clients")
                    .header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new ClientCreateRequest(name))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

  private UUID createAgent(String token, String name, Country country) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/agents")
                    .header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new AgentCreateRequest(name, country, new BigDecimal("2000.00")))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
  }

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

    mockMvc
        .perform(get("/api/contracts").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
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

    mockMvc
        .perform(get("/api/contracts").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
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
  void agentAndTesterCannotCreateOrListContracts() throws Exception {
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

      mockMvc
          .perform(get("/api/contracts").header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
    }
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
