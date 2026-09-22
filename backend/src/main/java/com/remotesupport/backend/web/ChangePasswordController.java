package com.remotesupport.backend.web;

import com.remotesupport.backend.dto.ChangePasswordRequest;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.web.ChangePasswordRefusedException.Reason;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Changes the signed-in caller's own password (change-own-password-endpoint ticket) — a sibling
 * of {@link MeController}, not a method added to it: {@link MeController} is documented as the
 * reference *read-only* protected endpoint, and this write path needs its own {@code
 * @ExceptionHandler} to emit a machine-readable {@code code} (coding standards Backend rule 7),
 * which {@link MeController} has no other use for (spec.md Solution). No id in the path or the
 * body — the row this writes is the caller's own, resolved from the JWT alone — and no {@code
 * SecurityConfig} matcher is added: {@code /api/me/**} already falls through to {@code
 * .anyRequest().authenticated()}, which is exactly the intended access for an endpoint every role
 * may call on itself only.
 */
@RestController
@RequestMapping("/api/me")
public class ChangePasswordController {

  private final ChangePasswordService changePasswordService;

  public ChangePasswordController(ChangePasswordService changePasswordService) {
    this.changePasswordService = changePasswordService;
  }

  /** {@code 204} with no body on success — there is nothing to tell the caller it doesn't already
   * know, and a response body around a password change is a place for a secret to leak into. */
  @PostMapping("/password")
  public ResponseEntity<Void> changePassword(
      @Valid @RequestBody ChangePasswordRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    changePasswordService.changeOwnPassword(
        principal, request.currentPassword(), request.newPassword());
    return ResponseEntity.noContent().build();
  }

  /**
   * Every refusal here is {@code 400}, never {@code 401}/{@code 403} (spec.md Constraints): a
   * wrong current password would otherwise bounce the caller to sign-in and discard whatever else
   * they had typed in the form.
   */
  @ExceptionHandler(ChangePasswordRefusedException.class)
  public ResponseEntity<Map<String, String>> changePasswordRefused(ChangePasswordRefusedException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("code", e.reason().name(), "message", e.getMessage()));
  }

  /**
   * A {@code newPassword} shorter than {@link com.remotesupport.backend.dto.PasswordPolicy#MIN_LENGTH}
   * fails its {@code @Size} constraint before this controller's method body ever runs
   * (password-minimum-length ticket); this is what gives that refusal its own {@code
   * PASSWORD_TOO_SHORT} code, distinct from {@code WRONG_CURRENT_PASSWORD} and {@code
   * PASSWORD_UNCHANGED}, so the change-password dialog this ticket unblocks can point at the
   * new-password field specifically rather than treating every refusal alike.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> requestBodyInvalid(MethodArgumentNotValidException e) {
    boolean newPasswordTooShort =
        e.getBindingResult().getFieldErrors().stream()
            .anyMatch(fieldError -> "newPassword".equals(fieldError.getField()));
    String code = newPasswordTooShort ? Reason.PASSWORD_TOO_SHORT.name() : "INVALID_REQUEST";
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse("The request is invalid");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("code", code, "message", message));
  }
}
