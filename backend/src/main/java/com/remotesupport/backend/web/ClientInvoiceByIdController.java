package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.dto.CarrierInvoiceFileResponse;
import com.remotesupport.backend.dto.ClientInvoiceResponse;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A Client Invoice addressed by its own id (manager-invoice-review-queue spec, "invoice by id"):
 * the Manager's read, Carrier Invoice Files, PDF and approve, for an invoice of any billing month.
 *
 * <p>Unlike {@link ClientInvoiceController}, nothing here ever gets-or-creates: it only finds an
 * invoice that already exists. The lookup is scoped to the caller's tenant, so an unknown id and
 * another tenant's id are the same 404 and existence never leaks. Manager-only at the matcher
 * level (SecurityConfig). What happens once the invoice is found is shared with the current-month
 * routes through {@link ClientInvoiceService}.
 */
@RestController
@RequestMapping("/api/client-invoices/{invoiceId}")
public class ClientInvoiceByIdController {

  private final ClientInvoiceRepository clientInvoiceRepository;
  private final ClientInvoiceService clientInvoiceService;

  public ClientInvoiceByIdController(
      ClientInvoiceRepository clientInvoiceRepository, ClientInvoiceService clientInvoiceService) {
    this.clientInvoiceRepository = clientInvoiceRepository;
    this.clientInvoiceService = clientInvoiceService;
  }

  @GetMapping
  public ClientInvoiceResponse get(
      @PathVariable UUID invoiceId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return clientInvoiceService.toResponse(find(invoiceId, principal));
  }

  @PostMapping("/approve")
  public ClientInvoiceResponse approve(
      @PathVariable UUID invoiceId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return clientInvoiceService.approve(find(invoiceId, principal), principal);
  }

  @GetMapping("/pdf")
  public ResponseEntity<byte[]> pdf(
      @PathVariable UUID invoiceId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return clientInvoiceService.pdf(find(invoiceId, principal));
  }

  @GetMapping("/files")
  public List<CarrierInvoiceFileResponse> listFiles(
      @PathVariable UUID invoiceId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return clientInvoiceService.files(find(invoiceId, principal));
  }

  @GetMapping("/files/{fileId}")
  public ResponseEntity<byte[]> downloadFile(
      @PathVariable UUID invoiceId,
      @PathVariable UUID fileId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return clientInvoiceService.downloadFile(find(invoiceId, principal), fileId);
  }

  private ClientInvoice find(UUID invoiceId, AuthenticatedPrincipal principal) {
    return clientInvoiceRepository
        .findByIdAndTenantId(invoiceId, principal.tenantId())
        .orElseThrow(() -> new NotFoundException("No Client Invoice with id " + invoiceId));
  }
}
