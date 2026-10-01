package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.dto.AgentCreatedResponse;
import com.remotesupport.backend.dto.TesterCreatedResponse;
import com.remotesupport.backend.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A Login created without a typed password gets a generated one, returned once; a typed one is
 * still honoured (transitional, until creation-takes-no-typed-password).
 */
class GeneratedPasswordCreationApiTest extends IntegrationTest {

  private static final String FORMAT = "^[a-km-np-z2-9]{4}(-[a-km-np-z2-9]{4}){2}$";
  private static final String TYPED = "Passw0rd!23";

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void creatingAnAgentWithoutAPasswordReturnsAGeneratedOneThatSignsIn() throws Exception {
    String token = managerToken();
    MvcResult created =
        agentCreation(token, "gen.agent@agents.example", null)
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.password").value(matchesPattern(FORMAT)))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.name").value("Gen Agent"))
            .andExpect(jsonPath("$.country").value("FRANCE"))
            .andExpect(jsonPath("$.currency").value("EUR"))
            .andExpect(jsonPath("$.contractCount").value(0))
            .andExpect(jsonPath("$.loginUsername").value("gen.agent@agents.example"))
            .andReturn();

    assertThat(loginAs("gen.agent@agents.example", passwordOf(created))).isNotBlank();
  }

  @Test
  void givingALoginLessAgentItsLoginWithoutAPasswordReturnsAGeneratedOne() throws Exception {
    String token = managerToken();
    UUID agentId = insertLoginLessAgent("Gen Login");

    MvcResult created =
        postJson(
                "/api/agents/" + agentId + "/login",
                token,
                loginBody("gen.login@agents.example", null))
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.password").value(matchesPattern(FORMAT)))
            .andExpect(jsonPath("$.id").value(agentId.toString()))
            .andExpect(jsonPath("$.loginUsername").value("gen.login@agents.example"))
            .andReturn();

    assertThat(loginAs("gen.login@agents.example", passwordOf(created))).isNotBlank();
  }

  @Test
  void creatingATesterWithoutAPasswordReturnsAGeneratedOneThatSignsIn() throws Exception {
    String token = managerToken();
    UUID clientId = createClient(token, "Gen Client");

    MvcResult created =
        testerCreation(token, clientId, "gen.tester@client.example", null)
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.password").value(matchesPattern(FORMAT)))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.clientId").value(clientId.toString()))
            .andExpect(jsonPath("$.username").value("gen.tester@client.example"))
            .andExpect(jsonPath("$.isPrimaryContact").value(false))
            .andReturn();

    assertThat(loginAs("gen.tester@client.example", passwordOf(created))).isNotBlank();
  }

  @Test
  void aTypedPasswordIsStillHonouredAndNotEchoedOnAgentCreation() throws Exception {
    String token = managerToken();
    agentCreation(token, "typed.agent@agents.example", TYPED)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.password").doesNotExist());
    assertThat(loginAs("typed.agent@agents.example", TYPED)).isNotBlank();
  }

  @Test
  void aTypedPasswordIsStillHonouredAndNotEchoedOnGiveLogin() throws Exception {
    String token = managerToken();
    UUID agentId = insertLoginLessAgent("Typed Login");
    postJson(
            "/api/agents/" + agentId + "/login",
            token,
            loginBody("typed.login@agents.example", TYPED))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.password").doesNotExist());
    assertThat(loginAs("typed.login@agents.example", TYPED)).isNotBlank();
  }

  @Test
  void aTypedPasswordIsStillHonouredAndNotEchoedOnTesterCreation() throws Exception {
    String token = managerToken();
    UUID clientId = createClient(token, "Typed Client");
    testerCreation(token, clientId, "typed.tester@client.example", TYPED)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.password").doesNotExist());
    assertThat(loginAs("typed.tester@client.example", TYPED)).isNotBlank();
  }

  @Test
  void aTypedPasswordShorterThanTheMinimumIsStillRejectedOnEveryRoute() throws Exception {
    String token = managerToken();
    UUID agentId = insertLoginLessAgent("Short Login");
    UUID clientId = createClient(token, "Short Client");

    agentCreation(token, "short.agent@agents.example", "short").andExpect(status().isBadRequest());
    postJson(
            "/api/agents/" + agentId + "/login",
            token,
            loginBody("short.login@agents.example", "short"))
        .andExpect(status().isBadRequest());
    testerCreation(token, clientId, "short.tester@client.example", "short")
        .andExpect(status().isBadRequest());
  }

  @Test
  void listEndpointsCarryNoPasswordForJustCreatedLogins() throws Exception {
    String token = managerToken();
    UUID clientId = createClient(token, "List Client");
    agentCreation(token, "list.agent@agents.example", null).andExpect(status().isCreated());
    testerCreation(token, clientId, "list.tester@client.example", null)
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/agents").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.loginUsername == 'list.agent@agents.example')]").isNotEmpty())
        .andExpect(jsonPath("$[*].password").isEmpty());
    mockMvc
        .perform(
            get("/api/clients/" + clientId + "/testers").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].username").value("list.tester@client.example"))
        .andExpect(jsonPath("$[*].password").isEmpty());
  }

  @Test
  void noCreationRouteLogsTheReturnedPassword() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.addAppender(appender);
    try {
      String token = managerToken();
      UUID clientId = createClient(token, "Logged Client");
      UUID agentId = insertLoginLessAgent("Logged Login");

      String agentPassword =
          passwordOf(agentCreation(token, "logged.agent@agents.example", null).andReturn());
      String loginPassword =
          passwordOf(
              postJson(
                      "/api/agents/" + agentId + "/login",
                      token,
                      loginBody("logged.login@agents.example", null))
                  .andReturn());
      String testerPassword =
          passwordOf(
              testerCreation(token, clientId, "logged.tester@client.example", null).andReturn());

      String logged =
          appender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .reduce("", (a, b) -> a + "\n" + b);
      assertThat(logged).contains("action=AGENT_LOGIN_CREATED");
      assertThat(logged).contains("action=CREATE entity=Tester");
      assertThat(logged)
          .doesNotContain(agentPassword)
          .doesNotContain(loginPassword)
          .doesNotContain(testerPassword);
    } finally {
      root.detachAppender(appender);
    }
  }

  @Test
  void theCreationRecordsNeverPrintThePassword() {
    String secret = "abcd-efgh-jkmn";
    assertThat(
            new AgentCreatedResponse(
                    UUID.randomUUID(), "n", "FRANCE", "EUR", BigDecimal.ONE, 0, "u", secret)
                .toString())
        .doesNotContain(secret);
    assertThat(
            new TesterCreatedResponse(UUID.randomUUID(), UUID.randomUUID(), "u", false, secret)
                .toString())
        .doesNotContain(secret);
  }

  private String passwordOf(MvcResult result) throws Exception {
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("password")
        .asText();
  }

  private ResultActions agentCreation(String token, String username, String password)
      throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", "Gen Agent");
    body.put("country", Country.FRANCE);
    body.put("salaryAmount", new BigDecimal("2000.00"));
    body.put("username", username);
    if (password != null) {
      body.put("password", password);
    }
    return postJson("/api/agents", token, body);
  }

  private ResultActions testerCreation(
      String token, UUID clientId, String username, String password) throws Exception {
    Map<String, Object> body = loginBody(username, password);
    body.put("isPrimaryContact", false);
    return postJson("/api/clients/" + clientId + "/testers", token, body);
  }

  private Map<String, Object> loginBody(String username, String password) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("username", username);
    if (password != null) {
      body.put("password", password);
    }
    return body;
  }

  private UUID insertLoginLessAgent(String name) {
    UUID tenantId =
        jdbcTemplate.queryForObject(
            "SELECT tenant_id FROM users WHERE username = ?", UUID.class, MANAGER_USERNAME);
    UUID agentId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO agents (id, tenant_id, name, country, currency, salary_amount)"
            + " VALUES (?, ?, ?, 'FRANCE', 'EUR', 2000.00)",
        agentId,
        tenantId,
        name);
    return agentId;
  }
}
