package com.remotesupport.backend.web;

import com.remotesupport.backend.dto.LoginRequest;
import com.remotesupport.backend.dto.LoginResponse;
import com.remotesupport.backend.security.AppUserPrincipal;
import com.remotesupport.backend.security.JwtService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Issues JWTs for valid username/password pairs. The only unauthenticated write endpoint. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private static final Logger log = LoggerFactory.getLogger(AuthController.class);

  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;

  public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
    this.authenticationManager = authenticationManager;
    this.jwtService = jwtService;
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    Authentication authentication;
    try {
      authentication =
          authenticationManager.authenticate(
              new UsernamePasswordAuthenticationToken(request.username(), request.password()));
    } catch (BadCredentialsException e) {
      log.warn("login failed username={}", request.username());
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
    String token =
        jwtService.issueToken(
            principal.userId(), principal.getUsername(), principal.tenantId(), principal.role());
    log.info(
        "login success tenantId={} userId={} role={} username={}",
        principal.tenantId(),
        principal.userId(),
        principal.role(),
        principal.getUsername());
    return ResponseEntity.ok(new LoginResponse(token, principal.role()));
  }
}
