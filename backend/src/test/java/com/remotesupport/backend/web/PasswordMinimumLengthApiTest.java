package com.remotesupport.backend.web;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.dto.AgentCreateRequest;
import com.remotesupport.backend.dto.AgentLoginCreateRequest;
import com.remotesupport.backend.dto.ChangePasswordRequest;
import com.remotesupport.backend.dto.TesterCreateRequest;
import com.remotesupport.backend.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The 8-character minimum (password-minimum-length ticket, spec.md "The password rule") on every
 * write path that sets a password: the change-password endpoint {@code
 * change-own-password-endpoint} adds, Agent creation, Tester creation, and giving an existing,
 * login-less Agent its login ({@code create-login-for-existing-agent} ticket's {@code
 * AgentLoginCreateRequest} — review finding F1: a fourth boundary that writes a password, missed
 * by this spec's own enumeration of three DTOs). A new class rather than added cases in {@code
 * AgentApiTest}/{@code TesterApiTest}/{@code ChangeOwnPasswordApiTest}/{@code AgentLoginApiTest} —
 * spec.md Testing decisions and this ticket's own {@code ## Tests} both require the table-driven
 * boundary coverage to live on its own so no pre-existing test file is touched.
 *
 * <p>Every fixture here is a Login this test creates itself, never a seeded credential — the same
 * constraint {@code ChangeOwnPasswordApiTest} follows, since the seeded credentials back {@code
 * IntegrationTest}'s own token helpers and most e2e specs (spec.md Constraints).
 */
class PasswordMinimumLengthApiTest extends IntegrationTest {

  private static final String SEVEN_CHARACTERS = "Ab3defg";
  private static final String EIGHT_CHARACTERS = "Ab3defgh";

  @Autowired private JdbcTemplate jdbcTemplate;

  private enum WritePath {
    CHANGE_PASSWORD,
    AGENT_CREATION,
    TESTER_CREATION,
    AGENT_LOGIN_CREATION
  }

  private static Stream<Arguments> boundaryCases() {
    return Stream.of(WritePath.values())
        .flatMap(
            path ->
                Stream.of(
                    Arguments.of(path, SEVEN_CHARACTERS, true),
                    Arguments.of(path, EIGHT_CHARACTERS, false)));
  }

  /**
   * Case: each of the three write paths × {7 chars refused, 8 chars accepted} (ticket ## Tests).
   * Each combination is its own parameterized invocation — its own fixtures, its own transaction —
   * so a refused 7-character attempt and an accepted 8-character one are never chained in the same
   * test method (the MockMvc-transaction gotcha in {@code docs/agents/implementer-notes.md} is
   * about a wrong-current-password case retried in the same method; this sidesteps it entirely by
   * never reusing one fixture across two calls).
   */
  @ParameterizedTest(name = "{0} with a {1}-character password: refused={2}")
  @MethodSource("boundaryCases")
  void theEightCharacterMinimumHoldsOnEveryWritePath(WritePath path, String password, boolean refused)
      throws Exception {
    switch (path) {
      case CHANGE_PASSWORD -> assertChangePasswordBoundary(password, refused);
      case AGENT_CREATION -> assertAgentCreationBoundary(password, refused);
      case TESTER_CREATION -> assertTesterCreationBoundary(password, refused);
      case AGENT_LOGIN_CREATION -> assertAgentLoginCreationBoundary(password, refused);
    }
  }

  /**
   * Case: the change-password path's 7-char refusal carries its own {@code code}, distinct from
   * the wrong-current-password {@code code} {@code change-own-password-endpoint} established
   * (ticket ## Tests) — asserted here as the literal string, which is itself the proof of
   * distinctness from {@code WRONG_CURRENT_PASSWORD}.
   */
  @Test
  void changePasswordSevenCharacterRefusalCarriesItsOwnDistinctCode() throws Exception {
    RoleLogin login = freshAgentLogin();

    postJson(
            "/api/me/password",
            login.token(),
            new ChangePasswordRequest(login.password(), SEVEN_CHARACTERS))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PASSWORD_TOO_SHORT"));

    // The refusal never reached the service, so the current password still signs in.
    loginAs(login.username(), login.password());
  }

