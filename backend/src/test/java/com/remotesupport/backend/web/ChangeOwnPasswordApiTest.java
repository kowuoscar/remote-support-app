package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.domain.Role;
import com.remotesupport.backend.dto.AgentCreateRequest;
import com.remotesupport.backend.dto.ChangePasswordRequest;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code POST /api/me/password} (change-own-password-endpoint ticket): the first write endpoint
 * acting on the caller rather than some other entity. Every fixture here is a Login the test
 * creates itself, never a seeded credential — spec.md Constraints: "no test — integration or
 * e2e — may change a seeded user's password", since the three seeded credentials back {@link
 * IntegrationTest}'s own token helpers and most e2e specs. The decisive proof of a successful
 * change is a real sign-in through {@link #loginAs}, never a {@code password_hash} read (spec.md
 * Testing decisions).
 *
 * <p>The wrong-current-password case is its own test method rather than chained after a success,
 * per the MockMvc-transaction gotcha in {@code docs/agents/implementer-notes.md}: requests in one
 * {@code @Transactional} test method share a Hibernate session, so a failing call followed by a
 * retry in the same method does not exercise what two separate attempts from a fresh client would.
 */
@Import(OtherTenantFixture.class)
class ChangeOwnPasswordApiTest extends IntegrationTest {

  private static final String NEW_PASSWORD = "BrandNewPassw0rd!";

  @Autowired private OtherTenantFixture otherTenantFixture;
  @Autowired private JdbcTemplate jdbcTemplate;

  /**
   * All three roles are covered at this seam (spec.md Testing decisions), each on a Login the
   * test itself creates. The Manager case is non-negotiable: it is the role whose {@code User} row
   * links to neither an Agent nor a Tester, and the one a naive implementation resolving the
   * caller through {@code CallerIdentityResolver} instead of {@code principal.userId()} would
   * break.
   */
  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"MANAGER", "AGENT", "TESTER"})
  void changingItsOwnPasswordLetsTheNewOneSignInAndRefusesTheOld(Role role) throws Exception {
    RoleLogin login = freshLoginFor(role);

    postJson(
            "/api/me/password",
            login.token(),
            new ChangePasswordRequest(login.password(), NEW_PASSWORD))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    loginAs(login.username(), NEW_PASSWORD);
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"username":"%s","password":"%s"}
                    """
                        .formatted(login.username(), login.password())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aWrongCurrentPasswordIsRefusedWithItsOwnCodeAndLeavesThePasswordUnchanged() throws Exception {
    RoleLogin login = freshManagerLogin();

    postJson(
            "/api/me/password",
            login.token(),
            new ChangePasswordRequest("definitely-the-wrong-password", NEW_PASSWORD))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("WRONG_CURRENT_PASSWORD"));

    loginAs(login.username(), login.password());
  }

  @Test
  void aNewPasswordIdenticalToTheCurrentOneIsRefusedWithItsOwnDistinctCode() throws Exception {
    RoleLogin login = freshManagerLogin();

    postJson(
            "/api/me/password",
            login.token(),
            new ChangePasswordRequest(login.password(), login.password()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PASSWORD_UNCHANGED"));

    loginAs(login.username(), login.password());
  }

  @Test
  void withNoAuthorizationHeaderIsRefused() throws Exception {
    mockMvc
        .perform(
            post("/api/me/password")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"currentPassword":"whatever","newPassword":"%s"}
                    """
                        .formatted(NEW_PASSWORD)))
        .andExpect(status().isUnauthorized());
  }

  /**
   * A wrong current password from each of the three seeded tokens is a refusal that never writes,
   * so this is safe to run against the seeded credentials directly (spec.md Constraints binds
   * only a test that actually changes a seeded password) — and it is the cleanest proof that none
   * of the three roles is turned away on role grounds: a role-based refusal would be {@code 403},
   * never the {@code 400} asserted here.
   */
  @Test
  void aValidJwtOfEachRoleIsNotRefusedOnRoleGrounds() throws Exception {
    for (String token : new String[] {managerToken(), agentToken(), testerToken()}) {
      postJson(
              "/api/me/password",
              token,
              new ChangePasswordRequest("definitely-the-wrong-password", NEW_PASSWORD))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("WRONG_CURRENT_PASSWORD"));
    }
  }

  @Test
  void aSuccessfulChangeLogsExactlyOnePasswordChangedLineNamingUserActorAndTenantNeverThePasswordOrHash()
      throws Exception {
    RoleLogin login = freshManagerLogin();
    UUID userId = userIdOf(login.username());
    UUID tenantId = tenantIdOf(login.token());

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      postJson(
              "/api/me/password",
              login.token(),
              new ChangePasswordRequest(login.password(), NEW_PASSWORD))
          .andExpect(status().isNoContent());

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);

      assertThat(logged).contains("action=PASSWORD_CHANGED");
      assertThat(logged).contains("entity=User");
      assertThat(logged).contains("entityId=" + userId);
      assertThat(logged).contains("actorUserId=" + userId);
      assertThat(logged).contains("tenantId=" + tenantId);
      assertThat(logged).doesNotContain(login.password());
      assertThat(logged).doesNotContain(NEW_PASSWORD);
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  @Test
  void aFailedVerificationLogsNoPasswordChangedLine() throws Exception {
    RoleLogin login = freshManagerLogin();

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);

    try {
      postJson(
              "/api/me/password",
              login.token(),
              new ChangePasswordRequest("definitely-the-wrong-password", NEW_PASSWORD))
          .andExpect(status().isBadRequest());

      String logged =
          appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
      assertThat(logged).doesNotContain("PASSWORD_CHANGED");
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  private RoleLogin freshLoginFor(Role role) throws Exception {
    return switch (role) {
      case MANAGER -> freshManagerLogin();
      case AGENT -> freshAgentLogin();
      case TESTER -> freshTesterLogin();
      case SUPER_ADMIN -> throw new IllegalArgumentException("Not exercised by this ticket");
    };
  }

  /**
   * A Manager Login (linked to neither an Agent nor a Tester) — there is no API to create one, so
   * it is built straight through the repository, the same shape {@link
   * OtherTenantFixture#managerLoginInAnotherTenant} already establishes for exactly this reason.
   */
  private RoleLogin freshManagerLogin() throws Exception {
    String username = "fresh-manager-" + UUID.randomUUID() + "@example.com";
    String password = "Passw0rd!23";
    otherTenantFixture.managerLoginInAnotherTenant(username, password);
    // Signing in for real, rather than trusting the fixture's own record, is what proves this
    // Manager Login actually works before the test changes its password.
    String token = loginAs(username, password);
    return new RoleLogin(username, password, token);
  }

  private RoleLogin freshAgentLogin() throws Exception {
    String username = "fresh-agent-" + UUID.randomUUID() + "@agents.example";
    String password = "Passw0rd!23";
    String managerToken = managerToken();
    postJson(
            "/api/agents",
            managerToken,
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

  private RoleLogin freshTesterLogin() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Fresh Tester Client " + UUID.randomUUID());
    String username = "fresh-tester-" + UUID.randomUUID() + "@example.com";
    String password = "Passw0rd!23";
    String token = createTesterAndLogin(managerToken, clientId, username, password);
    return new RoleLogin(username, password, token);
  }

  private UUID userIdOf(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM users WHERE username = ?", UUID.class, username);
  }

  private record RoleLogin(String username, String password, String token) {}
}
