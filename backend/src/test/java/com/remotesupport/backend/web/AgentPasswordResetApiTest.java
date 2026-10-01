package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import com.remotesupport.backend.support.OtherTenantFixture.OtherTenantAgentLogin;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A Manager resets an Agent's password (manager-resets-a-password spec, "Reset endpoints"). Each
 * test creates its own Agent through the API with no password and never touches a seeded Login;
 * request bodies are JSON maps so these tests survive the creation contract step.
 */
@Import(OtherTenantFixture.class)
class AgentPasswordResetApiTest extends IntegrationTest {

  private static final String FORMAT = "^[a-km-np-z2-9]{4}(-[a-km-np-z2-9]{4}){2}$";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private OtherTenantFixture otherTenantFixture;

  @Test
  void resetAnswersANewGeneratedPasswordThatSignsInAndTheOldOneNoLonger() throws Exception {
    String token = managerToken();
    CreatedAgent agent = createAgentWithoutPassword(token, "Reset Me");

    MvcResult reset =
        reset(token, agent.id())
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.password").value(matchesPattern(FORMAT)))
            .andReturn();
    String newPassword = passwordOf(reset);

    assertThat(newPassword).isNotEqualTo(agent.password());
    assertThat(loginAs(agent.username(), newPassword)).isNotBlank();
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(loginJson(agent.username(), agent.password())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void anAgentWithNoLoginIsRefusedWithACodedConflict() throws Exception {
    UUID agentId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO agents (id, tenant_id, name, country, currency, salary_amount)"
            + " VALUES (?, ?, 'No Login', 'FRANCE', 'EUR', 2000.00)",
        agentId,
        SEEDED_TENANT_ID);

    reset(managerToken(), agentId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AGENT_HAS_NO_LOGIN"));
  }

  @Test
  void anUnknownAgentIsNotFound() throws Exception {
    reset(managerToken(), UUID.randomUUID()).andExpect(status().isNotFound());
  }

  @Test
  void anAgentInAnotherTenantIsNotFoundAndKeepsItsOriginalPassword() throws Exception {
    OtherTenantAgentLogin other =
        otherTenantFixture.agentLoginInAnotherTenant(
            "other-tenant-agent-" + UUID.randomUUID() + "@example.com", "Original#Passw0rd1");

    reset(managerToken(), other.agentId()).andExpect(status().isNotFound());

    assertThat(loginAs(other.username(), other.password())).isNotBlank();
  }

  @Test
  void agentAndTesterAreRefusedAndTheOldPasswordStillWorks() throws Exception {
    String token = managerToken();
    CreatedAgent agent = createAgentWithoutPassword(token, "Not For Them");

    for (String refused : new String[] {agentToken(), testerToken()}) {
      reset(refused, agent.id()).andExpect(status().isForbidden());
    }

    assertThat(loginAs(agent.username(), agent.password())).isNotBlank();
  }

  @Test
  void aResetWritesOneAuditLineWithTheIdsAndNeverThePasswordOrHash() throws Exception {
    String token = managerToken();
    CreatedAgent agent = createAgentWithoutPassword(token, "Audited Reset");
    UUID agentUserId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE agent_id = ?", UUID.class, agent.id());
    UUID managerUserId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = ?", UUID.class, MANAGER_USERNAME);

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);
    try {
      String newPassword = passwordOf(reset(token, agent.id()).andReturn());
      String hash =
          jdbcTemplate.queryForObject(
              "SELECT password_hash FROM users WHERE id = ?", String.class, agentUserId);

      String logged = logged(appender);
      assertThat(logged).contains("action=PASSWORD_CHANGED entity=User entityId=" + agentUserId);
      assertThat(logged).contains("actorUserId=" + managerUserId);
      assertThat(logged).contains("tenantId=" + SEEDED_TENANT_ID);
      assertThat(count(appender, "action=PASSWORD_CHANGED")).isEqualTo(1);
      assertThat(logged).doesNotContain(newPassword).doesNotContain(hash);
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  @Test
  void refusedResetsWriteNoAuditLine() throws Exception {
    String token = managerToken();
    CreatedAgent agent = createAgentWithoutPassword(token, "Refused Reset");
    OtherTenantAgentLogin other =
        otherTenantFixture.agentLoginInAnotherTenant(
            "refused-" + UUID.randomUUID() + "@example.com", "Original#Passw0rd1");

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);
    try {
      reset(agentToken(), agent.id()).andExpect(status().isForbidden());
      reset(testerToken(), agent.id()).andExpect(status().isForbidden());
      reset(token, other.agentId()).andExpect(status().isNotFound());
      reset(token, UUID.randomUUID()).andExpect(status().isNotFound());

      assertThat(count(appender, "action=PASSWORD_CHANGED")).isZero();
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  @Test
  void aResetLeavesTheLoginUsernameAndStandingAmountsUnchanged() throws Exception {
    String token = managerToken();
    CreatedAgent agent = createAgentWithoutPassword(token, "Same After");
    String standingBefore = standingAmounts(token, agent.id());
    String usernameBefore = listedUsername(token, agent.id());

    reset(token, agent.id()).andExpect(status().isOk());

    assertThat(listedUsername(token, agent.id()))
        .isEqualTo(usernameBefore)
        .isEqualTo(agent.username());
    assertThat(standingAmounts(token, agent.id())).isEqualTo(standingBefore);
  }

  private ResultActions reset(String token, UUID agentId) throws Exception {
    return mockMvc.perform(
        post("/api/agents/" + agentId + "/login/password")
            .header("Authorization", "Bearer " + token));
  }

  private record CreatedAgent(UUID id, String username, String password) {}

  private CreatedAgent createAgentWithoutPassword(String token, String name) throws Exception {
    String username = "reset-" + UUID.randomUUID() + "@agents.example";
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("name", name);
    body.put("country", "FRANCE");
    body.put("salaryAmount", new BigDecimal("2000.00"));
    body.put("username", username);
    MvcResult created =
        postJson("/api/agents", token, body).andExpect(status().isCreated()).andReturn();
    var json = objectMapper.readTree(created.getResponse().getContentAsString());
    return new CreatedAgent(
        UUID.fromString(json.get("id").asText()), username, json.get("password").asText());
  }

  private String passwordOf(MvcResult result) throws Exception {
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("password")
        .asText();
  }

  private String loginJson(String username, String password) {
    return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
  }

  private String standingAmounts(String token, UUID agentId) throws Exception {
    return mockMvc
        .perform(
            get("/api/agents/" + agentId + "/standing-amounts")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String listedUsername(String token, UUID agentId) throws Exception {
    String listed =
        mockMvc
            .perform(get("/api/agents").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    for (var node : objectMapper.readTree(listed)) {
      if (agentId.toString().equals(node.get("id").asText())) {
        return node.get("loginUsername").asText();
      }
    }
    throw new AssertionError("Agent " + agentId + " not listed");
  }

  private static String logged(ListAppender<ILoggingEvent> appender) {
    return appender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .reduce("", (a, b) -> a + "\n" + b);
  }

  private static long count(ListAppender<ILoggingEvent> appender, String text) {
    return appender.list.stream().filter(e -> e.getFormattedMessage().contains(text)).count();
  }
}
