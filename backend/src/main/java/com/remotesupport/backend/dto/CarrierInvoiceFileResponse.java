package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.CarrierInvoiceFile;
import java.time.Instant;
import java.util.UUID;

public record CarrierInvoiceFileResponse(
    UUID id,
    UUID clientInvoiceId,
    UUID simCardId,
    String filename,
    String contentType,
    long sizeBytes,
    Instant uploadedAt,
    Instant deletedAt) {

  public static CarrierInvoiceFileResponse of(CarrierInvoiceFile file) {
    return new CarrierInvoiceFileResponse(
        file.getId(),
        file.getClientInvoice().getId(),
        file.getSimCard() != null ? file.getSimCard().getId() : null,
        file.getFilename(),
        file.getContentType(),
        file.getSizeBytes(),
        file.getUploadedAt(),
        file.getDeletedAt());
  }
}
