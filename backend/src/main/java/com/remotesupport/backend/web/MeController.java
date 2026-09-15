package com.remotesupport.backend.web;

import com.remotesupport.backend.dto.MeResponse;
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

  public MeController(CallerIdentityResolver callerIdentityResolver) {
    this.callerIdentityResolver = callerIdentityResolver;
  }

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return new MeResponse(
        principal.userId(),
        principal.username(),
        principal.tenantId(),
        principal.role(),
        callerIdentityResolver.resolveAgentId(principal).orElse(null),
        callerIdentityResolver.resolveClientId(principal).orElse(null));
  }
}
