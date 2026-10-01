package com.remotesupport.backend.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

/**
 * The permission rule's branches no HTTP route can reach today: a Manager-role target, and a target
 * in another Tenant (spec "The permission rule"; Testing decisions).
 */
class AgentPasswordResetGuardTest extends IntegrationTest {

  @Autowired private LoginAdministrationGuard guard;
  @Autowired private UserRepository userRepository;

  @Test
  void aManagerRoleTargetIsRefused() {
    User manager = userRepository.findByUsername(MANAGER_USERNAME).orElseThrow();

    assertThatThrownBy(() -> guard.requireCanAdminister(manager, principalOf(manager)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void aTargetInAnotherTenantIsRefused() {
    User manager = userRepository.findByUsername(MANAGER_USERNAME).orElseThrow();
    User agent = userRepository.findByUsername(AGENT_USERNAME).orElseThrow();
    AuthenticatedPrincipal foreignManager =
        new AuthenticatedPrincipal(
            manager.getId(), manager.getUsername(), UUID.randomUUID(), "MANAGER");

    assertThatThrownBy(() -> guard.requireCanAdminister(agent, foreignManager))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void aNonManagerCallerIsRefused() {
    User agent = userRepository.findByUsername(AGENT_USERNAME).orElseThrow();

    assertThatThrownBy(() -> guard.requireCanAdminister(agent, principalOf(agent)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void aManagerMayAdministerAnAgentInTheirTenant() {
    User manager = userRepository.findByUsername(MANAGER_USERNAME).orElseThrow();
    User agent = userRepository.findByUsername(AGENT_USERNAME).orElseThrow();

    assertThatCode(() -> guard.requireCanAdminister(agent, principalOf(manager)))
        .doesNotThrowAnyException();
  }

  private static AuthenticatedPrincipal principalOf(User user) {
    return new AuthenticatedPrincipal(
        user.getId(), user.getUsername(), user.getTenant().getId(), user.getRole().name());
  }
}
