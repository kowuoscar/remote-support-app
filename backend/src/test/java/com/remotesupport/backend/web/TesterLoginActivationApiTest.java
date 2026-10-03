package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import com.remotesupport.backend.support.OtherTenantFixture.OtherTenantTesterLogin;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A Manager deactivates and reactivates a Tester's Login (deactivate-a-login spec, "Endpoints").
 * Each test creates its own Client and Tester through the API and never touches a seeded Login.
 */
@Import(OtherTenantFixture.class)
class TesterLoginActivationApiTest extends IntegrationTest {

  private static final String WRONG_PASSWORD = "definitely-the-wrong-password";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private OtherTenantFixture otherTenantFixture;

  private ListAppender<ILoggingEvent> appender;
  private Logger auditLogger;

  @BeforeEach
  void attachAppender() {
    appender = new ListAppender<>();
    appender.start();
    auditLogger = (Logger) LoggerFactory.getLogger("AUDIT");
    auditLogger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    auditLogger.detachAppender(appender);
  }

  @Test
  void deactivateRefusesSignInWrongPasswordAndKeptToken() throws Exception {
    String manager = managerToken();
    NewTester tester = newTester(manager, createClient(manager, "Act C1"), false);
    String keptToken = loginAs(tester.username(), tester.password());

    deactivate(manager, tester)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deactivatedAt").isNotEmpty());

    signIn(tester.username(), tester.password())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("LOGIN_DEACTIVATED"));
    signIn(tester.username(), WRONG_PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(""));
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + keptToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void reactivateRestoresTheOriginalPassword() throws Exception {
    String manager = managerToken();
    NewTester tester = newTester(manager, createClient(manager, "Act C2"), false);
    deactivate(manager, tester).andExpect(status().isOk());

    reactivate(manager, tester)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deactivatedAt").value((Object) null));

    String fresh = loginAs(tester.username(), tester.password());
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + fresh))
        .andExpect(status().isOk());
  }

  @Test
  void deactivateTwiceKeepsTheTimestampAndWritesOneAuditLine() throws Exception {
    String manager = managerToken();
    NewTester tester = newTester(manager, createClient(manager, "Act C3"), false);

    String first = deactivatedAtOf(deactivate(manager, tester).andExpect(status().isOk()));
    String second = deactivatedAtOf(deactivate(manager, tester).andExpect(status().isOk()));

    assertThat(second).isEqualTo(first);
    assertThat(auditLines("LOGIN_DEACTIVATED", tester)).hasSize(1);

    reactivate(manager, tester).andExpect(status().isOk());
    reactivate(manager, tester)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deactivatedAt").value((Object) null));
    assertThat(auditLines("LOGIN_REACTIVATED", tester)).hasSize(1);
  }

  @Test
  void testersListKeepsEmailAndPrimaryContactAndShowsDeactivatedAt() throws Exception {
    String manager = managerToken();
    UUID clientId = createClient(manager, "Act C4");
    NewTester tester = newTester(manager, clientId, true);
    String path = "$[?(@.id == '%s')].".formatted(tester.id());

    listTesters(manager, clientId)
        .andExpect(jsonPath(path + "username").value(tester.username()))
        .andExpect(jsonPath(path + "isPrimaryContact").value(true))
        .andExpect(jsonPath(path + "deactivatedAt").value(nullInList()));

    String deactivatedAt = deactivatedAtOf(deactivate(manager, tester).andExpect(status().isOk()));
    listTesters(manager, clientId)
        .andExpect(jsonPath(path + "username").value(tester.username()))
        .andExpect(jsonPath(path + "isPrimaryContact").value(true))
        .andExpect(jsonPath(path + "deactivatedAt").value(deactivatedAt));

    reactivate(manager, tester).andExpect(status().isOk());
    listTesters(manager, clientId)
        .andExpect(jsonPath(path + "deactivatedAt").value(nullInList()));
  }

  @Test
  void clientRequestsStillNameTheTesterAsRaiser() throws Exception {
    String manager = managerToken();
    UUID clientId = createClient(manager, "Act C5");
    UUID agentId = createAgent(manager, "Act Agent " + UUID.randomUUID(), Country.UNITED_STATES);
    UUID contractId = createContract(manager, clientId, agentId);
    NewTester tester = newTester(manager, clientId, false);
    String token = loginAs(tester.username(), tester.password());
    postRequest(
            contractId,
            token,
            "{\"type\":\"PROVISION_SMARTPHONE\",\"requestedModel\":\"Pixel 9\"}")
        .andExpect(status().isCreated());

    deactivate(manager, tester).andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/contracts/" + contractId + "/requests")
                .header("Authorization", "Bearer " + manager))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].raisedByUsername").value(tester.username()))
        .andExpect(jsonPath("$[0].raisedByTesterId").value(tester.id().toString()));
  }

  @Test
  void resetOnADeactivatedTesterIs200AndSignsInOnlyAfterReactivation() throws Exception {
    String manager = managerToken();
    NewTester tester = newTester(manager, createClient(manager, "Act C6"), false);
    deactivate(manager, tester).andExpect(status().isOk());

    MvcResult reset =
        mockMvc
            .perform(
                post("/api/clients/" + tester.clientId() + "/testers/" + tester.id() + "/password")
                    .header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk())
            .andReturn();
    String newPassword =
        objectMapper.readTree(reset.getResponse().getContentAsString()).get("password").asText();

    signIn(tester.username(), newPassword)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("LOGIN_DEACTIVATED"));

    reactivate(manager, tester).andExpect(status().isOk());
    assertThat(loginAs(tester.username(), newPassword)).isNotBlank();
  }

  @Test
  void unknownClientOrTesterAndTesterOfAnotherClientAre404() throws Exception {
    String manager = managerToken();
    UUID clientA = createClient(manager, "Act C7a");
    UUID clientB = createClient(manager, "Act C7b");
    NewTester tester = newTester(manager, clientA, false);

    for (String action : new String[] {"deactivate", "reactivate"}) {
      act(manager, action, clientA, UUID.randomUUID()).andExpect(status().isNotFound());
      act(manager, action, UUID.randomUUID(), tester.id()).andExpect(status().isNotFound());
      act(manager, action, clientB, tester.id()).andExpect(status().isNotFound());
    }
    assertThat(allActivationLines()).isEmpty();
    assertThat(loginAs(tester.username(), tester.password())).isNotBlank();
  }

  @Test
  void agentAndTesterTokensAre403() throws Exception {
    String manager = managerToken();
    NewTester tester = newTester(manager, createClient(manager, "Act C8"), false);

    for (String token : new String[] {agentToken(), testerToken()}) {
      deactivate(token, tester).andExpect(status().isForbidden());
      reactivate(token, tester).andExpect(status().isForbidden());
    }
    assertThat(allActivationLines()).isEmpty();
    assertThat(loginAs(tester.username(), tester.password())).isNotBlank();
  }

  @Test
  void otherTenantClientAndTesterAre404AndStillSignIn() throws Exception {
    OtherTenantTesterLogin other =
        otherTenantFixture.testerLoginInAnotherTenant(
            "other-tenant-tester-" + UUID.randomUUID() + "@example.com", "Original#Passw0rd1");
    String manager = managerToken();

    for (String action : new String[] {"deactivate", "reactivate"}) {
      act(manager, action, other.clientId(), other.testerId()).andExpect(status().isNotFound());
    }

    assertThat(loginAs(other.username(), other.password())).isNotBlank();
    assertThat(allActivationLines()).isEmpty();
  }

  @Test
  void auditLinesNameTargetActorAndTenantAndNoneForRefusals() throws Exception {
    String manager = managerToken();
    NewTester tester = newTester(manager, createClient(manager, "Act C10"), false);
    UUID targetUserId = userIdOf(tester.username());
    UUID actorUserId = userIdOf(MANAGER_USERNAME);

    deactivate(agentToken(), tester).andExpect(status().isForbidden());
    assertThat(allActivationLines()).isEmpty();

    deactivate(manager, tester).andExpect(status().isOk());
    reactivate(manager, tester).andExpect(status().isOk());

    String expectedTail =
        "entity=User entityId=%s actorUserId=%s tenantId=%s"
            .formatted(targetUserId, actorUserId, tenantIdOf(manager));
    assertThat(auditLines("LOGIN_DEACTIVATED", tester))
        .singleElement()
        .asString()
        .contains(expectedTail);
    assertThat(auditLines("LOGIN_REACTIVATED", tester))
        .singleElement()
        .asString()
        .contains(expectedTail);
  }

  private UUID userIdOf(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM users WHERE username = ?", UUID.class, username);
  }

  private List<String> auditLines(String action, NewTester tester) {
    UUID targetUserId = userIdOf(tester.username());
    return appender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .filter(m -> m.contains("action=" + action) && m.contains("entityId=" + targetUserId))
        .toList();
  }

  private List<String> allActivationLines() {
    return appender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .filter(
            m -> m.contains("action=LOGIN_DEACTIVATED") || m.contains("action=LOGIN_REACTIVATED"))
        .toList();
  }

  private ResultActions act(String token, String action, UUID clientId, UUID testerId)
      throws Exception {
    return mockMvc.perform(
        post("/api/clients/" + clientId + "/testers/" + testerId + "/" + action)
            .header("Authorization", "Bearer " + token));
  }

  private ResultActions deactivate(String token, NewTester tester) throws Exception {
    return act(token, "deactivate", tester.clientId(), tester.id());
  }

  private ResultActions reactivate(String token, NewTester tester) throws Exception {
    return act(token, "reactivate", tester.clientId(), tester.id());
  }

  private ResultActions signIn(String username, String password) throws Exception {
    return mockMvc.perform(
        post("/api/auth/login")
            .contentType(APPLICATION_JSON)
            .content(
                """
                {"username":"%s","password":"%s"}
                """
                    .formatted(username, password)));
  }

  private ResultActions listTesters(String token, UUID clientId) throws Exception {
    return mockMvc
        .perform(
            get("/api/clients/" + clientId + "/testers").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  private String deactivatedAtOf(ResultActions result) throws Exception {
    return objectMapper
        .readTree(result.andReturn().getResponse().getContentAsString())
        .get("deactivatedAt")
        .asText();
  }

  /** A filter path over one Tester matches a list; a null field is a one-element list of null. */
  @SuppressWarnings("unchecked")
  private static Matcher<Object> nullInList() {
    return (Matcher<Object>) (Matcher<?>) contains((Object) null);
  }

  private NewTester newTester(String token, UUID clientId, boolean primary) throws Exception {
    String username = "activation-" + UUID.randomUUID() + "@testers.example";
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("username", username);
    body.put("isPrimaryContact", primary);
    MvcResult created =
        postJson("/api/clients/" + clientId + "/testers", token, body)
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode json = objectMapper.readTree(created.getResponse().getContentAsString());
    return new NewTester(
        UUID.fromString(json.get("id").asText()),
        clientId,
        username,
        json.get("password").asText());
  }

  private record NewTester(UUID id, UUID clientId, String username, String password) {}
}
