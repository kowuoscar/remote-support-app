package com.remotesupport.backend.web;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import com.remotesupport.backend.support.OtherTenantFixture.OtherTenantLogin;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Giving an existing, login-less Agent its login (create-login-for-existing-agent ticket).
 *
 * <p>The API can no longer create an Agent without a login, so the login-less Agent fixture is
 * inserted directly — with the create-login e2e spec's fixture, the only places setup bypasses
 * the API (agent-login-on-creation spec, Testing decisions). The insert joins the test's
 * rolled-back transaction.
 */
@Import(OtherTenantFixture.class)
class AgentLoginApiTest extends IntegrationTest {

  private static final String PASSWORD = "Passw0rd!23";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private OtherTenantFixture otherTenantFixture;

  @Test
  void managerCreatesALoginForALoginLessAgentWhichCanThenSignIn() throws Exception {
    String token = managerToken();
    UUID agentId = insertLoginLessAgent(managerTenantId(), "Sofia Marin");

    postLogin(token, agentId, loginBody("sofia.marin@agents.example", PASSWORD))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(agentId.toString()))
        .andExpect(jsonPath("$.name").value("Sofia Marin"))
        .andExpect(jsonPath("$.loginUsername").value("sofia.marin@agents.example"));

    assertLoginUsername(token, agentId, "sofia.marin@agents.example");

