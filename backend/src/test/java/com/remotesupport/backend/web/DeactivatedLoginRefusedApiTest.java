package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.domain.Role;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.dto.AgentCreateRequest;
import com.remotesupport.backend.dto.ChangePasswordRequest;
import com.remotesupport.backend.dto.TesterCreateRequest;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A deactivated Login is refused at sign-in and on every token request (deactivate-a-login, "What
 * deactivated means, at three doors"; the third door has its own narrow test). Every Login here is
 * one the test creates through the API, never a seeded or demo Login. There is no HTTP way to
 * deactivate yet, so the test does it through the {@link User} entity inside its own transaction,
 * which MockMvc shares.
 */
class DeactivatedLoginRefusedApiTest extends IntegrationTest {

  private static final String WRONG_PASSWORD = "definitely-the-wrong-password";

  @Autowired private UserRepository userRepository;

  private ListAppender<ILoggingEvent> appender;
  private Logger rootLogger;

  @BeforeEach
  void attachAppender() {
    appender = new ListAppender<>();
    appender.start();
    rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    rootLogger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    rootLogger.detachAppender(appender);
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"AGENT", "TESTER"})
  void rightPasswordOnDeactivatedLoginIs401LoginDeactivated(Role role) throws Exception {
    Fixture login = freshLoginFor(role);
    deactivate(login.username());

    attemptSignIn(login.username(), login.password())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("LOGIN_DEACTIVATED"));
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"AGENT", "TESTER"})
  void wrongPasswordOnDeactivatedLoginIs401WithNoBodySameAsActive(Role role) throws Exception {
    Fixture login = freshLoginFor(role);

    attemptSignIn(login.username(), WRONG_PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(""));

    deactivate(login.username());

    attemptSignIn(login.username(), WRONG_PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(""));
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"AGENT", "TESTER"})
  void keptTokenIs401OnMeAnEndpointAndChangePassword(Role role) throws Exception {
    Fixture login = freshLoginFor(role);
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + login.token()))
        .andExpect(status().isOk());

    deactivate(login.username());

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + login.token()))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/contracts").header("Authorization", "Bearer " + login.token()))
        .andExpect(status().isUnauthorized());
    postJson(
            "/api/me/password",
            login.token(),
            new ChangePasswordRequest(login.password(), "BrandNewPassw0rd!"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void tokenOfADeletedUserRowIs401() throws Exception {
    String token =
        jwtService.issueToken(
            UUID.randomUUID(), "ghost@example.com", SEEDED_TENANT_ID, Role.AGENT.name());

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"AGENT", "TESTER"})
  void reactivatedLoginSignsInWithSamePasswordAndFreshTokenWorks(Role role) throws Exception {
    Fixture login = freshLoginFor(role);
    User user = userRepository.findByUsername(login.username()).orElseThrow();
    user.deactivate(Instant.now());
    userRepository.saveAndFlush(user);
    attemptSignIn(login.username(), login.password()).andExpect(status().isUnauthorized());

    user.reactivate();
    userRepository.saveAndFlush(user);

    String fresh = loginAs(login.username(), login.password());
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + fresh))
        .andExpect(status().isOk());
  }

  @ParameterizedTest
  @EnumSource(
      value = Role.class,
      names = {"AGENT", "TESTER"})
  void refusedSignInLogsOneWarnLineWithoutPasswordAndDoorTwoLogsNone(Role role) throws Exception {
    Fixture login = freshLoginFor(role);
    deactivate(login.username());
    UUID userId = userRepository.findByUsername(login.username()).orElseThrow().getId();
    appender.list.clear();

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + login.token()))
        .andExpect(status().isUnauthorized());
    assertThat(refusedDeactivatedLines()).isEmpty();

    attemptSignIn(login.username(), login.password()).andExpect(status().isUnauthorized());

    List<ILoggingEvent> lines = refusedDeactivatedLines();
    assertThat(lines).hasSize(1);
    assertThat(lines.get(0).getLevel()).isEqualTo(Level.WARN);
    assertThat(lines.get(0).getFormattedMessage())
        .contains("userId=" + userId)
        .contains("tenantId=" + SEEDED_TENANT_ID);
    assertThat(appender.list)
        .noneMatch(event -> event.getFormattedMessage().contains(login.password()));
  }

  private List<ILoggingEvent> refusedDeactivatedLines() {
    return appender.list.stream()
        .filter(event -> event.getFormattedMessage().contains("login refused deactivated"))
        .toList();
  }

  private void deactivate(String username) {
    User user = userRepository.findByUsername(username).orElseThrow();
    user.deactivate(Instant.now());
    userRepository.saveAndFlush(user);
  }

  private ResultActions attemptSignIn(String username, String password) throws Exception {
    return mockMvc.perform(
        post("/api/auth/login")
            .contentType(APPLICATION_JSON)
            .content(
                """
                {"username":"%s","password":"%s"}
                """
                    .formatted(username, password)));
  }

  private Fixture freshLoginFor(Role role) throws Exception {
    String managerToken = managerToken();
    MvcResult created;
    String username;
    if (role == Role.AGENT) {
      username = "deact-agent-" + UUID.randomUUID() + "@agents.example";
      created =
          postJson(
                  "/api/agents",
                  managerToken,
                  new AgentCreateRequest(
                      "Deactivation Agent " + UUID.randomUUID(),
                      Country.UNITED_STATES,
                      new BigDecimal("2000.00"),
                      username))
              .andExpect(status().isCreated())
              .andReturn();
    } else {
      UUID clientId = createClient(managerToken, "Deactivation Client " + UUID.randomUUID());
      username = "deact-tester-" + UUID.randomUUID() + "@example.com";
      created =
          postJson(
                  "/api/clients/" + clientId + "/testers",
                  managerToken,
                  new TesterCreateRequest(username, false))
              .andExpect(status().isCreated())
              .andReturn();
    }
    String password =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("password").asText();
    return new Fixture(username, password, loginAs(username, password));
  }

  private record Fixture(String username, String password, String token) {}
}
