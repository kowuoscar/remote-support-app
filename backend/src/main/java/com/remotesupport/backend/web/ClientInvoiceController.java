package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.CarrierInvoiceFile;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.dto.CarrierInvoiceFileResponse;
import com.remotesupport.backend.dto.ClientInvoiceLineEditRequest;
import com.remotesupport.backend.dto.ClientInvoiceResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.CarrierInvoiceFileRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.security.ClientInvoiceAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 * A Contract's Client Invoice for the current calendar month (spec.md Solution's Client Invoice
 * entity; client-invoice-generation and client-invoice-submission-and-visibility tickets, user
 * stories 22-24, 34-35, 7-8). One per Contract per month, spanning the whole {@code DRAFT ->
 * SENT -> APPROVED} lifecycle: building/attaching files ({@code DRAFT} only), sending (the
 * Contract's own Agent), and Client visibility and on-demand PDF (from {@code SENT} onward). The
 * Manager's approval is <b>not</b> here: it is addressed by the invoice's own id, in {@link
 * ClientInvoiceByIdController}, so it exists exactly once for invoices of every billing month
 * (manager-invoice-review-queue spec).
 *
 * <p><b>Get-or-create semantics (Manager/Agent only).</b> {@code GET} is deliberately
 * idempotent-with-a-side-effect for a Manager/Agent caller: the ticket's AC is "created in status
 * draft on first access", not a separate explicit create step. A <b>Tester</b> caller never
 * triggers this side effect — see {@link #resolveInvoiceForView} — so a Tester merely looking
 * never conjures a draft into existence for a Contract nobody has built one for yet. A second,
 * concurrent first-view racing to create the same month's draft is resolved by the unique {@code
 * (contract_id, billing_month)} constraint (V11 migration), not by application-level locking —
 * see {@link #createDraft}.
 *
 * <p><b>Lines stored at send.</b> A draft never sent is computed on every {@code GET} (the Fleet
 * and Fees); {@link #send} hands the invoice to {@link ClientInvoiceService#send}, which stores its
 * lines in one transaction, and every {@code GET} from {@code SENT} onward serves them (see {@link
 * ClientInvoiceService#toResponse}), so a Fee logged afterwards can never silently change a total
 * the Manager already approved or the Client was already shown. See ADR 0001, ADR 0004 and
 * CONTEXT.md's "Client Invoice" entry.
 */
@RestController
@RequestMapping("/api/contracts/{contractId}/client-invoice")
public class ClientInvoiceController {

  private final ContractRepository contractRepository;
  private final ClientInvoiceRepository clientInvoiceRepository;
  private final CarrierInvoiceFileRepository carrierInvoiceFileRepository;
  private final ClientInvoiceAccessGuard clientInvoiceAccessGuard;
  private final CarrierInvoiceFileStorage fileStorage;
  private final ClientInvoiceService clientInvoiceService;

  public ClientInvoiceController(
      ContractRepository contractRepository,
      ClientInvoiceRepository clientInvoiceRepository,
      CarrierInvoiceFileRepository carrierInvoiceFileRepository,
      ClientInvoiceAccessGuard clientInvoiceAccessGuard,
      CarrierInvoiceFileStorage fileStorage,
      ClientInvoiceService clientInvoiceService) {
    this.contractRepository = contractRepository;
    this.clientInvoiceRepository = clientInvoiceRepository;
    this.carrierInvoiceFileRepository = carrierInvoiceFileRepository;
    this.clientInvoiceAccessGuard = clientInvoiceAccessGuard;
    this.fileStorage = fileStorage;
    this.clientInvoiceService = clientInvoiceService;
  }

  @GetMapping
  public ClientInvoiceResponse get(
      @PathVariable UUID contractId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    ClientInvoice invoice = resolveInvoiceForView(contract, principal);
    return clientInvoiceService.toResponse(invoice, !"TESTER".equals(principal.role()));
  }

  /**
   * Sends this Contract's current-month draft Client Invoice (ticket AC: "Agent can send a draft
   * Client Invoice, moving it to status sent; a sent invoice is no longer editable by the
   * Agent"). The send itself is {@link ClientInvoiceService#send}: one transaction storing the
   * invoice's lines and changing its status.
   */
  @PostMapping("/send")
  public ClientInvoiceResponse send(
      @PathVariable UUID contractId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    clientInvoiceAccessGuard.requireCanSend(contract, principal);

    // get-or-create, exactly like GET (class Javadoc): an Agent who logged Fees but never
    // happened to open the draft view first can still send directly, with no separate "build the
    // draft" step to remember.
    ClientInvoice invoice = getOrCreateDraftForCurrentMonth(contract, principal);

    return clientInvoiceService.send(invoice, principal);
  }

  /**
   * The Agent's edit of one line of this Contract's current-month draft (edit-client-invoice-lines
   * spec, "Backend: editing a line"); saving a line's computed amount is the reset. Only the
   * Contract's own Agent may ({@link ClientInvoiceAccessGuard#requireCanSend}, the check the send
   * uses). The rules, and the {@code 409}/{@code 404}, are {@link ClientInvoiceService#editLine}'s.
   */
  @PutMapping("/lines")
  public ClientInvoiceResponse editLine(
      @PathVariable UUID contractId,
      @Valid @RequestBody ClientInvoiceLineEditRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    clientInvoiceAccessGuard.requireCanSend(contract, principal);

    ClientInvoice invoice = getOrCreateDraftForCurrentMonth(contract, principal);

    return clientInvoiceService.editLine(invoice, request.kind(), request.sourceId(), request.amount(), principal);
  }

  /** A refused edit body is a {@code 400} whose {@code message} names the first thing wrong. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> editRequestInvalid(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .findFirst()
            .orElse("The request is invalid");
    return ResponseEntity.badRequest().body(Map.of("message", message));
  }

  /**
   * Renders a fresh PDF of this Contract's current-month Client Invoice (ticket AC: "A Tester can
   * generate a PDF of the Client Invoice on demand; no PDF is stored at rest") — visible to
   * whoever can view the invoice itself ({@link #resolveInvoiceForView}), but only once it is no
   * longer {@code DRAFT}: a draft is still being assembled, so there is nothing final to render
   * yet.
   */
  @GetMapping("/pdf")
  public ResponseEntity<byte[]> pdf(
      @PathVariable UUID contractId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    return clientInvoiceService.pdf(resolveInvoiceForView(contract, principal));
  }

  @PostMapping("/files")
  public ResponseEntity<CarrierInvoiceFileResponse> uploadFile(
      @PathVariable UUID contractId,
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    clientInvoiceAccessGuard.requireCanBuildOrView(contract, principal);

    if (file.isEmpty()) {
      throw new InvalidRequestException("file must not be empty");
    }

    ClientInvoice invoice = getOrCreateDraftForCurrentMonth(contract, principal);
    if (invoice.getStatus() != ClientInvoiceStatus.DRAFT) {
      throw new ConflictException(
          "Cannot attach a carrier invoice file to a Client Invoice that has already been sent");
    }

    CarrierInvoiceFile carrierFile = new CarrierInvoiceFile();
    carrierFile.setId(UUID.randomUUID());
    carrierFile.setTenant(contract.getTenant());
    carrierFile.setClientInvoice(invoice);
    carrierFile.setFilename(
        file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
            ? file.getOriginalFilename()
            : "carrier-invoice");
    carrierFile.setContentType(
        file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
    carrierFile.setSizeBytes(file.getSize());
    carrierFile.setUploadedAt(Instant.now());

    String storagePath;
    try {
      storagePath = fileStorage.store(invoice.getId(), carrierFile.getId(), file);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to store carrier invoice file", e);
    }
    carrierFile.setStoragePath(storagePath);
    carrierInvoiceFileRepository.save(carrierFile);

    AuditLog.created("CarrierInvoiceFile", carrierFile.getId(), principal.userId(), principal.tenantId());

    return ResponseEntity.status(HttpStatus.CREATED).body(CarrierInvoiceFileResponse.of(carrierFile));
  }

  @GetMapping("/files")
  public List<CarrierInvoiceFileResponse> listFiles(
      @PathVariable UUID contractId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    return clientInvoiceService.files(resolveInvoiceForView(contract, principal));
  }

  @GetMapping("/files/{fileId}")
  public ResponseEntity<byte[]> downloadFile(
      @PathVariable UUID contractId,
      @PathVariable UUID fileId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    return clientInvoiceService.downloadFile(resolveInvoiceForView(contract, principal), fileId);
  }

  /**
   * Resolves the current-month Client Invoice this caller may view (ticket AC: "Once sent, every
   * Tester at that Contract's Client can view the Client Invoice read-only" / "A draft Client
   * Invoice is never visible to a Tester"). A Manager/Agent always gets one — creating this
   * month's draft on first access exactly as before (see the class Javadoc). A <b>Tester</b> never
   * creates anything by looking: it looks up whatever already exists (or {@code null}, treated
   * identically to a {@code DRAFT} for visibility purposes) and lets {@link
   * ClientInvoiceAccessGuard#requireCanView} reject it — so a Tester peeking at a Contract with no
   * invoice yet this month gets the exact same 403 as one peeking at an existing draft, never a
   * 404 that would leak which case it is.
   */
  private ClientInvoice resolveInvoiceForView(Contract contract, AuthenticatedPrincipal principal) {
    if ("TESTER".equals(principal.role())) {
      ClientInvoice invoice = findCurrentMonthInvoice(contract).orElse(null);
      clientInvoiceAccessGuard.requireCanView(
          contract, invoice != null ? invoice.getStatus() : ClientInvoiceStatus.DRAFT, principal);
      return invoice;
    }

    // Manager/Agent: requireCanView's MANAGER/AGENT branches don't look at status at all, so the
    // placeholder DRAFT below is inert — kept only so both roles share the one guard method.
    clientInvoiceAccessGuard.requireCanView(contract, ClientInvoiceStatus.DRAFT, principal);
    return getOrCreateDraftForCurrentMonth(contract, principal);
  }

  private Optional<ClientInvoice> findCurrentMonthInvoice(Contract contract) {
    return clientInvoiceRepository.findByContractIdAndBillingMonth(contract.getId(), currentBillingMonth());
  }

  private ClientInvoice getOrCreateDraftForCurrentMonth(Contract contract, AuthenticatedPrincipal principal) {
    LocalDate billingMonth = currentBillingMonth();
    return clientInvoiceRepository
        .findByContractIdAndBillingMonth(contract.getId(), billingMonth)
        .orElseGet(() -> createDraft(contract, billingMonth, principal));
  }

  private LocalDate currentBillingMonth() {
    return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
  }

  private ClientInvoice createDraft(Contract contract, LocalDate billingMonth, AuthenticatedPrincipal principal) {
    ClientInvoice invoice = new ClientInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setTenant(contract.getTenant());
    invoice.setContract(contract);
    invoice.setBillingMonth(billingMonth);
    invoice.setStatus(ClientInvoiceStatus.DRAFT);
    invoice.setCurrency(contract.getCurrency());
    invoice.setCreatedAt(Instant.now());

    try {
      clientInvoiceRepository.saveAndFlush(invoice);
    } catch (DataIntegrityViolationException raceLost) {
      // Another request racing to view this same Contract/month for the first time already won
      // (V11's unique (contract_id, billing_month) constraint) — fall back to the row it created
      // instead of failing this request. Each repository call here runs in its own transaction
      // (no @Transactional wraps this controller), so the loser's transaction is the only one
      // that aborts; this fallback query runs cleanly in a fresh one.
      return clientInvoiceRepository
          .findByContractIdAndBillingMonth(contract.getId(), billingMonth)
          .orElseThrow(() -> raceLost);
    }

    AuditLog.created("ClientInvoice", invoice.getId(), principal.userId(), principal.tenantId());
    return invoice;
  }

  private Contract findContract(UUID contractId, AuthenticatedPrincipal principal) {
    return contractRepository
        .findByIdAndTenantId(contractId, principal.tenantId())
        .orElseThrow(() -> new NotFoundException("No contract with id " + contractId));
  }
}
