package com.remotesupport.backend.web;

import com.remotesupport.backend.dto.ClientInvoiceSentBackResponse;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.security.CallerIdentityResolver;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Client Invoices sent back to the calling Agent (send-a-client-invoice-back spec, "The
 * sent-back list"): the Agent's own Contracts' invoices that are {@code DRAFT} with a send-back,
 * oldest first. Agent-only at the matcher level (SecurityConfig). A literal path, so Spring never
 * reads {@code sent-back} as the {@code {invoiceId}} of {@link ClientInvoiceByIdController}.
 */
@RestController
public class ClientInvoiceSentBackController {

  private final ClientInvoiceRepository clientInvoiceRepository;
  private final CallerIdentityResolver callerIdentityResolver;

  public ClientInvoiceSentBackController(
      ClientInvoiceRepository clientInvoiceRepository, CallerIdentityResolver callerIdentityResolver) {
    this.clientInvoiceRepository = clientInvoiceRepository;
    this.callerIdentityResolver = callerIdentityResolver;
  }

  @GetMapping("/api/client-invoices/sent-back")
  public List<ClientInvoiceSentBackResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return callerIdentityResolver
        .resolveAgentId(principal)
        .map(agentId -> clientInvoiceRepository.findSentBackRows(principal.tenantId(), agentId))
        .orElse(List.of())
        .stream()
        .map(
            row ->
                new ClientInvoiceSentBackResponse(
                    row.id(),
                    row.contractId(),
                    row.clientName(),
                    row.country().name(),
                    row.billingMonth(),
                    row.currency().name(),
                    row.sentBackAt(),
                    row.sentBackReason()))
        .toList();
  }
}
