package com.remotesupport.backend.web;

import com.remotesupport.backend.dto.LoginActivationResponse;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager-only Login deactivation and reactivation (deactivate-a-login spec, "Endpoints"). The
 * Manager addresses the Agent, or the Tester under its Client, never a {@code User} id; no body. {@code SecurityConfig}'s
 * Manager-only {@code /api/agents/**} and {@code /api/clients/**} matchers already cover the routes. Has its own
 * {@code @ExceptionHandler} for the coded 409, as {@link PasswordResetController} does.
 */
@RestController
public class LoginActivationController {

  private final LoginActivationService loginActivationService;

  public LoginActivationController(LoginActivationService loginActivationService) {
    this.loginActivationService = loginActivationService;
  }

  @PostMapping("/api/agents/{agentId}/login/deactivate")
  public LoginActivationResponse deactivateAgentLogin(
      @PathVariable UUID agentId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return loginActivationService.deactivateAgentLogin(agentId, principal);
  }

  @PostMapping("/api/agents/{agentId}/login/reactivate")
  public LoginActivationResponse reactivateAgentLogin(
      @PathVariable UUID agentId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return loginActivationService.reactivateAgentLogin(agentId, principal);
  }

  @PostMapping("/api/clients/{clientId}/testers/{testerId}/deactivate")
  public LoginActivationResponse deactivateTesterLogin(
      @PathVariable UUID clientId,
      @PathVariable UUID testerId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return loginActivationService.deactivateTesterLogin(clientId, testerId, principal);
  }

  @PostMapping("/api/clients/{clientId}/testers/{testerId}/reactivate")
  public LoginActivationResponse reactivateTesterLogin(
      @PathVariable UUID clientId,
      @PathVariable UUID testerId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return loginActivationService.reactivateTesterLogin(clientId, testerId, principal);
  }

  @ExceptionHandler(AgentHasNoLoginException.class)
  public ResponseEntity<Map<String, String>> agentHasNoLogin(AgentHasNoLoginException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", "AGENT_HAS_NO_LOGIN", "message", e.getMessage()));
  }
}
