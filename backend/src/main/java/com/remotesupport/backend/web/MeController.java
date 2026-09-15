package com.remotesupport.backend.web;

import com.remotesupport.backend.dto.MeResponse;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The current authenticated identity. Serves as the reference protected endpoint proving JWT
 * enforcement works, and doubles as a "who am I" lookup for future clients.
 */
@RestController
@RequestMapping("/api")
public class MeController {

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return new MeResponse(
        principal.userId(), principal.username(), principal.tenantId(), principal.role());
  }
}
