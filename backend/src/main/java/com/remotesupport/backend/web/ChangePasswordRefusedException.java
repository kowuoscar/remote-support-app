package com.remotesupport.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A refused {@code POST /api/me/password} (change-own-password-endpoint ticket). Always maps to
 * {@code 400}, never {@code 401}/{@code 403} — load-bearing per spec.md Constraints: the
 * frontend's session layer reads {@code 401} as an expired session and would route a caller who
 * merely mistyped a field to the sign-in page, discarding whatever else they had typed. {@link
 * ChangePasswordController}'s own {@code @ExceptionHandler} renders {@code reason()} as the body's
 * {@code code}, so the form can tell a wrong current password apart from a no-op new password.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ChangePasswordRefusedException extends RuntimeException {

  /** The machine-readable cause, sent to the client as {@code code}. */
  public enum Reason {
    WRONG_CURRENT_PASSWORD,
    PASSWORD_UNCHANGED
  }

  private final Reason reason;

  public ChangePasswordRefusedException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
