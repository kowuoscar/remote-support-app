package com.remotesupport.backend.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Renders a {@link ClientInvoiceConflictException} as {@code {code, message}} on every Client
 * Invoice route, the by-id and the current-month one alike; a plain {@link ConflictException}
 * body carries no {@code code}.
 */
@RestControllerAdvice(assignableTypes = {ClientInvoiceByIdController.class, ClientInvoiceController.class})
public class ClientInvoiceConflictAdvice {

  @ExceptionHandler(ClientInvoiceConflictException.class)
  public ResponseEntity<Map<String, String>> clientInvoiceConflict(ClientInvoiceConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", e.reason().name(), "message", e.getMessage()));
  }

  /** A refused request names what was wrong in {@code message}, which the form shows no more than it needs to. */
  @ExceptionHandler(InvalidRequestException.class)
  public ResponseEntity<Map<String, String>> invalidRequest(InvalidRequestException e) {
    return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
  }
}
