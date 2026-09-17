package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.CarrierInvoiceFile;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceFeeSnapshot;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Fee;
import com.remotesupport.backend.dto.CarrierInvoiceFileResponse;
import com.remotesupport.backend.dto.ClientInvoiceResponse;
import com.remotesupport.backend.dto.FeeResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.CarrierInvoiceFileRepository;
import com.remotesupport.backend.repository.ClientInvoiceFeeSnapshotRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * What every Client Invoice route does once it has found its invoice, whether it was addressed as
 * "this Contract's invoice for the current month" ({@link ClientInvoiceController}) or by its own
 * id ({@link ClientInvoiceByIdController}): the response shape with its live-while-draft,
 * frozen-from-sent reads (ADR 0001), the approve transition, the PDF and the Carrier Invoice
 * Files. The two controllers differ only in how they find the invoice and who may call them.
 */
@Component
public class ClientInvoiceService {

  private final ClientInvoiceRepository clientInvoiceRepository;
  private final FeeRepository feeRepository;
  private final ContractAmountService contractAmountService;
  private final CarrierInvoiceFileRepository carrierInvoiceFileRepository;
  private final ClientInvoiceFeeSnapshotRepository clientInvoiceFeeSnapshotRepository;
  private final CarrierInvoiceFileStorage fileStorage;
  private final ClientInvoicePdfRenderer pdfRenderer;

  public ClientInvoiceService(
      ClientInvoiceRepository clientInvoiceRepository,
      FeeRepository feeRepository,
      ContractAmountService contractAmountService,
      CarrierInvoiceFileRepository carrierInvoiceFileRepository,
      ClientInvoiceFeeSnapshotRepository clientInvoiceFeeSnapshotRepository,
      CarrierInvoiceFileStorage fileStorage,
      ClientInvoicePdfRenderer pdfRenderer) {
    this.clientInvoiceRepository = clientInvoiceRepository;
    this.feeRepository = feeRepository;
    this.contractAmountService = contractAmountService;
    this.carrierInvoiceFileRepository = carrierInvoiceFileRepository;
    this.clientInvoiceFeeSnapshotRepository = clientInvoiceFeeSnapshotRepository;
    this.fileStorage = fileStorage;
    this.pdfRenderer = pdfRenderer;
  }

  /**
   * Approves a sent Client Invoice ({@code SENT -> APPROVED}); any other status is a clean {@link
   * ConflictException} (409), never a silent no-op. Logs the status-transition audit event.
   */
  public ClientInvoiceResponse approve(ClientInvoice invoice, AuthenticatedPrincipal principal) {
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

    return toResponse(invoice);
  }

  /**
   * A fresh PDF of a {@code SENT}/{@code APPROVED} invoice; a draft is still being assembled, so
   * there is nothing final to render yet (409).
   */
  public ResponseEntity<byte[]> pdf(ClientInvoice invoice) {
    if (invoice.getStatus() == ClientInvoiceStatus.DRAFT) {
      throw new ConflictException("Cannot generate a PDF for a Client Invoice still in draft");
    }

    byte[] pdfBytes = pdfRenderer.render(invoice.getContract(), toResponse(invoice));

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"client-invoice-" + invoice.getBillingMonth() + ".pdf\"")
        .body(pdfBytes);
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
   * The invoice's response: while {@code DRAFT}, base amount and Fee lines are computed live; from
   * {@code SENT} onward both come from the snapshot taken at send (ADR 0001).
   */
  public ClientInvoiceResponse toResponse(ClientInvoice invoice) {
    UUID contractId = invoice.getContract().getId();
    boolean frozen = invoice.getStatus() != ClientInvoiceStatus.DRAFT;

    BigDecimal baseAmount = frozen ? invoice.getSnapshotBaseAmount() : contractAmountService.baseAmount(contractId);
    List<FeeResponse> feeLines = frozen ? snapshottedFeeLines(invoice) : liveFeeLines(invoice);
    BigDecimal feesTotal =
        feeLines.stream().map(FeeResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

    return new ClientInvoiceResponse(
        invoice.getId(),
        contractId,
        invoice.getBillingMonth(),
        invoice.getStatus().name(),
        invoice.getCurrency().name(),
        baseAmount,
        feeLines,
        baseAmount.add(feesTotal),
        files(invoice),
        invoice.getSentAt(),
        invoice.getApprovedAt());
  }

  private List<FeeResponse> liveFeeLines(ClientInvoice invoice) {
    return feeRepository
        .findByContractIdAndBillingMonthOrderByCreatedAtAsc(invoice.getContract().getId(), invoice.getBillingMonth())
        .stream()
        .map(FeeResponse::of)
        .toList();
  }

  /**
   * The frozen Fee lines for a {@code SENT}/{@code APPROVED} invoice: every {@link Fee} pinned by
   * a {@link ClientInvoiceFeeSnapshot} row, ordered exactly like the live view (oldest first) so
   * switching from live to frozen never reorders what the Agent already saw.
   */
  private List<FeeResponse> snapshottedFeeLines(ClientInvoice invoice) {
    List<UUID> feeIds =
        clientInvoiceFeeSnapshotRepository.findByClientInvoiceId(invoice.getId()).stream()
            .map(line -> line.getFee().getId())
            .toList();
    return feeRepository.findAllById(feeIds).stream()
        .sorted(Comparator.comparing(Fee::getCreatedAt))
        .map(FeeResponse::of)
        .toList();
  }
}
