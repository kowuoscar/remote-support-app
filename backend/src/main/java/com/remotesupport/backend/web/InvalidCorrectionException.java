package com.remotesupport.backend.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A Fleet correction's value breaks a rule (blank, too long, ...). Carries the machine-readable
 * {@code code} the page maps to a message at the field; the owning controller's
 * {@code @ExceptionHandler} turns it into {@code { "code", "message" }}.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidCorrectionException extends RuntimeException {

  private final String code;

  public InvalidCorrectionException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