    String agentToken = loginAs("sofia.marin@agents.example", PASSWORD);
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + agentToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("AGENT"))
        .andExpect(jsonPath("$.agentId").value(agentId.toString()));
  }

  @Test
  void aLoginLessAgentIsListedWithANullLoginUsername() throws Exception {
    String token = managerToken();
    UUID agentId = insertLoginLessAgent(managerTenantId(), "Listed Without Login");

    assertLoginUsername(token, agentId, null);
  }

  @Test
  void creatingASecondLoginForAnAgentIsRejected() throws Exception {
    String token = managerToken();
    UUID agentId = insertLoginLessAgent(managerTenantId(), "Twice Given");
    postLogin(token, agentId, loginBody("first.login@agents.example", PASSWORD))
        .andExpect(status().isCreated());

    postLogin(token, agentId, loginBody("second.login@agents.example", PASSWORD))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AGENT_ALREADY_HAS_LOGIN"));

    assertLoginUsername(token, agentId, "first.login@agents.example");
  }

  @Test
  void creatingALoginForTheSeededAgentWhichAlreadyHasOneIsRejected() throws Exception {
    postLogin(managerToken(), SEEDED_AGENT_ID, loginBody("another@agents.example", PASSWORD))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AGENT_ALREADY_HAS_LOGIN"));
  }

  @Test
  void anAgentWithALoginIsRejectedAsSuchEvenWhenTheUsernameIsAlsoTaken() throws Exception {
    postLogin(managerToken(), SEEDED_AGENT_ID, loginBody(MANAGER_USERNAME, PASSWORD))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AGENT_ALREADY_HAS_LOGIN"));
  }

  @Test
  void aUsernameAlreadyInUseIsRejectedAndTheAgentStaysWithoutALogin() throws Exception {
    String token = managerToken();
    UUID agentId = insertLoginLessAgent(managerTenantId(), "Name Clash");

    postLogin(token, agentId, loginBody(MANAGER_USERNAME, PASSWORD))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

    assertLoginUsername(token, agentId, null);
  }

  /**
   * The same cross-Tenant collision, told three ways: the exact username, one differing only by
   * case, and one differing only by surrounding whitespace — one {@link ParameterizedTest} in
   * place of three copies identical but for the transformation (review finding F9;
   * docs/agents/coding-standards.md Backend rule 12 doesn't bind here, but carries the same
   * coverage with one body).
   */
  @ParameterizedTest
  @MethodSource("usernameTransformations")
  void aUsernameTakenInAnotherTenantIsRejectedAndTheAgentStaysWithoutALogin(
      UnaryOperator<String> transformation) throws Exception {
    OtherTenantLogin otherTenantLogin =
        otherTenantFixture.managerLoginInAnotherTenant(
            "cross-tenant-login-" + UUID.randomUUID() + "@example.com", "Different#Passw0rd1");

    String token = managerToken();
    UUID agentId = insertLoginLessAgent(managerTenantId(), "Cross Tenant Name Clash");

    postLogin(token, agentId, loginBody(transformation.apply(otherTenantLogin.username()), PASSWORD))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

    assertLoginUsername(token, agentId, null);
  }

  static Stream<Named<UnaryOperator<String>>> usernameTransformations() {
    UnaryOperator<String> identical = UnaryOperator.identity();
    UnaryOperator<String> upperCased = String::toUpperCase;
    UnaryOperator<String> whitespacePadded = username -> "  " + username + "  ";
    return Stream.of(
        Named.of("identical", identical),
        Named.of("upper-cased", upperCased),
        Named.of("surrounded by whitespace", whitespacePadded));
  }

  @Test
  void aMissingUsernameOrPasswordIsRejected() throws Exception {
    String token = managerToken();
    UUID agentId = insertLoginLessAgent(managerTenantId(), "Incomplete Login");

    Map<String, Object> noUsername = loginBody("unused@agents.example", PASSWORD);
    noUsername.remove("username");
    postLogin(token, agentId, noUsername).andExpect(status().isBadRequest());

    Map<String, Object> blankPassword = loginBody("unused@agents.example", " ");
    postLogin(token, agentId, blankPassword).andExpect(status().isBadRequest());

    Map<String, Object> noPassword = loginBody("unused@agents.example", PASSWORD);
    noPassword.remove("password");
    postLogin(token, agentId, noPassword).andExpect(status().isBadRequest());

    assertLoginUsername(token, agentId, null);
  }

  @Test
  void anUnknownAgentIsNotFound() throws Exception {
    postLogin(managerToken(), UUID.randomUUID(), loginBody("ghost@agents.example", PASSWORD))
        .andExpect(status().isNotFound());
  }

  @Test
  void anAgentInAnotherTenantIsNotFound() throws Exception {
    UUID otherTenantId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO tenants (id, name) VALUES (?, ?)", otherTenantId, "Other Tenant");
    UUID foreignAgentId = insertLoginLessAgent(otherTenantId, "Foreign Agent");

    postLogin(managerToken(), foreignAgentId, loginBody("foreign@agents.example", PASSWORD))
        .andExpect(status().isNotFound());
  }

  @Test
  void agentAndTesterCannotCreateAnAgentsLogin() throws Exception {
    UUID agentId = insertLoginLessAgent(managerTenantId(), "Not Yours To Give");

    for (String token : new String[] {agentToken(), testerToken()}) {
      postLogin(token, agentId, loginBody("forbidden@agents.example", PASSWORD))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void creatingALoginLogsAnAuditEntryWithoutThePassword() throws Exception {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      String token = managerToken();
      UUID agentId = insertLoginLessAgent(managerTenantId(), "Audited Agent");
      postLogin(token, agentId, loginBody("audited@agents.example", PASSWORD))
          .andExpect(status().isCreated());

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      Assertions.assertThat(logged).contains("action=AGENT_LOGIN_CREATED");
      Assertions.assertThat(logged).contains("agentId=" + agentId);
      Assertions.assertThat(logged).doesNotContain(PASSWORD);
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  private UUID managerTenantId() {
    return jdbcTemplate.queryForObject(
        "SELECT tenant_id FROM users WHERE username = ?", UUID.class, MANAGER_USERNAME);
  }

  private UUID insertLoginLessAgent(UUID tenantId, String name) {
    UUID agentId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO agents (id, tenant_id, name, country, currency, salary_amount)"
            + " VALUES (?, ?, ?, 'FRANCE', 'EUR', 2000.00)",
        agentId,
        tenantId,
        name);
    return agentId;
  }

  private Map<String, Object> loginBody(String username, String password) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("username", username);
    body.put("password", password);
    return body;
  }

  private ResultActions postLogin(String token, UUID agentId, Map<String, Object> body)
      throws Exception {
    return postJson("/api/agents/" + agentId + "/login", token, body);
  }

  private void assertLoginUsername(String token, UUID agentId, String expected) throws Exception {
    ResultActions listed =
        mockMvc
            .perform(get("/api/agents").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    String path = "$[?(@.id == '" + agentId + "')].loginUsername";
    if (expected == null) {
      listed.andExpect(jsonPath("$[?(@.id == '" + agentId + "')]").isNotEmpty());
      listed.andExpect(jsonPath(path).value(contains((Object) null)));
    } else {
      listed.andExpect(jsonPath(path).value(expected));
    }
  }
}
