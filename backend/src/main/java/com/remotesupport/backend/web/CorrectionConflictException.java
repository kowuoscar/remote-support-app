package com.remotesupport.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A Fleet correction clashes with the unit's current state (an owner change while an open Return
 * names the Smartphone). Carries the {@code code} the page maps to a message at the field; the owning
 * controller's {@code @ExceptionHandler} turns it into {@code { "code", "message" }}.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class CorrectionConflictException extends RuntimeException {

  private final String code;

  public CorrectionConflictException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
