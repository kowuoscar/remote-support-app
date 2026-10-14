package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Role;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.dto.MeResponse;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.CallerIdentityResolver;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current authenticated identity. Serves as the reference protected endpoint proving JWT
 * enforcement works, and doubles as a "who am I" lookup for future clients — including, for an
 * AGENT or TESTER login, the Agent/Client record it resolves to (fleet-management ticket
 * prefactor), which Fleet visibility is authorized against.
 */
@RestController
@RequestMapping("/api")
public class MeController {

  private final CallerIdentityResolver callerIdentityResolver;

  private final UserRepository userRepository;

  public MeController(
      CallerIdentityResolver callerIdentityResolver, UserRepository userRepository) {
    this.callerIdentityResolver = callerIdentityResolver;
    this.userRepository = userRepository;
  }

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return new MeResponse(
        principal.userId(),
        principal.username(),
        principal.tenantId(),
        principal.role(),
        callerIdentityResolver.resolveAgentId(principal).orElse(null),
        callerIdentityResolver.resolveClientId(principal).orElse(null),
        managerName(principal));
  }

  /** The Manager name, read from the row on each call so a correction needs no new token. */
  private String managerName(AuthenticatedPrincipal principal) {
    if (!Role.MANAGER.name().equals(principal.role())) {
      return null;
    }
    return userRepository.findById(principal.userId()).map(User::getName).orElse(null);
  }
}
