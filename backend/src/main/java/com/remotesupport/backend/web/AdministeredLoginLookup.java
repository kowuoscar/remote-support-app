package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.Client;
import com.remotesupport.backend.domain.Tester;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.ClientRepository;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.security.LoginAdministrationGuard;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves the Login a Manager is administering: the Agent, or the Client's Tester, within the
 * caller's Tenant, then asks the {@link LoginAdministrationGuard}. The one place that turns route
 * ids into a target {@link User}; callers act on the returned Login.
 */
@Component
public class AdministeredLoginLookup {

  private final AgentRepository agentRepository;
  private final ClientRepository clientRepository;
  private final TesterRepository testerRepository;
  private final UserRepository userRepository;
  private final LoginAdministrationGuard guard;

  public AdministeredLoginLookup(
      AgentRepository agentRepository,
      ClientRepository clientRepository,
      TesterRepository testerRepository,
      UserRepository userRepository,
      LoginAdministrationGuard guard) {
    this.agentRepository = agentRepository;
    this.clientRepository = clientRepository;
    this.testerRepository = testerRepository;
    this.userRepository = userRepository;
    this.guard = guard;
  }

  /**
   * 404 ({@link NotFoundException}) for an unknown or other-Tenant Agent, 409 ({@link
   * AgentHasNoLoginException}) for an Agent without a Login, 403 when the guard refuses.
   */
  public User forAgent(UUID agentId, AuthenticatedPrincipal caller) {
    Agent agent =
        agentRepository
            .findByIdAndTenantId(agentId, caller.tenantId())
            .orElseThrow(() -> new NotFoundException("No agent with id " + agentId));
    User login =
        userRepository
            .findByAgentId(agent.getId())
            .orElseThrow(
                () -> new AgentHasNoLoginException("Agent " + agent.getId() + " has no login"));
    guard.requireCanAdminister(login, caller);
    return login;
  }

  /**
   * 404 ({@link NotFoundException}) for an unknown Client or Tester, or a Client in another Tenant,
   * 403 when the guard refuses. A Tester always has a Login, so there is no 409.
   */
  public User forTester(UUID clientId, UUID testerId, AuthenticatedPrincipal caller) {
    Client client =
        clientRepository
            .findByIdAndTenantId(clientId, caller.tenantId())
            .orElseThrow(() -> new NotFoundException("No client with id " + clientId));
    Tester tester =
        testerRepository
            .findByIdAndClientId(testerId, client.getId())
            .orElseThrow(() -> new NotFoundException("No tester with id " + testerId));
    User login = tester.getUser();
    guard.requireCanAdminister(login, caller);
    return login;
  }
}
