package com.remotesupport.backend.web;

import com.remotesupport.backend.dto.PasswordResetResponse;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager-only password resets (manager-resets-a-password spec, "Reset endpoints"). The Manager
 * addresses the Agent, never a {@code User} id; no body. {@code SecurityConfig}'s Manager-only
 * {@code /api/agents/**} matcher already covers the route. Has its own {@code @ExceptionHandler}
 * for the coded 409, as {@link ChangePasswordController} does.
 */
@RestController
public class PasswordResetController {

  private final PasswordResetService passwordResetService;

  public PasswordResetController(PasswordResetService passwordResetService) {
    this.passwordResetService = passwordResetService;
  }

  @PostMapping("/api/agents/{agentId}/login/password")
  public ResponseEntity<PasswordResetResponse> resetAgentPassword(
      @PathVariable UUID agentId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    String password = passwordResetService.resetAgentPassword(agentId, principal);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new PasswordResetResponse(password));
  }

  @ExceptionHandler(AgentHasNoLoginException.class)
  public ResponseEntity<Map<String, String>> agentHasNoLogin(AgentHasNoLoginException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", "AGENT_HAS_NO_LOGIN", "message", e.getMessage()));
  }
}
