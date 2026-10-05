package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.dto.CarrierInvoiceFileResponse;
import com.remotesupport.backend.dto.ClientInvoiceLineEditRequest;
import com.remotesupport.backend.dto.ClientInvoiceResponse;
import com.remotesupport.backend.dto.ClientInvoiceSendBackRequest;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.security.ClientInvoiceAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * A Client Invoice addressed by its own id (manager-invoice-review-queue spec, "invoice by id"; the
 * Agent's half is send-a-client-invoice-back, "the Agent reaches an invoice by its id"): for an
 * invoice of any billing month, the Manager's and the Contract's own Agent's read and Carrier
 * Invoice Files, the Agent's attach, line edit and send, and the Manager's PDF, approve and
 * send-back.
 *
 * <p>Unlike {@link ClientInvoiceController}, nothing here ever gets-or-creates: it only finds an
 * invoice that already exists. The lookup is scoped to the caller's tenant, so an unknown id and
 * another tenant's id are the same 404 and existence never leaks; only then does {@link
 * ClientInvoiceAccessGuard} check the caller against the invoice's Contract (another Agent's is a
 * 403). Which roles reach which route is SecurityConfig's. What happens once the invoice is found
 * is shared with the current-month routes through {@link ClientInvoiceService}, which also refuses
 * every write on a draft not open to its Agent.
 */
@RestController
@RequestMapping("/api/client-invoices/{invoiceId}")
public class ClientInvoiceByIdController {

  private final ClientInvoiceRepository clientInvoiceRepository;
  private final ClientInvoiceAccessGuard clientInvoiceAccessGuard;
  private final ClientInvoiceService clientInvoiceService;

  public ClientInvoiceByIdController(
      ClientInvoiceRepository clientInvoiceRepository,
      ClientInvoiceAccessGuard clientInvoiceAccessGuard,
      ClientInvoiceService clientInvoiceService) {
    this.clientInvoiceRepository = clientInvoiceRepository;
    this.clientInvoiceAccessGuard = clientInvoiceAccessGuard;
    this.clientInvoiceService = clientInvoiceService;
  }

  @GetMapping
  public ClientInvoiceResponse get(
      @PathVariable UUID invoiceId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return clientInvoiceService.toResponse(findForView(invoiceId, principal), true);
  }

  /** The Agent's resend, or first send, by id: the same send the current-month route calls. */
  @PostMapping("/send")
  public ClientInvoiceResponse send(
      @PathVariable UUID invoiceId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    ClientInvoice invoice = find(invoiceId, principal);
    clientInvoiceAccessGuard.requireCanSend(invoice.getContract(), principal);
    return clientInvoiceService.send(invoice, principal);
  }

  /** The Agent's line edit by id: the body, responses and {@code editLine} of the current-month route. */
  @PutMapping("/lines")
  public ClientInvoiceResponse editLine(
      @PathVariable UUID invoiceId,
      @Valid @RequestBody ClientInvoiceLineEditRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    ClientInvoice invoice = find(invoiceId, principal);
    clientInvoiceAccessGuard.requireCanSend(invoice.getContract(), principal);
    return clientInvoiceService.editLine(invoice, request.kind(), request.sourceId(), request.amount(), principal);
  }

  @PostMapping("/approve")
  public ClientInvoiceResponse approve(
      @PathVariable UUID invoiceId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return clientInvoiceService.approve(find(invoiceId, principal), principal);
  }

  @PostMapping("/send-back")
  public ClientInvoiceResponse sendBack(
      @PathVariable UUID invoiceId,
      @Valid @RequestBody ClientInvoiceSendBackRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return clientInvoiceService.sendBack(find(invoiceId, principal), request.reason(), principal);
  }

  @GetMapping("/pdf")
  public ResponseEntity<byte[]> pdf(
      @PathVariable UUID invoiceId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return clientInvoiceService.pdf(find(invoiceId, principal));
  }

  @PostMapping("/files")
  public ResponseEntity<CarrierInvoiceFileResponse> uploadFile(
      @PathVariable UUID invoiceId,
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    ClientInvoice invoice = find(invoiceId, principal);
    clientInvoiceAccessGuard.requireCanBuildOrView(invoice.getContract(), principal);
    return clientInvoiceService.attachFile(invoice, file, principal);
  }

  @GetMapping("/files")
  public List<CarrierInvoiceFileResponse> listFiles(
      @PathVariable UUID invoiceId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return clientInvoiceService.files(findForView(invoiceId, principal));
  }

  @GetMapping("/files/{fileId}")
  public ResponseEntity<byte[]> downloadFile(
      @PathVariable UUID invoiceId,
      @PathVariable UUID fileId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return clientInvoiceService.downloadFile(findForView(invoiceId, principal), fileId);
  }

  /** A refused body (a line edit's amount, a send-back's reason) is a {@code 400} whose {@code message} names the first thing wrong. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> requestInvalid(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .findFirst()
            .orElse("The request is invalid");
    return ResponseEntity.badRequest().body(Map.of("message", message));
  }

  /** The {@code 409} of a draft not open to its Agent carries a {@code code}, which a plain {@link ConflictException} body does not. */
  @ExceptionHandler(ClientInvoiceConflictException.class)
  public ResponseEntity<Map<String, String>> clientInvoiceConflict(ClientInvoiceConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", e.reason().name(), "message", e.getMessage()));
  }

  private ClientInvoice findForView(UUID invoiceId, AuthenticatedPrincipal principal) {
    ClientInvoice invoice = find(invoiceId, principal);
    clientInvoiceAccessGuard.requireCanView(invoice.getContract(), invoice.getStatus(), principal);
    return invoice;
  }

  private ClientInvoice find(UUID invoiceId, AuthenticatedPrincipal principal) {
    return clientInvoiceRepository
        .findByIdAndTenantId(invoiceId, principal.tenantId())
        .orElseThrow(() -> new NotFoundException("No Client Invoice with id " + invoiceId));
  }
}
