package com.remotesupport.backend.web;

import com.remotesupport.backend.dto.ClientInvoiceMonthSendRequest;
import com.remotesupport.backend.dto.ClientInvoiceMonthSendResponse;
import com.remotesupport.backend.security.CallerIdentityResolver;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The calling Agent's month of Client Invoices sent at once (agent-sends-the-month spec, "HTTP
 * contract"). Agent-only at the matcher level (SecurityConfig, before the general {@code /api/me}
 * fall-through); an Agent login with no Agent is a 403. What the send does, and its coded 409s, are
 * {@link ClientInvoiceMonthService}'s.
 */
@RestController
public class ClientInvoiceMonthController {

  private final ClientInvoiceMonthService clientInvoiceMonthService;
  private final CallerIdentityResolver callerIdentityResolver;

  public ClientInvoiceMonthController(
      ClientInvoiceMonthService clientInvoiceMonthService, CallerIdentityResolver callerIdentityResolver) {
    this.clientInvoiceMonthService = clientInvoiceMonthService;
    this.callerIdentityResolver = callerIdentityResolver;
  }

  /** {@code month} is {@code YYYY-MM}; the optional body names the invoices the Agent saw. */
  @PostMapping("/api/me/client-invoice-months/{month}/send")
  public ClientInvoiceMonthSendResponse send(
      @PathVariable String month,
      @Valid @RequestBody(required = false) ClientInvoiceMonthSendRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    UUID agentId =
        callerIdentityResolver
            .resolveAgentId(principal)
            .orElseThrow(() -> new AccessDeniedException("Not an Agent"));
    Optional<Set<UUID>> expected =
        request == null || request.invoiceIds() == null
            ? Optional.empty()
            : Optional.of(new HashSet<>(request.invoiceIds()));
    return clientInvoiceMonthService.send(agentId, parse(month), expected, principal);
  }

  private static LocalDate parse(String month) {
    try {
      return YearMonth.parse(month).atDay(1);
    } catch (DateTimeParseException e) {
      throw new InvalidRequestException("month must be YYYY-MM");
    }
  }

  /** A refused body is a {@code 400} whose {@code message} names the first thing wrong. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> requestInvalid(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .findFirst()
            .orElse("The request is invalid");
    return ResponseEntity.badRequest().body(Map.of("message", message));
  }

  /** The coded {@code 409}s of the month send; {@code CARRIER_INVOICES_MISSING} also carries {@code invoiceIds}. */
  @ExceptionHandler(ClientInvoiceConflictException.class)
  public ResponseEntity<Map<String, Object>> clientInvoiceConflict(ClientInvoiceConflictException e) {
    return ClientInvoiceErrors.conflict(e);
  }

  /** A malformed month names what was wrong in {@code message}. */
  @ExceptionHandler(InvalidRequestException.class)
  public ResponseEntity<Map<String, String>> invalidRequest(InvalidRequestException e) {
    return ClientInvoiceErrors.invalid(e);
  }
}
