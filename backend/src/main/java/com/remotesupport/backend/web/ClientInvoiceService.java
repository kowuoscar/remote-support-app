package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.CarrierInvoiceFile;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceLine;
import com.remotesupport.backend.domain.ClientInvoiceLineKind;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Client;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Fee;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.dto.CarrierInvoiceFileResponse;
import com.remotesupport.backend.dto.ClientInvoiceBaseSimLineResponse;
import com.remotesupport.backend.dto.ClientInvoiceResponse;
import com.remotesupport.backend.dto.ClientInvoiceSummaryResponse;
import com.remotesupport.backend.dto.ClientInvoiceFeeLineResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.CarrierInvoiceFileRepository;
import com.remotesupport.backend.repository.ClientInvoiceLineRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * What every Client Invoice route does once it has found its invoice, whether it was addressed as
 * "this Contract's invoice for the current month" ({@link ClientInvoiceController}, which today
 * carries no Manager action) or by its own id ({@link ClientInvoiceByIdController}, the only
 * place a Manager approves): the response shape with its lines resolved from the stored rows or the computation
 * (ADR 0001, ADR 0004), the send and approve transitions, the PDF and the Carrier Invoice Files. The two controllers
 * differ only in how they find the invoice and who may call them.
 */
@Component
public class ClientInvoiceService {

  private final ClientInvoiceRepository clientInvoiceRepository;
  private final ContractRepository contractRepository;
  private final FeeRepository feeRepository;
  private final ContractAmountService contractAmountService;
  private final CarrierInvoiceFileRepository carrierInvoiceFileRepository;
  private final ClientInvoiceLineRepository clientInvoiceLineRepository;
  private final CarrierInvoiceFileStorage fileStorage;
  private final ClientInvoicePdfRenderer pdfRenderer;
  private final AgentInvoiceService agentInvoiceService;

  public ClientInvoiceService(
      ClientInvoiceRepository clientInvoiceRepository,
      ContractRepository contractRepository,
      FeeRepository feeRepository,
      ContractAmountService contractAmountService,
      CarrierInvoiceFileRepository carrierInvoiceFileRepository,
      ClientInvoiceLineRepository clientInvoiceLineRepository,
      CarrierInvoiceFileStorage fileStorage,
      ClientInvoicePdfRenderer pdfRenderer,
      AgentInvoiceService agentInvoiceService) {
    this.clientInvoiceRepository = clientInvoiceRepository;
    this.contractRepository = contractRepository;
    this.feeRepository = feeRepository;
    this.contractAmountService = contractAmountService;
    this.carrierInvoiceFileRepository = carrierInvoiceFileRepository;
    this.clientInvoiceLineRepository = clientInvoiceLineRepository;
    this.fileStorage = fileStorage;
    this.pdfRenderer = pdfRenderer;
    this.agentInvoiceService = agentInvoiceService;
  }

  /**
   * Approves a sent Client Invoice ({@code SENT -> APPROVED}); any other status is a clean {@link
   * ConflictException} (409), never a silent no-op. Logs the status-transition audit event. The
   * invoice is re-read under the row lock send, edit and send-back take, so an approval racing a
   * send-back runs after it and gets 409.
   */
  @Transactional
  public ClientInvoiceResponse approve(ClientInvoice found, AuthenticatedPrincipal principal) {
    ClientInvoice invoice = clientInvoiceRepository.findByIdForUpdate(found.getId()).orElseThrow();
    ClientInvoiceStatus oldStatus = invoice.getStatus();
    if (!oldStatus.canTransitionTo(ClientInvoiceStatus.APPROVED)) {
      throw new ConflictException("Cannot approve a Client Invoice from status " + oldStatus);
    }

    invoice.setStatus(ClientInvoiceStatus.APPROVED);
    invoice.setApprovedAt(Instant.now());
    clientInvoiceRepository.save(invoice);

    AuditLog.statusChanged(
        "ClientInvoice",
        invoice.getId(),
        oldStatus.name(),
        ClientInvoiceStatus.APPROVED.name(),
        principal.userId(),
        principal.tenantId());

    return toResponse(invoice, true);
  }

