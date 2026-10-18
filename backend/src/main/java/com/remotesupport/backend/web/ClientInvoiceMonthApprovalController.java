package com.remotesupport.backend.web;

import com.remotesupport.backend.dto.ClientInvoiceMonthApproveRequest;
import com.remotesupport.backend.dto.ClientInvoiceMonthApproveResponse;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * A Manager's approval of the invoices left to review in one Agent's month (manager-reviews-by-agent
 * spec, "Month approval"). Manager-only through the {@code /api/agents/**} matcher; an unknown or
 * other-tenant Agent is a 404. What the approval does, and its coded 409s, are {@link
 * ClientInvoiceService#approveMonth}'s.
 */
@RestController
public class ClientInvoiceMonthApprovalController {

  private final ClientInvoiceService clientInvoiceService;
  private final AgentRepository agentRepository;

  public ClientInvoiceMonthApprovalController(
      ClientInvoiceService clientInvoiceService, AgentRepository agentRepository) {
    this.clientInvoiceService = clientInvoiceService;
    this.agentRepository = agentRepository;
  }

  /** {@code month} is {@code YYYY-MM}, any month; the body names the invoices the Manager saw. */
  @PostMapping("/api/agents/{agentId}/client-invoice-months/{month}/approve")
  public ClientInvoiceMonthApproveResponse approve(
      @PathVariable UUID agentId,
      @PathVariable String month,
      @Valid @RequestBody ClientInvoiceMonthApproveRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    agentRepository
        .findByIdAndTenantId(agentId, principal.tenantId())
        .orElseThrow(() -> new NotFoundException("No agent with id " + agentId));
    LocalDate billingMonth = parse(month);
    Set<UUID> expected = new HashSet<>(request.invoiceIds());
    ClientInvoiceService.MonthApproval approved =
        clientInvoiceService.approveMonth(agentId, billingMonth, expected, principal);
    return new ClientInvoiceMonthApproveResponse(billingMonth, approved.approvedAt(), approved.invoiceIds());
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

  /** The coded {@code 409}s of the month approval: {@code {code, message}}. */
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
