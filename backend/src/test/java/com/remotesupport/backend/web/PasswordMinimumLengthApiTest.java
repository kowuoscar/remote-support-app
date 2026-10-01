package com.remotesupport.backend.web;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.dto.AgentCreateRequest;
import com.remotesupport.backend.dto.ChangePasswordRequest;
import com.remotesupport.backend.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The 8-character minimum (password-minimum-length ticket, spec.md "The password rule") on the one
 * write path that still takes a typed password: the change-password endpoint. Login creation
 * takes no typed password any more (creation-takes-no-typed-password ticket), so it has no
 * boundary to test; a typed value sent there is ignored, which {@code
 * GeneratedPasswordCreationApiTest} covers.
 *
 * <p>Every fixture here is a Login this test creates itself, never a seeded credential — the same
 * constraint {@code ChangeOwnPasswordApiTest} follows, since the seeded credentials back {@code
 * IntegrationTest}'s own token helpers and most e2e specs (spec.md Constraints).
 */
class PasswordMinimumLengthApiTest extends IntegrationTest {

  private static final String SEVEN_CHARACTERS = "Ab3defg";
  private static final String EIGHT_CHARACTERS = "Ab3defgh";

  private static Stream<Arguments> boundaryCases() {
    return Stream.of(
        Arguments.of(SEVEN_CHARACTERS, true), Arguments.of(EIGHT_CHARACTERS, false));
  }

  /**
   * Case: {7 chars refused, 8 chars accepted}. Each combination is its own parameterized
   * invocation — its own fixtures, its own transaction — so a refused 7-character attempt and an
   * accepted 8-character one are never chained in the same test method (the MockMvc-transaction
   * gotcha in {@code docs/agents/implementer-notes.md}).
   */
  @ParameterizedTest(name = "change password to a {0}-character value: refused={1}")
  @MethodSource("boundaryCases")
  void theEightCharacterMinimumHoldsOnChangePassword(String newPassword, boolean refused)
      throws Exception {
    RoleLogin login = freshAgentLogin();

    postJson(
            "/api/me/password", login.token(), new ChangePasswordRequest(login.password(), newPassword))
        .andExpect(refused ? status().isBadRequest() : status().isNoContent());

    // The accepting side of the boundary really changed the password; the refusing side left it
    // alone — either way, proven through a real sign-in (spec.md Testing decisions), never a hash
    // read.
    loginAs(login.username(), refused ? login.password() : newPassword);
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
   * A fresh Agent Login, created through the API rather than any seeded credential (spec.md
   * Constraints), signed in with the password the creation returned.
   */
  private RoleLogin freshAgentLogin() throws Exception {
    String username = "fresh-agent-" + UUID.randomUUID() + "@agents.example";
    MvcResult created =
        postJson(
                "/api/agents",
                managerToken(),
                new AgentCreateRequest(
                    "Fresh Agent " + UUID.randomUUID(),
                    Country.UNITED_STATES,
                    new BigDecimal("2000.00"),
                    username))
            .andExpect(status().isCreated())
            .andReturn();
    String password =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("password").asText();
    String token = loginAs(username, password);
    return new RoleLogin(username, password, token);
  }

  private record RoleLogin(String username, String password, String token) {}
}
