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
import com.remotesupport.backend.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Agent creation and listing (manager-entity-setup ticket): an Agent's currency is fixed
 * deterministically by its country, never chosen independently (spec.md: "a person hired in one
 * country (which fixes their currency)"). Creating an Agent also creates its login, all or
 * nothing (agent-login-on-creation spec, create-agent-with-login ticket).
 */
class AgentApiTest extends IntegrationTest {

  private static final String PASSWORD = "Passw0rd!23";

  @Test
  void managerCanCreateAnAgentWithCountryDerivingItsCurrency() throws Exception {
    String token = managerToken();
    AgentCreateRequest request =
        new AgentCreateRequest(
            "Camille Duforet",
            Country.FRANCE,
            new BigDecimal("2400.00"),
            "camille.duforet@agents.example",
            PASSWORD);

    mockMvc
        .perform(
            post("/api/agents")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Camille Duforet"))
        .andExpect(jsonPath("$.country").value("FRANCE"))
        .andExpect(jsonPath("$.currency").value("EUR"))
        .andExpect(jsonPath("$.salaryAmount").value(2400.00))
        .andExpect(jsonPath("$.contractCount").value(0))
        .andExpect(jsonPath("$.loginUsername").value("camille.duforet@agents.example"));

    mockMvc
        .perform(get("/api/agents").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == 'Camille Duforet')].currency").value("EUR"))
        .andExpect(
            jsonPath("$[?(@.name == 'Camille Duforet')].loginUsername")
                .value("camille.duforet@agents.example"));
  }

  @Test
  void aCreatedAgentCanSignInImmediatelyAndResolvesToThatAgent() throws Exception {
    String token = managerToken();
    AgentCreateRequest request =
        new AgentCreateRequest(
            "Ana Lima",
            Country.MEXICO,
            new BigDecimal("1800.00"),
            "ana.lima@agents.example",
            PASSWORD);

    MvcResult created =
        mockMvc
            .perform(
                post("/api/agents")
                    .header("Authorization", "Bearer " + token)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();
    String agentId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    String agentToken = loginAs("ana.lima@agents.example", PASSWORD);

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("AGENT"))
        .andExpect(jsonPath("$.agentId").value(agentId));
  }

  @Test
  void theSeededAgentIsListedWithItsLoginEmail() throws Exception {
    mockMvc
        .perform(get("/api/agents").header("Authorization", "Bearer " + managerToken()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.id == '" + SEEDED_AGENT_ID + "')].loginUsername")
                .value(AGENT_USERNAME));
  }

  @Test
  void creatingAnAgentWithoutAUsernameIsRejectedAndCreatesNoAgent() throws Exception {
    String token = managerToken();
    Map<String, Object> body = agentBody("No Username Agent", "unused@agents.example");
    body.remove("username");

    postAgent(token, body).andExpect(status().isBadRequest());

    assertNoAgentNamed(token, "No Username Agent");
  }

  @Test
  void creatingAnAgentWithoutAPasswordIsRejectedAndCreatesNoAgent() throws Exception {
    String token = managerToken();
    Map<String, Object> body = agentBody("No Password Agent", "nopassword@agents.example");
    body.remove("password");

    postAgent(token, body).andExpect(status().isBadRequest());

    assertNoAgentNamed(token, "No Password Agent");
  }

  @Test
  void creatingAnAgentWithAUsernameAlreadyInUseIsRejectedAndCreatesNoAgent() throws Exception {
    String token = managerToken();
    postAgent(token, agentBody("First Holder", "taken@agents.example"))
        .andExpect(status().isCreated());

    postAgent(token, agentBody("Second Holder", "taken@agents.example"))
        .andExpect(status().isConflict());

    assertNoAgentNamed(token, "Second Holder");
  }

  @Test
  void creatingAnAgentWithAnotherUsersUsernameIsRejectedAndCreatesNoAgent() throws Exception {
    String token = managerToken();

    postAgent(token, agentBody("Manager Name Clash", MANAGER_USERNAME))
        .andExpect(status().isConflict());

    assertNoAgentNamed(token, "Manager Name Clash");
  }

  @Test
  void differentCountriesDeriveDifferentCurrencies() throws Exception {
    String token = managerToken();
    AgentCreateRequest mexican =
        new AgentCreateRequest(
            "Luis Bautista",
            Country.MEXICO,
            new BigDecimal("1800.00"),
            "luis.bautista@agents.example",
            PASSWORD);
    AgentCreateRequest filipino =
        new AgentCreateRequest(
            "Priya Nair",
            Country.PHILIPPINES,
            new BigDecimal("1450.00"),
            "priya.nair@agents.example",
            PASSWORD);

    mockMvc
        .perform(
            post("/api/agents")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mexican)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currency").value("MXN"));

    mockMvc
        .perform(
            post("/api/agents")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(filipino)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.currency").value("PHP"));
  }

  @Test
  void creatingAnAgentRejectsANegativeSalary() throws Exception {
    String token = managerToken();
    AgentCreateRequest request =
        new AgentCreateRequest(
            "Negative Salary",
            Country.UNITED_STATES,
            new BigDecimal("-1.00"),
            "negative.salary@agents.example",
            PASSWORD);

    mockMvc
        .perform(
            post("/api/agents")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void agentAndTesterCannotCreateOrListAgents() throws Exception {
    AgentCreateRequest request =
        new AgentCreateRequest(
            "Rejected",
            Country.UNITED_KINGDOM,
            new BigDecimal("100.00"),
            "rejected@agents.example",
            PASSWORD);

    for (String token : new String[] {agentToken(), testerToken()}) {
      mockMvc
          .perform(
              post("/api/agents")
                  .header("Authorization", "Bearer " + token)
                  .contentType(APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isForbidden());

      mockMvc
          .perform(get("/api/agents").header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void creatingAnAgentLogsAnAuditEntryForTheAgentAndForItsLogin() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      String token = managerToken();
      postAgent(token, agentBody("Owen Whitfield", "owen.whitfield@agents.example"))
          .andExpect(status().isCreated());

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=CREATE");
      Assertions.assertThat(logged).contains("entity=Agent");
      Assertions.assertThat(logged).contains("action=AGENT_LOGIN_CREATED");
      Assertions.assertThat(logged).doesNotContain(PASSWORD);
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  private Map<String, Object> agentBody(String name, String username) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("country", "FRANCE");
    body.put("salaryAmount", 2000);
    body.put("username", username);
    body.put("password", PASSWORD);
    return body;
  }

  private ResultActions postAgent(String token, Map<String, Object> body) throws Exception {
    return mockMvc.perform(
        post("/api/agents")
            .header("Authorization", "Bearer " + token)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
  }

  private void assertNoAgentNamed(String token, String name) throws Exception {
    mockMvc
        .perform(get("/api/agents").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == '" + name + "')]").isEmpty());
  }
}
