package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.AgentInvoice;
import com.remotesupport.backend.dto.AgentInvoiceOverrideRequest;
import com.remotesupport.backend.dto.AgentInvoiceResponse;
import com.remotesupport.backend.repository.AgentInvoiceRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * An Agent Invoice addressed by its own id (manager-invoice-review-queue spec, "invoice by id"):
 * the Manager's read, override, approve and mark paid, for an invoice of any billing month.
 *
 * <p>Unlike {@link AgentInvoiceController}, nothing here ever gets-or-creates: it only finds an
 * invoice that already exists. The lookup is scoped to the caller's tenant, so an unknown id and
 * another tenant's id are the same 404 and existence never leaks. Manager-only at the matcher
 * level (SecurityConfig). What happens once the invoice is found is shared with the current-month
 * routes through {@link AgentInvoiceService}.
 */
@RestController
@RequestMapping("/api/agent-invoices/{invoiceId}")
public class AgentInvoiceByIdController {

  private final AgentInvoiceRepository agentInvoiceRepository;
  private final AgentInvoiceService agentInvoiceService;

  public AgentInvoiceByIdController(
      AgentInvoiceRepository agentInvoiceRepository, AgentInvoiceService agentInvoiceService) {
    this.agentInvoiceRepository = agentInvoiceRepository;
    this.agentInvoiceService = agentInvoiceService;
  }

  @GetMapping
  public AgentInvoiceResponse get(
      @PathVariable UUID invoiceId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return agentInvoiceService.toResponse(find(invoiceId, principal));
  }

  @PostMapping("/override")
  public AgentInvoiceResponse override(
      @PathVariable UUID invoiceId,
      @Valid @RequestBody AgentInvoiceOverrideRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return agentInvoiceService.override(find(invoiceId, principal), request, principal);
  }

  @PostMapping("/approve")
  public AgentInvoiceResponse approve(
      @PathVariable UUID invoiceId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return agentInvoiceService.approve(find(invoiceId, principal), principal);
  }

  @PostMapping("/paid")
  public AgentInvoiceResponse markPaid(
      @PathVariable UUID invoiceId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return agentInvoiceService.markPaid(find(invoiceId, principal), principal);
  }

  private AgentInvoice find(UUID invoiceId, AuthenticatedPrincipal principal) {
    return agentInvoiceRepository
        .findByIdAndTenantId(invoiceId, principal.tenantId())
        .orElseThrow(() -> new NotFoundException("No Agent Invoice with id " + invoiceId));
  }
}
