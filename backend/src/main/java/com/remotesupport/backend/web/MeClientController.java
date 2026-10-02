package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Client;
import com.remotesupport.backend.dto.ClientInvoiceSummaryResponse;
import com.remotesupport.backend.dto.MeClientResponse;
import com.remotesupport.backend.repository.ClientRepository;
import com.remotesupport.backend.security.CallerIdentityResolver;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The calling Tester's own Client and the latest sent or approved Client Invoice of each of its
 * Contracts ({@code GET /api/me/client[/latest-client-invoices]}). Takes no id: the Client is
 * resolved from the principal, so one Tester cannot address another Client. Read-only: nothing
 * here creates a Client Invoice.
 */
@RestController
@RequestMapping("/api/me/client")
public class MeClientController {

  private final CallerIdentityResolver callerIdentityResolver;
  private final ClientRepository clientRepository;
  private final ClientInvoiceService clientInvoiceService;

  public MeClientController(
      CallerIdentityResolver callerIdentityResolver,
      ClientRepository clientRepository,
      ClientInvoiceService clientInvoiceService) {
    this.callerIdentityResolver = callerIdentityResolver;
    this.clientRepository = clientRepository;
    this.clientInvoiceService = clientInvoiceService;
  }

  @GetMapping
  public MeClientResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return MeClientResponse.of(callersClient(principal));
  }

  @GetMapping("/latest-client-invoices")
  public List<ClientInvoiceSummaryResponse> latestClientInvoices(
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return clientInvoiceService.latestSentOrApproved(callersClient(principal), principal.tenantId());
  }

  private Client callersClient(AuthenticatedPrincipal principal) {
    return callerIdentityResolver
        .resolveClientId(principal)
        .flatMap(id -> clientRepository.findByIdAndTenantId(id, principal.tenantId()))
        .orElseThrow(() -> new NotFoundException("The caller is not linked to a Client"));
  }
}
