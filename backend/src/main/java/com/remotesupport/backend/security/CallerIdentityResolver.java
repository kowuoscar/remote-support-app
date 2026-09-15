package com.remotesupport.backend.security;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves the domain entity a JWT-authenticated caller corresponds to, beyond what rides in the
 * token itself (userId/tenantId/role): an AGENT-role login's Agent id (via the nullable
 * {@code users.agent_id} link, fleet-management ticket prefactor) and a TESTER-role login's
 * Client id (via {@link com.remotesupport.backend.domain.Tester}). Looked up per request rather
 * than baked into the JWT so a Manager re-linking or unlinking either takes effect immediately.
 * Empty for a MANAGER, a not-yet-linked AGENT, or a not-yet-linked TESTER.
 */
@Component
public class CallerIdentityResolver {

  private final UserRepository userRepository;
  private final TesterRepository testerRepository;

  public CallerIdentityResolver(UserRepository userRepository, TesterRepository testerRepository) {
    this.userRepository = userRepository;
    this.testerRepository = testerRepository;
  }

  public Optional<UUID> resolveAgentId(AuthenticatedPrincipal principal) {
    return userRepository.findById(principal.userId()).map(User::getAgent).map(Agent::getId);
  }

  public Optional<UUID> resolveClientId(AuthenticatedPrincipal principal) {
    return testerRepository
        .findByUserId(principal.userId())
        .map(tester -> tester.getClient().getId());
  }
}
