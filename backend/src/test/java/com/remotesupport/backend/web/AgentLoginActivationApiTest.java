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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.dto.AgentCreateRequest;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import com.remotesupport.backend.support.OtherTenantFixture.OtherTenantAgentLogin;
import java.math.BigDecimal;
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
 * A Manager deactivates and reactivates an Agent's Login (deactivate-a-login spec, "Endpoints").
 * Each test creates its own Agent through the API and never touches a seeded or demo Login.
 */
@Import(OtherTenantFixture.class)
class AgentLoginActivationApiTest extends IntegrationTest {

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
    NewAgent agent = newAgent(manager);
    String keptToken = loginAs(agent.username(), agent.password());

    deactivate(manager, agent.id())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deactivatedAt").isNotEmpty());

    signIn(agent.username(), agent.password())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("LOGIN_DEACTIVATED"));
    signIn(agent.username(), WRONG_PASSWORD)
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(""));
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + keptToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void reactivateRestoresTheOriginalPassword() throws Exception {
    String manager = managerToken();
    NewAgent agent = newAgent(manager);
    deactivate(manager, agent.id()).andExpect(status().isOk());

    reactivate(manager, agent.id())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deactivatedAt").value((Object) null));

    String fresh = loginAs(agent.username(), agent.password());
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + fresh))
        .andExpect(status().isOk());
  }

  @Test
  void deactivateTwiceKeepsTheTimestampAndWritesOneAuditLine() throws Exception {
    String manager = managerToken();
    NewAgent agent = newAgent(manager);

    String first = deactivatedAtOf(deactivate(manager, agent.id()).andExpect(status().isOk()));
    String second = deactivatedAtOf(deactivate(manager, agent.id()).andExpect(status().isOk()));

    assertThat(second).isEqualTo(first);
    assertThat(auditLines("LOGIN_DEACTIVATED", agent)).hasSize(1);
  }

  @Test
  void reactivateTwiceIs200NullAndWritesOneAuditLine() throws Exception {
    String manager = managerToken();
    NewAgent agent = newAgent(manager);
    reactivate(manager, agent.id())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deactivatedAt").value((Object) null));
    assertThat(auditLines("LOGIN_REACTIVATED", agent)).isEmpty();

    deactivate(manager, agent.id()).andExpect(status().isOk());
    reactivate(manager, agent.id()).andExpect(status().isOk());
    reactivate(manager, agent.id())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deactivatedAt").value((Object) null));

    assertThat(auditLines("LOGIN_REACTIVATED", agent)).hasSize(1);
  }

  @Test
  void deactivationIsNotDeletionEverythingElseReadsTheSame() throws Exception {
    String manager = managerToken();
    NewAgent agent = newAgent(manager);
    UUID clientId = createClient(manager, "Activation Client " + UUID.randomUUID());
    UUID contractId = createContract(manager, clientId, agent.id());
    String testerToken =
        createTesterAndLogin(manager, clientId, "activation-tester-" + UUID.randomUUID() + "@example.com");
    postRequest(
            contractId,
            testerToken,
            "{\"type\":\"PROVISION_SMARTPHONE\",\"requestedModel\":\"Pixel 9\"}")
        .andExpect(status().isCreated());

    String standingUrl = "/api/agents/" + agent.id() + "/standing-amounts";
    String invoiceUrl = "/api/agents/" + agent.id() + "/invoice";
    String requestsUrl = "/api/contracts/" + contractId + "/requests";
    String standingBefore = read(manager, standingUrl);
    String contractsBefore = read(manager, "/api/contracts");
    String invoiceBefore = read(manager, invoiceUrl);
    String requestsBefore = read(manager, requestsUrl);
    String agentsBefore = withoutLoginState(read(manager, "/api/agents"));

    deactivate(manager, agent.id()).andExpect(status().isOk());

    assertThat(read(manager, standingUrl)).isEqualTo(standingBefore);
    assertThat(read(manager, "/api/contracts")).isEqualTo(contractsBefore);
    assertThat(read(manager, invoiceUrl)).isEqualTo(invoiceBefore);
    assertThat(read(manager, requestsUrl)).isEqualTo(requestsBefore);
    assertThat(withoutLoginState(read(manager, "/api/agents"))).isEqualTo(agentsBefore);
  }

  @Test
  void creatingALoginForADeactivatedAgentIsStillAlreadyHasLogin() throws Exception {
    String manager = managerToken();
    NewAgent agent = newAgent(manager);
    deactivate(manager, agent.id()).andExpect(status().isOk());

    postJson(
            "/api/agents/" + agent.id() + "/login",
            manager,
            Map.of("username", "second-" + UUID.randomUUID() + "@agents.example"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AGENT_ALREADY_HAS_LOGIN"));
  }

  @Test
  void agentResponseCarriesLoginDeactivatedAtAndNullWithoutALogin() throws Exception {
    String manager = managerToken();
    NewAgent agent = newAgent(manager);
    UUID loginLess = insertLoginLessAgent(manager);
    String deactivatedAtPath = "$[?(@.id == '%s')].loginDeactivatedAt";
    String usernamePath = "$[?(@.id == '%s')].loginUsername";

    listAgents(manager)
        .andExpect(jsonPath(deactivatedAtPath.formatted(agent.id())).value(nullInList()));

    String deactivatedAt =
        deactivatedAtOf(deactivate(manager, agent.id()).andExpect(status().isOk()));
    listAgents(manager)
        .andExpect(jsonPath(deactivatedAtPath.formatted(agent.id())).value(deactivatedAt))
        .andExpect(jsonPath(usernamePath.formatted(agent.id())).value(agent.username()))
        .andExpect(jsonPath(deactivatedAtPath.formatted(loginLess)).value(nullInList()));

    reactivate(manager, agent.id()).andExpect(status().isOk());
    listAgents(manager)
        .andExpect(jsonPath(deactivatedAtPath.formatted(agent.id())).value(nullInList()));
  }

  @Test
  void resetOnADeactivatedLoginIs200AndSignsInOnlyAfterReactivation() throws Exception {
    String manager = managerToken();
    NewAgent agent = newAgent(manager);
    deactivate(manager, agent.id()).andExpect(status().isOk());

    MvcResult reset =
        mockMvc
            .perform(
                post("/api/agents/" + agent.id() + "/login/password")
                    .header("Authorization", "Bearer " + manager))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.password").isNotEmpty())
            .andReturn();
    String newPassword = objectMapper.readTree(body(reset)).get("password").asText();

    signIn(agent.username(), newPassword)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("LOGIN_DEACTIVATED"));

    reactivate(manager, agent.id()).andExpect(status().isOk());
    assertThat(loginAs(agent.username(), newPassword)).isNotBlank();
  }

  @Test
  void unknownAgentIs404() throws Exception {
    String manager = managerToken();
    deactivate(manager, UUID.randomUUID()).andExpect(status().isNotFound());
    reactivate(manager, UUID.randomUUID()).andExpect(status().isNotFound());
    assertThat(allActivationLines()).isEmpty();
  }

  @Test
  void loginLessAgentIs409AgentHasNoLogin() throws Exception {
    String manager = managerToken();
    UUID loginLess = insertLoginLessAgent(manager);

    deactivate(manager, loginLess)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AGENT_HAS_NO_LOGIN"));
    reactivate(manager, loginLess)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AGENT_HAS_NO_LOGIN"));
    assertThat(allActivationLines()).isEmpty();
  }

  @Test
  void agentAndTesterTokensAre403() throws Exception {
    String manager = managerToken();
    NewAgent agent = newAgent(manager);

    for (String token : new String[] {agentToken(), testerToken()}) {
      deactivate(token, agent.id()).andExpect(status().isForbidden());
      reactivate(token, agent.id()).andExpect(status().isForbidden());
      deactivate(token, SEEDED_AGENT_ID).andExpect(status().isForbidden());
    }
    assertThat(allActivationLines()).isEmpty();
    assertThat(loginAs(agent.username(), agent.password())).isNotBlank();
    assertThat(loginAs(AGENT_USERNAME, AGENT_PASSWORD)).isNotBlank();
  }

  @Test
  void otherTenantAgentIs404AndStillSignsIn() throws Exception {
    OtherTenantAgentLogin other =
        otherTenantFixture.agentLoginInAnotherTenant(
            "other-tenant-agent-" + UUID.randomUUID() + "@example.com", "Original#Passw0rd1");
    String manager = managerToken();

    deactivate(manager, other.agentId()).andExpect(status().isNotFound());
    reactivate(manager, other.agentId()).andExpect(status().isNotFound());

    assertThat(loginAs(other.username(), other.password())).isNotBlank();
    assertThat(allActivationLines()).isEmpty();
  }

  @Test
  void auditLinesNameTargetActorAndTenant() throws Exception {
    String manager = managerToken();
    NewAgent agent = newAgent(manager);
    UUID targetUserId = userIdOf(agent.username());
    UUID actorUserId = userIdOf(MANAGER_USERNAME);

    deactivate(manager, agent.id()).andExpect(status().isOk());
    reactivate(manager, agent.id()).andExpect(status().isOk());

    String expectedTail =
        "entity=User entityId=%s actorUserId=%s tenantId=%s"
            .formatted(targetUserId, actorUserId, tenantIdOf(manager));
    assertThat(auditLines("LOGIN_DEACTIVATED", agent))
        .singleElement()
        .asString()
        .contains(expectedTail);
    assertThat(auditLines("LOGIN_REACTIVATED", agent))
        .singleElement()
        .asString()
        .contains(expectedTail);
  }

  private UUID userIdOf(String username) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM users WHERE username = ?", UUID.class, username);
  }

  private UUID insertLoginLessAgent(String managerToken) {
    UUID agentId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO agents (id, tenant_id, name, country, currency, salary_amount)"
            + " VALUES (?, ?, 'No Login', 'FRANCE', 'EUR', 2000.00)",
        agentId,
        tenantIdOf(managerToken));
    return agentId;
  }

  private List<String> auditLines(String action, NewAgent agent) {
    UUID targetUserId = userIdOf(agent.username());
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

  private ResultActions deactivate(String token, UUID agentId) throws Exception {
    return mockMvc.perform(
        post("/api/agents/" + agentId + "/login/deactivate")
            .header("Authorization", "Bearer " + token));
  }

  private ResultActions reactivate(String token, UUID agentId) throws Exception {
    return mockMvc.perform(
        post("/api/agents/" + agentId + "/login/reactivate")
            .header("Authorization", "Bearer " + token));
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

  private ResultActions listAgents(String token) throws Exception {
    return mockMvc
        .perform(get("/api/agents").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  private String read(String token, String url) throws Exception {
    return body(
        mockMvc
            .perform(get(url).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn());
  }

  /** The Agents list with {@code loginDeactivatedAt} dropped, so before and after compare. */
  private String withoutLoginState(String agentsJson) throws Exception {
    JsonNode agents = objectMapper.readTree(agentsJson);
    agents.forEach(agent -> ((ObjectNode) agent).remove("loginDeactivatedAt"));
    return agents.toString();
  }

  private String deactivatedAtOf(ResultActions result) throws Exception {
    return objectMapper.readTree(body(result.andReturn())).get("deactivatedAt").asText();
  }

  private static String body(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString();
  }

  /** A filter path over one Agent matches a list; a null field is a one-element list of null. */
  @SuppressWarnings("unchecked")
  private static Matcher<Object> nullInList() {
    return (Matcher<Object>) (Matcher<?>) contains((Object) null);
  }

  private NewAgent newAgent(String managerToken) throws Exception {
    String username = "activation-" + UUID.randomUUID() + "@agents.example";
    MvcResult created =
        postJson(
                "/api/agents",
                managerToken,
                new AgentCreateRequest(
                    "Activation Agent " + UUID.randomUUID(),
                    Country.UNITED_STATES,
                    new BigDecimal("2000.00"),
                    username))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode json = objectMapper.readTree(body(created));
    return new NewAgent(
        UUID.fromString(json.get("id").asText()), username, json.get("password").asText());
  }

  private record NewAgent(UUID id, String username, String password) {}
}