  /**
   * Case: the two creation paths' 7-char refusal is the same shape as their existing
   * blank-password {@code 400} — no new {@code code} introduced there (ticket ## Tests).
   */
  @ParameterizedTest
  @EnumSource(
      value = WritePath.class,
      names = {"AGENT_CREATION", "TESTER_CREATION", "AGENT_LOGIN_CREATION"})
  void creationPathsSevenCharacterRefusalCarriesNoCode(WritePath path) throws Exception {
    switch (path) {
      case AGENT_CREATION ->
          postJson(
                  "/api/agents",
                  managerToken(),
                  new AgentCreateRequest(
                      "Boundary Agent " + UUID.randomUUID(),
                      Country.UNITED_STATES,
                      new BigDecimal("2000.00"),
                      "boundary-agent-" + UUID.randomUUID() + "@agents.example",
                      SEVEN_CHARACTERS))
              .andExpect(status().isBadRequest())
              .andExpect(jsonPath("$.code").doesNotExist());
      case TESTER_CREATION -> {
        String managerToken = managerToken();
        UUID clientId = createClient(managerToken, "Boundary Tester Client " + UUID.randomUUID());
        postJson(
                "/api/clients/" + clientId + "/testers",
                managerToken,
                new TesterCreateRequest(
                    "boundary-tester-" + UUID.randomUUID() + "@example.com",
                    SEVEN_CHARACTERS,
                    false))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").doesNotExist());
      }
      case AGENT_LOGIN_CREATION -> {
        String managerToken = managerToken();
        UUID agentId =
            insertLoginLessAgent(managerTenantId(), "Boundary Agent Login " + UUID.randomUUID());
        postJson(
                "/api/agents/" + agentId + "/login",
                managerToken,
                new AgentLoginCreateRequest(
                    "boundary-agent-login-" + UUID.randomUUID() + "@agents.example",
                    SEVEN_CHARACTERS))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").doesNotExist());
      }
      default -> throw new IllegalArgumentException("Not exercised by this test: " + path);
    }
  }

  private void assertChangePasswordBoundary(String newPassword, boolean refused) throws Exception {
    RoleLogin login = freshAgentLogin();

    postJson(
            "/api/me/password", login.token(), new ChangePasswordRequest(login.password(), newPassword))
        .andExpect(refused ? status().isBadRequest() : status().isNoContent());

    // The accepting side of the boundary really changed the password; the refusing side left it
    // alone — either way, proven through a real sign-in (spec.md Testing decisions), never a hash
    // read.
    loginAs(login.username(), refused ? login.password() : newPassword);
  }

  private void assertAgentCreationBoundary(String password, boolean refused) throws Exception {
    postJson(
            "/api/agents",
            managerToken(),
            new AgentCreateRequest(
                "Boundary Agent " + UUID.randomUUID(),
                Country.UNITED_STATES,
                new BigDecimal("2000.00"),
                "boundary-agent-" + UUID.randomUUID() + "@agents.example",
                password))
        .andExpect(refused ? status().isBadRequest() : status().isCreated());
  }

  private void assertTesterCreationBoundary(String password, boolean refused) throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Boundary Tester Client " + UUID.randomUUID());

    postJson(
            "/api/clients/" + clientId + "/testers",
            managerToken,
            new TesterCreateRequest(
                "boundary-tester-" + UUID.randomUUID() + "@example.com", password, false))
        .andExpect(refused ? status().isBadRequest() : status().isCreated());
  }

  /**
   * The fourth write path (review finding F1): giving an existing, login-less Agent its login
   * ({@code create-login-for-existing-agent} ticket, {@code POST /api/agents/{id}/login}). The
   * accepting side really creates a working Login, proven the same way every other boundary case
   * here is — a real sign-in, never a hash read.
   */
  private void assertAgentLoginCreationBoundary(String password, boolean refused) throws Exception {
    String managerToken = managerToken();
    UUID agentId =
        insertLoginLessAgent(managerTenantId(), "Boundary Agent Login " + UUID.randomUUID());
    String username = "boundary-agent-login-" + UUID.randomUUID() + "@agents.example";

    postJson(
            "/api/agents/" + agentId + "/login",
            managerToken,
            new AgentLoginCreateRequest(username, password))
        .andExpect(refused ? status().isBadRequest() : status().isCreated());

    if (!refused) {
      loginAs(username, password);
    }
  }

  /**
   * The Tenant id of the seeded {@code MANAGER_USERNAME} login — the same query {@code
   * AgentLoginApiTest.managerTenantId} uses, duplicated here rather than hoisted onto {@code
   * IntegrationTest}, since this ticket touches no pre-existing test file.
   */
  private UUID managerTenantId() {
    return jdbcTemplate.queryForObject(
        "SELECT tenant_id FROM users WHERE username = ?", UUID.class, MANAGER_USERNAME);
  }

  /**
   * A login-less Agent, inserted directly rather than through the API — the API can no longer
   * create an Agent without a login (agent-login-on-creation spec, Testing decisions) — the same
   * fixture shape {@code AgentLoginApiTest.insertLoginLessAgent} uses, duplicated here for the
   * same reason as {@link #managerTenantId()}.
   */
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

  /**
   * A fresh Agent Login, created through the API rather than any seeded credential (spec.md
   * Constraints) — the same pattern {@code ChangeOwnPasswordApiTest.freshAgentLogin} uses,
   * duplicated here rather than hoisted onto {@code IntegrationTest}, since this ticket touches no
   * pre-existing test file.
   */
  private RoleLogin freshAgentLogin() throws Exception {
    String username = "fresh-agent-" + UUID.randomUUID() + "@agents.example";
    String password = "Passw0rd!23";
    postJson(
            "/api/agents",
            managerToken(),
            new AgentCreateRequest(
                "Fresh Agent " + UUID.randomUUID(),
                Country.UNITED_STATES,
                new BigDecimal("2000.00"),
                username,
                password))
        .andExpect(status().isCreated());
    String token = loginAs(username, password);
    return new RoleLogin(username, password, token);
  }

  private record RoleLogin(String username, String password, String token) {}
}
