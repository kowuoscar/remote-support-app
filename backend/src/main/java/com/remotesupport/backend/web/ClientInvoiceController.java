package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.CarrierInvoiceFile;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardFlavor;
import com.remotesupport.backend.domain.SimCardStatus;
import com.remotesupport.backend.dto.CarrierInvoiceFileResponse;
import com.remotesupport.backend.dto.ClientInvoiceResponse;
import com.remotesupport.backend.dto.FeeResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.CarrierInvoiceFileRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.security.ClientInvoiceAccessGuard;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
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
 * entity; client-invoice-generation ticket, user stories 22-23). One per Contract per month; this
 * ticket only ever produces/returns one in {@code DRAFT} — {@code SENT}/{@code APPROVED} are
 * client-invoice-submission-and-visibility's concern.
 *
 * <p><b>Get-or-create semantics.</b> {@code GET} is deliberately idempotent-with-a-side-effect:
 * the ticket's AC is "created in status draft on first access", not a separate explicit create
 * step the Agent has to remember to call first — the same "no separate create step" shape
 * fee-logging-and-provisioning already established for a proactive Fee's auto-created linking
 * Request. A plain {@code GET} that creates the current month's draft the first time it's viewed,
 * and simply returns the same row every time after, is idempotent in the sense that matters to a
 * caller (repeating the call is always safe and yields the same resource), even though it isn't
 * purely free of side effects. The alternative — a separate {@code POST .../draft} the Agent must
 * call before the first {@code GET} — would add a step with no real decision behind it (there is
 * only ever one correct draft for "now": this Contract's, this month's), and every existing
 * caller of {@code GET} would need to become "POST-then-GET" for no behavioural benefit. A second,
 * concurrent first-view racing to create the same month's draft is resolved by the unique {@code
 * (contract_id, billing_month)} constraint (V11 migration), not by application-level locking —
 * see {@link #createDraft}.
 *
 * <p><b>Base amount and Fee lines are never stored.</b> spec.md: the base amount is "the sum of
 * the monthly fee of every Postpaid SIM active in the Contract's Fleet at the time of viewing" —
 * computed fresh on every {@code GET} from {@link SimCardRepository}, and Fee lines fresh from
 * {@link FeeRepository}, so a Fleet or Fee change between two views is reflected immediately with
 * no cache-invalidation logic anywhere.
 */
@RestController
@RequestMapping("/api/contracts/{contractId}/client-invoice")
public class ClientInvoiceController {

  private final ContractRepository contractRepository;
  private final ClientInvoiceRepository clientInvoiceRepository;
  private final SimCardRepository simCardRepository;
  private final FeeRepository feeRepository;
  private final CarrierInvoiceFileRepository carrierInvoiceFileRepository;
  private final ClientInvoiceAccessGuard clientInvoiceAccessGuard;
  private final CarrierInvoiceFileStorage fileStorage;

  public ClientInvoiceController(
      ContractRepository contractRepository,
      ClientInvoiceRepository clientInvoiceRepository,
      SimCardRepository simCardRepository,
      FeeRepository feeRepository,
      CarrierInvoiceFileRepository carrierInvoiceFileRepository,
      ClientInvoiceAccessGuard clientInvoiceAccessGuard,
      CarrierInvoiceFileStorage fileStorage) {
    this.contractRepository = contractRepository;
    this.clientInvoiceRepository = clientInvoiceRepository;
    this.simCardRepository = simCardRepository;
    this.feeRepository = feeRepository;
    this.carrierInvoiceFileRepository = carrierInvoiceFileRepository;
    this.clientInvoiceAccessGuard = clientInvoiceAccessGuard;
    this.fileStorage = fileStorage;
  }

  @GetMapping
  public ClientInvoiceResponse get(
      @PathVariable UUID contractId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    clientInvoiceAccessGuard.requireCanBuildOrView(contract, principal);

    ClientInvoice invoice = getOrCreateDraftForCurrentMonth(contract, principal);
    return buildResponse(invoice);
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
    clientInvoiceAccessGuard.requireCanBuildOrView(contract, principal);

    ClientInvoice invoice = getOrCreateDraftForCurrentMonth(contract, principal);
    return carrierInvoiceFileRepository.findByClientInvoiceIdOrderByUploadedAtAsc(invoice.getId()).stream()
        .map(CarrierInvoiceFileResponse::of)
        .toList();
  }

  @GetMapping("/files/{fileId}")
  public ResponseEntity<byte[]> downloadFile(
      @PathVariable UUID contractId,
      @PathVariable UUID fileId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Contract contract = findContract(contractId, principal);
    clientInvoiceAccessGuard.requireCanBuildOrView(contract, principal);

    CarrierInvoiceFile file =
        carrierInvoiceFileRepository
            .findByIdAndClientInvoiceContractId(fileId, contract.getId())
            .orElseThrow(() -> new NotFoundException("No carrier invoice file with id " + fileId));

    byte[] content;
    try {
      content = fileStorage.read(file.getStoragePath());
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read carrier invoice file", e);
    }

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.getContentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
        .body(content);
  }

  private ClientInvoiceResponse buildResponse(ClientInvoice invoice) {
    UUID contractId = invoice.getContract().getId();
    BigDecimal baseAmount = computeBaseAmount(contractId);

    List<FeeResponse> feeLines =
        feeRepository.findByContractIdAndBillingMonthOrderByCreatedAtAsc(contractId, invoice.getBillingMonth())
            .stream()
            .map(FeeResponse::of)
            .toList();
    BigDecimal feesTotal =
        feeLines.stream().map(FeeResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

    List<CarrierInvoiceFileResponse> files =
        carrierInvoiceFileRepository.findByClientInvoiceIdOrderByUploadedAtAsc(invoice.getId()).stream()
            .map(CarrierInvoiceFileResponse::of)
            .toList();

    return new ClientInvoiceResponse(
        invoice.getId(),
        contractId,
        invoice.getBillingMonth(),
        invoice.getStatus().name(),
        invoice.getCurrency().name(),
        baseAmount,
        feeLines,
        baseAmount.add(feesTotal),
        files);
  }

  /**
   * spec.md Solution: base amount = "the sum of the monthly fee of every Postpaid SIM active in
   * the Contract's Fleet at the time of viewing" — a Retired Postpaid SIM (even one that was
   * Active earlier this month) and every Prepaid SIM (which never carries a monthly fee) are both
   * excluded.
   */
  private BigDecimal computeBaseAmount(UUID contractId) {
    return simCardRepository.findByContractIdOrderByCreatedAtAsc(contractId).stream()
        .filter(sim -> sim.getFlavor() == SimCardFlavor.POSTPAID && sim.getStatus() == SimCardStatus.ACTIVE)
        .map(SimCard::getMonthlyFeeAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private ClientInvoice getOrCreateDraftForCurrentMonth(Contract contract, AuthenticatedPrincipal principal) {
    LocalDate billingMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
    return clientInvoiceRepository
        .findByContractIdAndBillingMonth(contract.getId(), billingMonth)
        .orElseGet(() -> createDraft(contract, billingMonth, principal));
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
