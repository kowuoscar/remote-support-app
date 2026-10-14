package com.remotesupport.backend.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * The coded 400 body of a refused Fleet correction (correct-a-fleet-unit spec), shared by the
 * Smartphone and SIM Card controllers' {@code @ExceptionHandler}s. A request DTO's Bean Validation
 * constraint names the code as its {@code message}; {@link #MESSAGES} gives that code its text.
 */
final class CorrectionErrors {

  private static final Map<String, String> MESSAGES =
      Map.of(
          "NUMBER_BLANK", "Enter a number.",
          "MODEL_BLANK", "Enter a model.",
          "OWNER_INVALID", "Choose Client or Company.",
          "PLAN_NOT_ON_CARRIER", "Choose a plan of this SIM's Carrier.");

  private CorrectionErrors() {}

  static ResponseEntity<Map<String, String>> badRequest(InvalidCorrectionException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("code", e.getCode(), "message", e.getMessage()));
  }

  /**
   * The coded body for a correction DTO's failed constraint. The controllers' other {@code @Valid}
   * endpoints share the handler; their failures keep the framework's default 400 body.
   */
  static ResponseEntity<?> badRequest(MethodArgumentNotValidException e) {
    FieldError first = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
    String code = first == null ? null : first.getDefaultMessage();
    if (code == null || !MESSAGES.containsKey(code)) {
      return ResponseEntity.status(e.getStatusCode()).body(e.getBody());
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("code", code, "message", MESSAGES.get(code)));
  }
}
