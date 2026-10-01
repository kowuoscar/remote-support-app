package com.remotesupport.backend.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.support.IntegrationTest;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

/**
 * Which Logins a Manager may administer (spec "The permission rule"; Testing decisions). The table
 * includes the branches no HTTP route can reach today: a Manager-role target, and a target in
 * another Tenant.
 */
class LoginAdministrationGuardTest extends IntegrationTest {

  /** Whose principal is calling: a seeded user's, or that Manager's in a Tenant that is not theirs. */
  private enum Caller {
    MANAGER,
    MANAGER_OF_ANOTHER_TENANT,
    AGENT
  }

  @Autowired private LoginAdministrationGuard guard;
  @Autowired private UserRepository userRepository;

  static Stream<Arguments> cases() {
    return Stream.of(
        Arguments.of(Named.of("a Manager may administer an Agent", AGENT_USERNAME), Caller.MANAGER, false),
        Arguments.of(Named.of("a Manager may administer a Tester", TESTER_USERNAME), Caller.MANAGER, false),
        Arguments.of(Named.of("a Manager-role target is refused", MANAGER_USERNAME), Caller.MANAGER, true),
        Arguments.of(
            Named.of("a target in another Tenant is refused", AGENT_USERNAME),
            Caller.MANAGER_OF_ANOTHER_TENANT,
            true),
        Arguments.of(Named.of("a non-Manager caller is refused", AGENT_USERNAME), Caller.AGENT, true));
  }

  @ParameterizedTest
  @MethodSource("cases")
  void requireCanAdminister(String targetUsername, Caller caller, boolean refused) {
    User target = userRepository.findByUsername(targetUsername).orElseThrow();
    AuthenticatedPrincipal principal = principalFor(caller);

    if (refused) {
      assertThatThrownBy(() -> guard.requireCanAdminister(target, principal))
          .isInstanceOf(AccessDeniedException.class);
    } else {
      assertThatCode(() -> guard.requireCanAdminister(target, principal)).doesNotThrowAnyException();
    }
  }

  private AuthenticatedPrincipal principalFor(Caller caller) {
    User manager = userRepository.findByUsername(MANAGER_USERNAME).orElseThrow();
    return switch (caller) {
      case MANAGER -> principalOf(manager);
      case MANAGER_OF_ANOTHER_TENANT ->
          new AuthenticatedPrincipal(manager.getId(), manager.getUsername(), UUID.randomUUID(), "MANAGER");
      case AGENT -> principalOf(userRepository.findByUsername(AGENT_USERNAME).orElseThrow());
    };
  }

  private static AuthenticatedPrincipal principalOf(User user) {
    return new AuthenticatedPrincipal(
        user.getId(), user.getUsername(), user.getTenant().getId(), user.getRole().name());
  }
}
