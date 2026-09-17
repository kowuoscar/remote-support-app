package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.CarrierInvoiceFile;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceFeeSnapshot;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Fee;
import com.remotesupport.backend.dto.CarrierInvoiceFileResponse;
import com.remotesupport.backend.dto.ClientInvoiceResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.CarrierInvoiceFileRepository;
import com.remotesupport.backend.repository.ClientInvoiceFeeSnapshotRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.security.ClientInvoiceAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * A Contract's Client Invoice for the current calendar month (spec.md Solution's Client Invoice
 * entity; client-invoice-generation and client-invoice-submission-and-visibility tickets, user
 * stories 22-24, 34-35, 7-8). One per Contract per month, spanning the whole {@code DRAFT ->
 * SENT -> APPROVED} lifecycle: building/attaching files ({@code DRAFT} only), sending (the
 * Contract's own Agent), Client visibility and on-demand PDF (from {@code SENT} onward), and
 * Manager approval ({@code SENT -> APPROVED}).
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
 * <p><b>Live while DRAFT, frozen from SENT onward.</b> While {@code DRAFT}, the base amount and
 * Fee lines are computed fresh via {@link ContractAmountService} on every
 * {@code GET} (client-invoice-generation ticket, unchanged by this one — the Regression this
 * ticket must not break). {@link #send} snapshots both the moment the Agent sends: {@code
 * ClientInvoice#snapshotBaseAmount} and the Fee-line membership into {@link
 * ClientInvoiceFeeSnapshot} rows. Every {@code GET} from {@code SENT} onward serves that snapshot
 * — see {@link ClientInvoiceService#toResponse} — so a Fee logged against the same Contract/month
 * afterwards can never silently change a total the Manager already approved or the Client was
 * already shown. See
 * {@link ClientInvoice}'s Javadoc and CONTEXT.md's "Client Invoice" entry for the full reasoning.
 */
@RestController
@RequestMapping("/api/contracts/{contractId}/client-invoice")
public class ClientInvoiceController {

  private final ContractRepository contractRepository;
  private final ClientInvoiceRepository clientInvoiceRepository;
  private final FeeRepository feeRepository;
  private final ContractAmountService contractAmountService;
  private final CarrierInvoiceFileRepository carrierInvoiceFileRepository;
  private final ClientInvoiceFeeSnapshotRepository clientInvoiceFeeSnapshotRepository;
  private final ClientInvoiceAccessGuard clientInvoiceAccessGuard;
  private final CarrierInvoiceFileStorage fileStorage;
  private final ClientInvoiceService clientInvoiceService;

  public ClientInvoiceController(
      ContractRepository contractRepository,
      ClientInvoiceRepository clientInvoiceRepository,
      FeeRepository feeRepository,
      ContractAmountService contractAmountService,
      CarrierInvoiceFileRepository carrierInvoiceFileRepository,
      ClientInvoiceFeeSnapshotRepository clientInvoiceFeeSnapshotRepository,
      ClientInvoiceAccessGuard clientInvoiceAccessGuard,
      CarrierInvoiceFileStorage fileStorage,
      ClientInvoiceService clientInvoiceService) {
    this.contractRepository = contractRepository;
    this.clientInvoiceRepository = clientInvoiceRepository;
    this.feeRepository = feeRepository;
    this.contractAmountService = contractAmountService;
    this.carrierInvoiceFileRepository = carrierInvoiceFileRepository;
    this.clientInvoiceFeeSnapshotRepository = clientInvoiceFeeSnapshotRepository;
    this.clientInvoiceAccessGuard = clientInvoiceAccessGuard;
    this.fileStorage = fileStorage;
    this.clientInvoiceService = clientInvoiceService;
  }

  @GetMapping
  public ClientInvoiceResponse get(
      @PathVariable UUID contractId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    ClientInvoice invoice = resolveInvoiceForView(contract, principal);
    return clientInvoiceService.toResponse(invoice);
  }

  /**
   * Sends this Contract's current-month draft Client Invoice (ticket AC: "Agent can send a draft
   * Client Invoice, moving it to status sent; a sent invoice is no longer editable by the
   * Agent"), snapshotting its base amount and Fee lines in the same transaction as the status
   * change so the two can never disagree.
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

    ClientInvoiceStatus oldStatus = invoice.getStatus();
    if (!oldStatus.canTransitionTo(ClientInvoiceStatus.SENT)) {
      throw new ConflictException("Cannot send a Client Invoice from status " + oldStatus);
    }

    snapshot(contract, invoice);
    invoice.setStatus(ClientInvoiceStatus.SENT);
    invoice.setSentAt(Instant.now());
    clientInvoiceRepository.save(invoice);

    AuditLog.statusChanged(
        "ClientInvoice",
        invoice.getId(),
        oldStatus.name(),
        ClientInvoiceStatus.SENT.name(),
        principal.userId(),
        principal.tenantId());

    return clientInvoiceService.toResponse(invoice);
  }

  /**
   * Approves this Contract's current-month sent Client Invoice (ticket AC: "Manager can review a
   * sent Client Invoice ... and approve it, moving it to status approved" / "Manager cannot
   * approve a Client Invoice still in draft"). The precondition is enforced the same way
   * {@link com.remotesupport.backend.domain.RequestStatus}/{@link
   * com.remotesupport.backend.domain.SmartphoneStatus} invalid transitions already are: a clean
   * {@link ConflictException} (409), not a silent no-op or a 500.
   */
  @PostMapping("/approve")
  public ClientInvoiceResponse approve(
      @PathVariable UUID contractId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    clientInvoiceAccessGuard.requireCanApprove(principal);

    ClientInvoice invoice =
        clientInvoiceRepository
            .findByContractIdAndBillingMonth(contract.getId(), currentBillingMonth())
            .orElseThrow(() -> new NotFoundException("No Client Invoice for this Contract this month"));

    return clientInvoiceService.approve(invoice, principal);
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

  /**
   * Freezes this send's base amount and Fee-line membership (see {@link ClientInvoice}'s and
   * {@link ClientInvoiceFeeSnapshot}'s Javadoc for why). Called once, from {@link #send}, inside
   * the same request/transaction as the status change.
   */
  private void snapshot(Contract contract, ClientInvoice invoice) {
    invoice.setSnapshotBaseAmount(contractAmountService.baseAmount(contract.getId()));

    List<Fee> fees =
        feeRepository.findByContractIdAndBillingMonthOrderByCreatedAtAsc(contract.getId(), invoice.getBillingMonth());
    for (Fee fee : fees) {
      ClientInvoiceFeeSnapshot line = new ClientInvoiceFeeSnapshot();
      line.setId(UUID.randomUUID());
      line.setTenant(contract.getTenant());
      line.setClientInvoice(invoice);
      line.setFee(fee);
      line.setCreatedAt(Instant.now());
      clientInvoiceFeeSnapshotRepository.save(line);
    }
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
