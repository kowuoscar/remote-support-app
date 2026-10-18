package com.remotesupport.backend.web;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * A Carrier Invoice File whose bytes retention deleted was asked for: 410. The controllers render
 * it as {@code {code: "CARRIER_INVOICE_FILE_DELETED", message}} through {@link ClientInvoiceErrors}.
 */
@ResponseStatus(HttpStatus.GONE)
public class CarrierInvoiceFileDeletedException extends RuntimeException {

  /** The machine-readable cause, sent to the client as {@code code}. */
  public static final String CODE = "CARRIER_INVOICE_FILE_DELETED";

  public CarrierInvoiceFileDeletedException(UUID fileId) {
    super("The carrier invoice file " + fileId + " was deleted after the retention period");
  }
}
