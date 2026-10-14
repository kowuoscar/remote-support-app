package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.dto.AgentCreateRequest;
import com.remotesupport.backend.dto.TesterCreateRequest;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.support.IntegrationTest;
import com.remotesupport.backend.support.OtherTenantFixture;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code PUT /api/me/name} and the {@code name} on {@code GET /api/me} (real-manager-dashboard,
 * manager-name-api ticket). Every Manager here is one the test creates itself; the seeded
 * Manager's login is never renamed. Refusals are separate test methods from successes, per the
 * MockMvc-transaction gotcha in {@code docs/agents/implementer-notes.md}.
 */
@Import(OtherTenantFixture.class)
class ManagerNameApiTest extends IntegrationTest {

  private static final String PASSWORD = "Passw0rd!23";

  @Autowired private OtherTenantFixture otherTenantFixture;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserRepository userRepository;

  @Test
  void anUnnamedManagerHasNoNameOnMe() throws Exception {
    ManagerLogin manager = freshManager();

    me(manager.token())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(manager.username()))
        .andExpect(jsonPath("$.name").doesNotExist());
  }

  @Test
  void theSeededManagerHasNoName() throws Exception {
    me(managerToken()).andExpect(status().isOk()).andExpect(jsonPath("$.name").doesNotExist());
  }

  @Test
  void aManagerSetsTheirNameTrimmedAndMeReadsItBackWithTheSameLogin() throws Exception {
    ManagerLogin manager = freshManager();

    putName(manager.token(), "  Yazan Abuhassan  ")
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));

    me(manager.token())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Yazan Abuhassan"))
        .andExpect(jsonPath("$.username").value(manager.username()));
    // The same token still works (checked above) and so does the old password.
    assertThat(loginAs(manager.username(), PASSWORD)).isNotBlank();
  }

  @Test
  void aSecondPutReplacesTheName() throws Exception {
    ManagerLogin manager = freshManager();
    putName(manager.token(), "First Name").andExpect(status().isNoContent());

    putName(manager.token(), "Second Name").andExpect(status().isNoContent());

    me(manager.token()).andExpect(jsonPath("$.name").value("Second Name"));
  }

  static Stream<Arguments> acceptedNames() {
    return Stream.of(
        Arguments.of("n".repeat(100), "n".repeat(100)),
        Arguments.of("  " + "n".repeat(100) + " \t", "n".repeat(100)));
  }

  @ParameterizedTest
  @MethodSource("acceptedNames")
  void aNameOfUpToOneHundredCharactersAfterTrimmingIsAccepted(String sent, String stored)
      throws Exception {
    ManagerLogin manager = freshManager();

    putName(manager.token(), sent).andExpect(status().isNoContent());

    me(manager.token()).andExpect(jsonPath("$.name").value(stored));
  }

  static Stream<Arguments> refusedNames() {
    return Stream.of(
        Arguments.of("n".repeat(101), "NAME_TOO_LONG"),
        Arguments.of("", "NAME_BLANK"),
        Arguments.of("   \t ", "NAME_BLANK"));
  }

  @ParameterizedTest
  @MethodSource("refusedNames")
  void aRefusedNameIsA400WithItsCodeAndLeavesTheNameAlone(String sent, String code)
      throws Exception {
    ManagerLogin manager = freshManager();
    storeName(manager.username(), "Kept Name");

    putName(manager.token(), sent)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(code));

    assertThat(storedName(manager.username())).isEqualTo("Kept Name");
  }

  @Test
  void aBodyWithNoNameIsRefusedAsInvalidRequest() throws Exception {
    ManagerLogin manager = freshManager();

    mockMvc
        .perform(
            put("/api/me/name")
                .header("Authorization", "Bearer " + manager.token())
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void anAgentIsRefusedAndHasNoNameOnMe() throws Exception {
    String username = "name-agent-" + UUID.randomUUID() + "@agents.example";
    MvcResult created =
        postJson(
                "/api/agents",
                managerToken(),
                new AgentCreateRequest(
                    "Name Agent " + UUID.randomUUID(),
                    Country.UNITED_STATES,
                    new BigDecimal("2000.00"),
                    username))
            .andExpect(status().isCreated())
            .andReturn();
    String token = loginAs(username, passwordOf(created));

    putName(token, "Someone").andExpect(status().isForbidden());

    me(token).andExpect(status().isOk()).andExpect(jsonPath("$.name").doesNotExist());
  }

  @Test
  void aTesterIsRefusedAndHasNoNameOnMe() throws Exception {
    String managerToken = managerToken();
    UUID clientId = createClient(managerToken, "Name Client " + UUID.randomUUID());
    String username = "name-tester-" + UUID.randomUUID() + "@example.com";
    MvcResult created =
        postJson(
                "/api/clients/" + clientId + "/testers",
                managerToken,
                new TesterCreateRequest(username, false))
            .andExpect(status().isCreated())
            .andReturn();
    String token = loginAs(username, passwordOf(created));

    putName(token, "Someone").andExpect(status().isForbidden());

    me(token).andExpect(status().isOk()).andExpect(jsonPath("$.name").doesNotExist());
  }

  @Test
  void noTokenIs401() throws Exception {
    mockMvc
        .perform(put("/api/me/name").contentType(APPLICATION_JSON).content("{\"name\":\"X\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void aDeactivatedManagerIs401AndTheirNameIsUnchanged() throws Exception {
    ManagerLogin manager = freshManager();
    storeName(manager.username(), "Kept Name");
    User user = userRepository.findByUsername(manager.username()).orElseThrow();
    user.deactivate(Instant.now());
    userRepository.saveAndFlush(user);

    putName(manager.token(), "New Name").andExpect(status().isUnauthorized());

    assertThat(storedName(manager.username())).isEqualTo("Kept Name");
  }

  @Test
  void aManagerChangesOnlyTheirOwnName() throws Exception {
    ManagerLogin first = freshManager();
    ManagerLogin second = freshManager();
    storeName(second.username(), "Second Original");
    String seededBefore = storedName(MANAGER_USERNAME);

    putName(first.token(), "First New").andExpect(status().isNoContent());

    assertThat(storedName(first.username())).isEqualTo("First New");
    assertThat(storedName(second.username())).isEqualTo("Second Original");
    assertThat(storedName(MANAGER_USERNAME)).isEqualTo(seededBefore);
  }

  private ResultActions putName(String token, String name) throws Exception {
    return mockMvc.perform(
        put("/api/me/name")
            .header("Authorization", "Bearer " + token)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new NameBody(name))));
  }

  private ResultActions me(String token) throws Exception {
    return mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token));
  }

  private ManagerLogin freshManager() throws Exception {
    String username = "name-manager-" + UUID.randomUUID() + "@example.com";
    otherTenantFixture.managerLoginInAnotherTenant(username, PASSWORD);
    return new ManagerLogin(username, loginAs(username, PASSWORD));
  }

  private void storeName(String username, String name) {
    User user = userRepository.findByUsername(username).orElseThrow();
    user.setName(name);
    userRepository.saveAndFlush(user);
  }

  private String storedName(String username) {
    userRepository.flush();
    return jdbcTemplate.queryForObject(
        "SELECT name FROM users WHERE username = ?", String.class, username);
  }

  private String passwordOf(MvcResult created) throws Exception {
    return objectMapper.readTree(created.getResponse().getContentAsString()).get("password").asText();
  }

  private record NameBody(String name) {}

  private record ManagerLogin(String username, String token) {}
}
