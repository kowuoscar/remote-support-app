package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.dto.AgentCreateRequest;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Door 3 of deactivate-a-login, tested directly: the filter (door 2) stops a deactivated caller
 * before the endpoint, so HTTP can never reach {@link ChangePasswordService}'s own refusal. This
 * is the narrow Spring-context test the spec asks for, calling the service with a deactivated
 * {@link User}.
 */
class ChangePasswordServiceDeactivatedTest extends IntegrationTest {

  private static final String NEW_PASSWORD = "BrandNewPassw0rd!";

  @Autowired private ChangePasswordService changePasswordService;
  @Autowired private UserRepository userRepository;

  @Test
  void deactivatedCallerIsRefusedBeforeVerification() throws Exception {
    Login login = freshAgentLogin();
    deactivate(login.user());

    // A wrong current password would be refused too, but with ChangePasswordRefusedException (400);
    // BadCredentialsException proves the deactivation check ran first.
    assertThatThrownBy(
            () ->
                changePasswordService.changeOwnPassword(
                    principalOf(login.user()), "definitely-the-wrong-password", NEW_PASSWORD))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void passwordUnchangedAfterRefusalAndReactivatedSignInStillWorks() throws Exception {
    Login login = freshAgentLogin();
    deactivate(login.user());

    assertThatThrownBy(
            () ->
                changePasswordService.changeOwnPassword(
                    principalOf(login.user()), login.password(), NEW_PASSWORD))
        .isInstanceOf(BadCredentialsException.class);

    login.user().reactivate();
    userRepository.saveAndFlush(login.user());

    loginAs(login.user().getUsername(), login.password());
  }

  private void deactivate(User user) {
    user.deactivate(Instant.now());
    userRepository.saveAndFlush(user);
  }

  private AuthenticatedPrincipal principalOf(User user) {
    return new AuthenticatedPrincipal(
        user.getId(), user.getUsername(), user.getTenant().getId(), user.getRole().name());
  }

  private Login freshAgentLogin() throws Exception {
    String username = "deact-service-" + UUID.randomUUID() + "@agents.example";
    MvcResult created =
        postJson(
                "/api/agents",
                managerToken(),
                new AgentCreateRequest(
                    "Service Agent " + UUID.randomUUID(),
                    Country.UNITED_STATES,
                    new BigDecimal("2000.00"),
                    username))
            .andExpect(status().isCreated())
            .andReturn();
    String password =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("password").asText();
    return new Login(userRepository.findByUsername(username).orElseThrow(), password);
  }

  private record Login(User user, String password) {}
}
