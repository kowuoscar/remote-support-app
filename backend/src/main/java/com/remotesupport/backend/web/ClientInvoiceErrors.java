package com.remotesupport.backend.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The response bodies of a refused Client Invoice request, shared by the two Client Invoice
 * controllers' {@code @ExceptionHandler}s (coding standards Backend 7): a plain {@link
 * ConflictException} or {@link InvalidRequestException} body carries no {@code message} here.
 */
final class ClientInvoiceErrors {

  private ClientInvoiceErrors() {}

  /**
   * The coded {@code 409}: {@code {code, message}}, plus {@code invoiceIds} when the refusal names
   * invoices (the month send's {@code CARRIER_INVOICES_MISSING}); every other body stays two keys.
   */
  static ResponseEntity<Map<String, Object>> conflict(ClientInvoiceConflictException e) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", e.reason().name());
    body.put("message", e.getMessage());
    if (e.invoiceIds() != null) {
      body.put("invoiceIds", e.invoiceIds());
    }
    return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
  }

  /** The {@code 410} of a deleted Carrier Invoice File: {@code {code, message}}. */
  static ResponseEntity<Map<String, String>> gone(CarrierInvoiceFileDeletedException e) {
    return ResponseEntity.status(HttpStatus.GONE)
        .body(Map.of("code", CarrierInvoiceFileDeletedException.CODE, "message", e.getMessage()));
  }

  /** A refused request names what was wrong in {@code message}. */
  static ResponseEntity<Map<String, String>> invalid(InvalidRequestException e) {
    return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
  }
}
