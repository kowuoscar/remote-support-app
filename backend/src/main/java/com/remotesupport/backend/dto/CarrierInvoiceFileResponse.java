package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.CarrierInvoiceFile;
import java.time.Instant;
import java.util.UUID;

public record CarrierInvoiceFileResponse(
    UUID id, UUID clientInvoiceId, String filename, String contentType, long sizeBytes, Instant uploadedAt) {

  public static CarrierInvoiceFileResponse of(CarrierInvoiceFile file) {
    return new CarrierInvoiceFileResponse(
        file.getId(),
        file.getClientInvoice().getId(),
        file.getFilename(),
        file.getContentType(),
        file.getSizeBytes(),
        file.getUploadedAt());
  }
}
