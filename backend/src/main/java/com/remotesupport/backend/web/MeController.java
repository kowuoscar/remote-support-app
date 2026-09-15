package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.dto.MeResponse;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.UUID;
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

  private final UserRepository userRepository;
  private final TesterRepository testerRepository;

  public MeController(UserRepository userRepository, TesterRepository testerRepository) {
    this.userRepository = userRepository;
    this.testerRepository = testerRepository;
  }

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    UUID agentId =
        userRepository
            .findById(principal.userId())
            .map(User::getAgent)
            .map(Agent::getId)
            .orElse(null);
    UUID clientId =
        testerRepository
            .findByUserId(principal.userId())
            .map(tester -> tester.getClient().getId())
            .orElse(null);

    return new MeResponse(
        principal.userId(), principal.username(), principal.tenantId(), principal.role(), agentId, clientId);
  }
}
