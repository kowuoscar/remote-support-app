package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.ManagerName;
import com.remotesupport.backend.dto.ManagerNameRequest;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets a Manager correct their own Manager name ({@code PUT /api/me/name}), a sibling of {@link
 * ChangePasswordController} in shape: no id anywhere, the row written is the caller's own,
 * resolved from the principal. Manager-only through a {@code SecurityConfig} matcher. It changes
 * {@code users.name} and nothing else, and ends no session.
 */
@RestController
@RequestMapping("/api/me")
public class ManagerNameController {

  private final ManagerNameService managerNameService;

  public ManagerNameController(ManagerNameService managerNameService) {
    this.managerNameService = managerNameService;
  }

  @PutMapping("/name")
  public ResponseEntity<Void> setName(
      @Valid @RequestBody ManagerNameRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    managerNameService.rename(principal, request.name());
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler(ManagerName.Refused.class)
  public ResponseEntity<Map<String, String>> nameRefused(ManagerName.Refused e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("code", e.reason().name(), "message", e.getMessage()));
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  public ResponseEntity<Map<String, String>> requestInvalid() {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("code", "INVALID_REQUEST", "message", "The request is invalid"));
  }
}
