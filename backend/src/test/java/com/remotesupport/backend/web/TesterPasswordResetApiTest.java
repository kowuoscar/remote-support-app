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
import com.remotesupport.backend.support.OtherTenantFixture.OtherTenantTesterLogin;
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
 * A Manager resets a Tester's password (manager-resets-a-password spec, "Reset endpoints"). Each
 * test creates its own Tester through the API with no password; request bodies are JSON maps so
 * these tests survive the creation contract step.
 */
@Import(OtherTenantFixture.class)
class TesterPasswordResetApiTest extends IntegrationTest {

  private static final String FORMAT = "^[a-km-np-z2-9]{4}(-[a-km-np-z2-9]{4}){2}$";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private OtherTenantFixture otherTenantFixture;

  @Test
  void resetAnswersANewGeneratedPasswordThatSignsInAndTheOldOneNoLonger() throws Exception {
    String token = managerToken();
    CreatedTester tester = createTester(token, createClient(token, "Reset C1"), false);

    MvcResult reset =
        reset(token, tester.clientId(), tester.id())
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.password").value(matchesPattern(FORMAT)))
            .andReturn();
    String newPassword = passwordOf(reset);

    assertThat(newPassword).isNotEqualTo(tester.password());
    assertThat(loginAs(tester.username(), newPassword)).isNotBlank();
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    "{\"username\":\"%s\",\"password\":\"%s\"}"
                        .formatted(tester.username(), tester.password())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void anUnknownTesterOrClientIsNotFound() throws Exception {
    String token = managerToken();
    CreatedTester tester = createTester(token, createClient(token, "Reset C2"), false);

    reset(token, tester.clientId(), UUID.randomUUID()).andExpect(status().isNotFound());
    reset(token, UUID.randomUUID(), tester.id()).andExpect(status().isNotFound());
  }

  @Test
  void aTesterInAnotherTenantIsNotFoundAndKeepsItsOriginalPassword() throws Exception {
    OtherTenantTesterLogin other =
        otherTenantFixture.testerLoginInAnotherTenant(
            "other-tenant-tester-" + UUID.randomUUID() + "@example.com", "Original#Passw0rd1");

    reset(managerToken(), other.clientId(), other.testerId()).andExpect(status().isNotFound());

    assertThat(loginAs(other.username(), other.password())).isNotBlank();
  }

  @Test
  void agentAndTesterAreRefusedAndTheOldPasswordStillWorks() throws Exception {
    String token = managerToken();
    CreatedTester tester = createTester(token, createClient(token, "Reset C3"), false);

    for (String refused : new String[] {agentToken(), testerToken()}) {
      reset(refused, tester.clientId(), tester.id()).andExpect(status().isForbidden());
    }

    assertThat(loginAs(tester.username(), tester.password())).isNotBlank();
  }

  @Test
  void aResetWritesOneAuditLineWithTheIdsAndNeverThePasswordOrHash() throws Exception {
    String token = managerToken();
    CreatedTester tester = createTester(token, createClient(token, "Reset C4"), false);
    UUID testerUserId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = ?", UUID.class, tester.username());
    UUID managerUserId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = ?", UUID.class, MANAGER_USERNAME);

    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    Logger auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);
    try {
      String newPassword = passwordOf(reset(token, tester.clientId(), tester.id()).andReturn());
      String hash =
          jdbcTemplate.queryForObject(
              "SELECT password_hash FROM users WHERE id = ?", String.class, testerUserId);

      String logged =
          appender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .reduce("", (a, b) -> a + "\n" + b);
      assertThat(logged).contains("action=PASSWORD_CHANGED entity=User entityId=" + testerUserId);
      assertThat(logged).contains("actorUserId=" + managerUserId);
      assertThat(logged).contains("tenantId=" + SEEDED_TENANT_ID);
      assertThat(
              appender.list.stream()
                  .filter(e -> e.getFormattedMessage().contains("action=PASSWORD_CHANGED"))
                  .count())
          .isEqualTo(1);
      assertThat(logged).doesNotContain(newPassword).doesNotContain(hash);
    } finally {
      auditLogger.detachAppender(appender);
    }
  }

  @Test
  void aResetLeavesTheListedUsernameAndPrimaryContactFlagUnchanged() throws Exception {
    String token = managerToken();
    UUID clientId = createClient(token, "Reset C5");
    CreatedTester tester = createTester(token, clientId, true);
    String before = listed(token, clientId);

    reset(token, clientId, tester.id()).andExpect(status().isOk());

    assertThat(listed(token, clientId)).isEqualTo(before);
    assertThat(before).contains(tester.username()).contains("\"isPrimaryContact\":true");
  }

  private ResultActions reset(String token, UUID clientId, UUID testerId) throws Exception {
    return mockMvc.perform(
        post("/api/clients/" + clientId + "/testers/" + testerId + "/password")
            .header("Authorization", "Bearer " + token));
  }

  private record CreatedTester(UUID id, UUID clientId, String username, String password) {}

  private CreatedTester createTester(String token, UUID clientId, boolean primary)
      throws Exception {
    String username = "reset-" + UUID.randomUUID() + "@testers.example";
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("username", username);
    body.put("isPrimaryContact", primary);
    MvcResult created =
        postJson("/api/clients/" + clientId + "/testers", token, body)
            .andExpect(status().isCreated())
            .andReturn();
    var json = objectMapper.readTree(created.getResponse().getContentAsString());
    return new CreatedTester(
        UUID.fromString(json.get("id").asText()),
        clientId,
        username,
        json.get("password").asText());
  }

  private String passwordOf(MvcResult result) throws Exception {
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("password")
        .asText();
  }

  private String listed(String token, UUID clientId) throws Exception {
    return mockMvc
        .perform(
            get("/api/clients/" + clientId + "/testers").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
