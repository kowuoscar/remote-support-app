package com.remotesupport.backend.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.support.IntegrationTest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating an Agent with a username already in use leaves nothing behind (agent-login-on-creation
 * spec, "One request, one transaction").
 *
 * <p>Runs outside the shared test transaction, unlike every other {@link IntegrationTest}: inside
 * it, the whole test rolls back anyway, so a create endpoint that committed the Agent before its
 * login failed would pass unnoticed. Here each request commits or rolls back for real, the
 * database is read directly afterwards, and whatever a test committed is deleted after it.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgentCreationAtomicityTest extends IntegrationTest {

  private static final String PASSWORD = "Passw0rd!23";
  private static final String NAME_PREFIX = "Atomicity " + UUID.randomUUID() + " ";

  @Autowired private JdbcTemplate jdbcTemplate;

  @AfterEach
  void deleteWhatThisTestCommitted() {
    String agentsOfThisClass = "SELECT id FROM agents WHERE name LIKE ?";
    String pattern = NAME_PREFIX + "%";
    jdbcTemplate.update(
        "DELETE FROM agent_standing_amounts WHERE agent_id IN (" + agentsOfThisClass + ")",
        pattern);
    jdbcTemplate.update(
        "DELETE FROM users WHERE agent_id IN (" + agentsOfThisClass + ")", pattern);
    jdbcTemplate.update("DELETE FROM agents WHERE name LIKE ?", pattern);
  }

  @Test
  void aUsernameHeldByAnotherUserLeavesNoAgentStandingAmountOrUserBehind() throws Exception {
    String token = managerToken();
    String name = NAME_PREFIX + "Manager Name Clash";
    Snapshot before = snapshot();

    postAgent(token, agentBody(name, MANAGER_USERNAME))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

    assertNothingCreated(token, name, before);
  }

  @Test
  void aUsernameHeldByAnotherAgentLeavesNoAgentStandingAmountOrUserBehind() throws Exception {
    String token = managerToken();
    String taken = "taken-" + UUID.randomUUID() + "@agents.example";
    postAgent(token, agentBody(NAME_PREFIX + "First Holder", taken))
        .andExpect(status().isCreated());
    String name = NAME_PREFIX + "Second Holder";
    Snapshot before = snapshot();

    postAgent(token, agentBody(name, taken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

    assertNothingCreated(token, name, before);
  }

  private record Snapshot(long agents, long standingAmounts, long users) {}

  private Snapshot snapshot() {
    return new Snapshot(
        count("SELECT COUNT(*) FROM agents"),
        count("SELECT COUNT(*) FROM agent_standing_amounts"),
        count("SELECT COUNT(*) FROM users"));
  }

  private long count(String sql) {
    return jdbcTemplate.queryForObject(sql, Long.class);
  }

  private void assertNothingCreated(String token, String name, Snapshot before) throws Exception {
    Assertions.assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agents WHERE name = ?", Long.class, name))
        .as("Agent rows named %s", name)
        .isZero();
    Assertions.assertThat(snapshot()).isEqualTo(before);

    mockMvc
        .perform(get("/api/agents").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name == '" + name + "')]").isEmpty());
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
}
