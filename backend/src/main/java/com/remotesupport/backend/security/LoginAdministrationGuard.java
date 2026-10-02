package com.remotesupport.backend.security;

import com.remotesupport.backend.domain.Role;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Which Logins a Manager may administer (manager-resets-a-password spec, "The permission rule"):
 * the caller is a Manager, the target Login is in the caller's Tenant, and the target's role is
 * {@code AGENT} or {@code TESTER}. A Manager's own Login is refused by design (a SuperAdmin
 * concern). Modelled on {@link FleetAccessGuard}: refusal is Spring Security's {@link
 * AccessDeniedException}, so it is a 403 like every other. Callers resolve the target within the
 * caller's Tenant first (unknown or other-Tenant is 404); this 403 is defence in depth.
 */
@Component
public class LoginAdministrationGuard {

  public void requireCanAdminister(User target, AuthenticatedPrincipal caller) {
    if (!"MANAGER".equals(caller.role())) {
      throw new AccessDeniedException("Only a Manager may administer a Login");
    }
    if (!target.getTenant().getId().equals(caller.tenantId())) {
      throw new AccessDeniedException("Not a Login in your Tenant");
    }
    if (target.getRole() != Role.AGENT && target.getRole() != Role.TESTER) {
      throw new AccessDeniedException("Not a Login a Manager may administer");
    }
  }
}