  /**
   * A fresh PDF of a {@code SENT}/{@code APPROVED} invoice; a draft is still being assembled, so
   * there is nothing final to render yet (409).
   */
  @Transactional(readOnly = true)
  public ResponseEntity<byte[]> pdf(ClientInvoice invoice) {
    if (invoice.getStatus() == ClientInvoiceStatus.DRAFT) {
      throw new ConflictException("Cannot generate a PDF for a Client Invoice still in draft");
    }

    byte[] pdfBytes = pdfRenderer.render(invoice.getContract(), toResponse(invoice, false));

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"client-invoice-" + invoice.getBillingMonth() + ".pdf\"")
        .body(pdfBytes);
  }

  /** An empty upload is refused ({@link InvalidRequestException}, 400). */
  public void requireNonEmpty(MultipartFile file) {
    if (file.isEmpty()) {
      throw new InvalidRequestException("file must not be empty");
    }
  }

  /**
   * Attaches a Carrier Invoice File to a found invoice: only a {@code DRAFT} takes one (any other
   * status is a {@link ConflictException}, 409), and an empty file is refused. Stores the bytes,
   * saves the row, logs its creation, and answers {@code 201} with the file.
   */
  public ResponseEntity<CarrierInvoiceFileResponse> attachFile(
      ClientInvoice invoice, MultipartFile file, AuthenticatedPrincipal principal) {
    requireNonEmpty(file);
    if (invoice.getStatus() != ClientInvoiceStatus.DRAFT) {
      throw new ConflictException(
          "Cannot attach a carrier invoice file to a Client Invoice that has already been sent");
    }

    CarrierInvoiceFile carrierFile = new CarrierInvoiceFile();
    carrierFile.setId(UUID.randomUUID());
    carrierFile.setTenant(invoice.getTenant());
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

  public List<CarrierInvoiceFileResponse> files(ClientInvoice invoice) {
    return carrierInvoiceFileRepository.findByClientInvoiceIdOrderByUploadedAtAsc(invoice.getId()).stream()
        .map(CarrierInvoiceFileResponse::of)
        .toList();
  }

  /** One of this invoice's Carrier Invoice Files; a file belonging to any other invoice is 404. */
  public ResponseEntity<byte[]> downloadFile(ClientInvoice invoice, UUID fileId) {
    CarrierInvoiceFile file =
        carrierInvoiceFileRepository
            .findByIdAndClientInvoiceId(fileId, invoice.getId())
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

  /**
   * The invoice's response, served from {@link #lines its lines}: the base amount is the sum of the
   * per-SIM and base-amount lines, the Fee lines are the Fee lines, the total is both. The per-SIM
   * breakdown is served whenever the base amount is per SIM, whatever the status; an invoice
   * holding a {@code BASE_AMOUNT} line (sent before per-line storage) has none. {@code
   * includeEdits} false (a Tester caller) leaves {@code computedAmount} and {@code edited} null
   * everywhere; the billed amounts are the same.
   */
  @Transactional(readOnly = true)
  public ClientInvoiceResponse toResponse(ClientInvoice invoice, boolean includeEdits) {
    List<ResolvedLine> lines = lines(invoice);
    boolean perSim = lines.stream().noneMatch(l -> l.kind() == ClientInvoiceLineKind.BASE_AMOUNT);
    List<ClientInvoiceBaseSimLineResponse> basePostpaidSims =
        perSim
            ? lines.stream()
                .filter(l -> l.kind() == ClientInvoiceLineKind.POSTPAID_SIM)
                .map(
                    l ->
                        ClientInvoiceBaseSimLineResponse.of(
                            l.simCard(),
                            l.amount(),
                            includeEdits ? l.computedAmount() : null,
                            includeEdits ? l.edited() : null))
                .toList()
            : null;
    List<ClientInvoiceFeeLineResponse> feeLines =
        lines.stream()
            .filter(l -> l.kind() == ClientInvoiceLineKind.FEE)
            .map(
                l ->
                    ClientInvoiceFeeLineResponse.of(
                        l.fee(),
                        l.amount(),
                        includeEdits ? l.computedAmount() : null,
                        includeEdits ? l.edited() : null))
            .toList();

    return new ClientInvoiceResponse(
        invoice.getId(),
        invoice.getContract().getId(),
        invoice.getBillingMonth(),
        invoice.getStatus().name(),
        invoice.getCurrency().name(),
        baseAmount(lines),
        basePostpaidSims,
        feeLines,
        total(lines),
        files(invoice),
        invoice.getSentAt(),
        invoice.getApprovedAt(),
        includeEdits ? invoice.getSentBackAt() : null,
        includeEdits ? invoice.getSentBackReason() : null);
  }

  /**
   * A Manager's send-back ({@code SENT -> DRAFT}, ADR 0005) as one transaction under the row lock
   * approve, send and edit take. Touches no line and leaves {@code linesStored} set: the draft
   * carries the stored lines it was sent with, nothing recomputed. Clears {@code sentAt}, records
   * {@code sentBackAt} and the reason. The audit line names who and which invoice, never the
   * reason's text.
   */
  @Transactional
  public ClientInvoiceResponse sendBack(
      ClientInvoice found, String reason, AuthenticatedPrincipal principal) {
    ClientInvoice invoice = clientInvoiceRepository.findByIdForUpdate(found.getId()).orElseThrow();
    ClientInvoiceStatus oldStatus = invoice.getStatus();
    if (!oldStatus.canTransitionTo(ClientInvoiceStatus.DRAFT)) {
      throw new ConflictException("Cannot send a Client Invoice back from status " + oldStatus);
    }

    invoice.setStatus(ClientInvoiceStatus.DRAFT);
    invoice.setSentAt(null);
    invoice.setSentBackAt(Instant.now());
    invoice.setSentBackReason(reason);
    clientInvoiceRepository.save(invoice);

    AuditLog.statusChanged(
        "ClientInvoice",
        invoice.getId(),
        oldStatus.name(),
        ClientInvoiceStatus.DRAFT.name(),
        principal.userId(),
        principal.tenantId());

    return toResponse(invoice, true);
  }

  /**
   * The Agent's edit of one line of a draft invoice (edit-client-invoice-lines spec, "Backend:
   * editing a line", steps 1 to 7), as one transaction. The invoice is re-read under the row lock
   * the send takes, so an edit never lands on a sent invoice: it waits for the send and then gets
   * 409. The line is found among {@link #lines the invoice's current lines} (404 if none). On a
   * never-sent draft the edit upserts the override row, or deletes it when the amount equals the
   * computed amount (the line follows the computation again); on a draft with stored lines it
   * updates the row, inserts one for a pre-filled late-Fee line, and never deletes a row.
   */
  @Transactional
  public ClientInvoiceResponse editLine(
      ClientInvoice found,
      ClientInvoiceLineKind kind,
      UUID sourceId,
      BigDecimal amount,
      AuthenticatedPrincipal principal) {
    ClientInvoice invoice = clientInvoiceRepository.findByIdForUpdate(found.getId()).orElseThrow();
    if (invoice.getStatus() != ClientInvoiceStatus.DRAFT) {
      throw new ConflictException("Cannot edit a line of a Client Invoice that is " + invoice.getStatus());
    }
    UUID wantedSource = kind == ClientInvoiceLineKind.BASE_AMOUNT ? null : sourceId;
    ResolvedLine line =
        lines(invoice).stream()
            .filter(l -> l.kind() == kind && Objects.equals(sourceOf(l), wantedSource))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("No such line on this Client Invoice"));

    boolean reset = amount.compareTo(line.computedAmount()) == 0;
    ClientInvoiceLine row = line.stored();
    if (!invoice.isLinesStored() && reset) {
      if (row != null) {
        clientInvoiceLineRepository.delete(row);
      }
    } else {
      if (row == null) {
        row = newRow(invoice, line);
      }
      row.setAmount(amount);
      if (!invoice.isLinesStored()) {
        row.setComputedAmount(line.computedAmount());
      }
      row.setEditedAt(reset ? null : Instant.now());
      row.setEditedBy(reset ? null : principal.userId());
      clientInvoiceLineRepository.save(row);
    }

    AuditLog.clientInvoiceLineEdited(
        invoice.getId(), kind.name(), sourceOf(line), line.amount(), amount, principal.userId(), principal.tenantId());

    agentInvoiceService.followClientInvoiceEdit(invoice, line.amount(), amount, principal);

    return toResponse(invoice, true);
  }

  private static UUID sourceOf(ResolvedLine line) {
    return switch (line.kind()) {
      case POSTPAID_SIM -> line.simCard().getId();
      case FEE -> line.fee().getId();
      case BASE_AMOUNT -> null;
    };
  }

  /**
   * Sends a draft ({@code DRAFT -> SENT}) as one transaction (edit-client-invoice-lines spec,
   * "Prefactoring"). The invoice is re-read under a row lock, so a concurrent send waits and then
   * finds it {@code SENT} (409). Stores a row for every line the invoice shows that has none and
   * sets {@code linesStored}; a draft that already had stored lines keeps every existing row, so
   * only the pre-filled late-Fee lines are written. From here no figure moves (ADR 0001).
   */
  @Transactional
  public ClientInvoiceResponse send(ClientInvoice found, AuthenticatedPrincipal principal) {
    ClientInvoice invoice = clientInvoiceRepository.findByIdForUpdate(found.getId()).orElseThrow();
    ClientInvoiceStatus oldStatus = invoice.getStatus();
    if (!oldStatus.canTransitionTo(ClientInvoiceStatus.SENT)) {
      throw new ConflictException("Cannot send a Client Invoice from status " + oldStatus);
    }

    boolean neverSent = !invoice.isLinesStored();
    for (ResolvedLine line : lines(invoice)) {
      if (line.stored() == null) {
        clientInvoiceLineRepository.save(newRow(invoice, line));
      } else if (neverSent) {
        line.stored().setComputedAmount(line.computedAmount());
      }
    }
    invoice.setLinesStored(true);
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

    return toResponse(invoice, true);
  }

  private static ClientInvoiceLine newRow(ClientInvoice invoice, ResolvedLine line) {
    ClientInvoiceLine row = new ClientInvoiceLine();
    row.setId(UUID.randomUUID());
    row.setTenant(invoice.getTenant());
    row.setClientInvoice(invoice);
    row.setKind(line.kind());
    row.setSimCard(line.simCard());
    row.setFee(line.fee());
    row.setAmount(line.amount());
    row.setComputedAmount(line.computedAmount());
    row.setCreatedAt(Instant.now());
    return row;
  }

  /**
   * One line an invoice shows. {@code stored} is its row, or {@code null} for a line that is only
   * computed or pre-filled and has no row yet.
   */
  public record ResolvedLine(
      ClientInvoiceLineKind kind,
      SimCard simCard,
      Fee fee,
      BigDecimal amount,
      BigDecimal computedAmount,
      ClientInvoiceLine stored) {

    /** A line is edited when what it bills differs from what the computation said. */
    public boolean edited() {
      return amount.compareTo(computedAmount) != 0;
    }
  }

  /**
   * An invoice's lines: the one resolution the read and the send share (edit-client-invoice-lines
   * spec, "The model"). {@code linesStored} false is the computation (a Postpaid SIM line per
   * billing SIM, a Fee line per Fee of the month) with any stored row for the same SIM or Fee
   * overriding its amount. {@code linesStored} true is the stored rows exactly; while {@code DRAFT}
   * (a sent-back invoice) it also pre-fills a line per Fee of the month that has no row, and none
   * for a Postpaid SIM.
   */
  public List<ResolvedLine> lines(ClientInvoice invoice) {
    List<ClientInvoiceLine> rows = clientInvoiceLineRepository.findByClientInvoiceId(invoice.getId());
    UUID contractId = invoice.getContract().getId();
    List<ResolvedLine> lines = new ArrayList<>();

    if (!invoice.isLinesStored()) {
      Map<UUID, ClientInvoiceLine> simRows =
          rowsBy(rows, ClientInvoiceLineKind.POSTPAID_SIM, l -> l.getSimCard().getId());
      for (SimCard sim : contractAmountService.billablePostpaidSims(contractId, invoice.getBillingMonth())) {
        lines.add(
            computedLine(
                ClientInvoiceLineKind.POSTPAID_SIM, sim, null, sim.getMonthlyFeeAmount(), simRows.remove(sim.getId())));
      }
      // An override row whose SIM no longer bills this month is kept, with a computed amount of
      // zero, so the Agent's figure is not silently dropped and it still reads as edited.
      simRows.values().stream()
          .sorted(Comparator.comparing(ClientInvoiceLine::getCreatedAt))
          .forEach(
              row ->
                  lines.add(
                      new ResolvedLine(
                          ClientInvoiceLineKind.POSTPAID_SIM,
                          row.getSimCard(),
                          null,
                          row.getAmount(),
                          BigDecimal.ZERO,
                          row)));
      Map<UUID, ClientInvoiceLine> feeRows = rowsBy(rows, ClientInvoiceLineKind.FEE, l -> l.getFee().getId());
      for (Fee fee : feesOfMonth(invoice)) {
        lines.add(computedLine(ClientInvoiceLineKind.FEE, null, fee, fee.getAmount(), feeRows.get(fee.getId())));
      }
      return lines;
    }

    rows.stream()
        .filter(l -> l.getKind() != ClientInvoiceLineKind.FEE)
        .sorted(Comparator.comparing(ClientInvoiceLine::getKind).thenComparing(ClientInvoiceLine::getCreatedAt))
        .forEach(l -> lines.add(storedLine(l)));
    rows.stream()
        .filter(l -> l.getKind() == ClientInvoiceLineKind.FEE)
        .sorted(Comparator.comparing(l -> l.getFee().getCreatedAt()))
        .forEach(l -> lines.add(storedLine(l)));
    if (invoice.getStatus() == ClientInvoiceStatus.DRAFT) {
      Map<UUID, ClientInvoiceLine> feeRows = rowsBy(rows, ClientInvoiceLineKind.FEE, l -> l.getFee().getId());
      for (Fee fee : feesOfMonth(invoice)) {
        if (!feeRows.containsKey(fee.getId())) {
          lines.add(computedLine(ClientInvoiceLineKind.FEE, null, fee, fee.getAmount(), null));
        }
      }
    }
    return lines;
  }

  private List<Fee> feesOfMonth(ClientInvoice invoice) {
    return feeRepository.findByContractIdAndBillingMonthOrderByCreatedAtAsc(
        invoice.getContract().getId(), invoice.getBillingMonth());
  }

  private static Map<UUID, ClientInvoiceLine> rowsBy(
      List<ClientInvoiceLine> rows, ClientInvoiceLineKind kind, Function<ClientInvoiceLine, UUID> key) {
    return rows.stream().filter(l -> l.getKind() == kind).collect(Collectors.toMap(key, Function.identity()));
  }

  private static ResolvedLine computedLine(
      ClientInvoiceLineKind kind, SimCard sim, Fee fee, BigDecimal computed, ClientInvoiceLine override) {
    return new ResolvedLine(kind, sim, fee, override != null ? override.getAmount() : computed, computed, override);
  }

  private static ResolvedLine storedLine(ClientInvoiceLine row) {
    return new ResolvedLine(
        row.getKind(), row.getSimCard(), row.getFee(), row.getAmount(), row.getComputedAmount(), row);
  }

  private static BigDecimal baseAmount(List<ResolvedLine> lines) {
    return lines.stream()
        .filter(l -> l.kind() != ClientInvoiceLineKind.FEE)
        .map(ResolvedLine::amount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /** The invoice total (ADR 0001): the base amount plus every Fee line's amount. */
  private static BigDecimal total(List<ResolvedLine> lines) {
    return lines.stream().map(ResolvedLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /**
   * For each Contract of {@code client} (within {@code tenantId}) that has a {@code SENT} or
   * {@code APPROVED} invoice, a summary of the one with the latest billing month. Read-only: a
   * draft is never created, and a Contract with only a draft is absent. The total is the sum of the
   * invoice's lines (ADR 0001), as in {@link
   * #toResponse}.
   */
  @Transactional(readOnly = true)
  public List<ClientInvoiceSummaryResponse> latestSentOrApproved(Client client, UUID tenantId) {
    return contractRepository
        .findByTenantIdAndClientIdOrderByCreatedAtAsc(tenantId, client.getId())
        .stream()
        .map(this::latestSentOrApproved)
        .flatMap(Optional::stream)
        .toList();
  }

  private Optional<ClientInvoiceSummaryResponse> latestSentOrApproved(Contract contract) {
    return clientInvoiceRepository
        .findFirstByContractIdAndStatusInOrderByBillingMonthDesc(
            contract.getId(),
            EnumSet.of(ClientInvoiceStatus.SENT, ClientInvoiceStatus.APPROVED))
        .map(
            invoice ->
                new ClientInvoiceSummaryResponse(
                    contract.getId(),
                    invoice.getId(),
                    invoice.getBillingMonth(),
                    invoice.getStatus().name(),
                    invoice.getCurrency().name(),
                    total(lines(invoice))));
  }
}
