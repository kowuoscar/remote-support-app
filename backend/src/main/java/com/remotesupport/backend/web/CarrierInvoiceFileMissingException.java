package com.remotesupport.backend.web;

/** A Carrier Invoice File's row exists but its object is not in the store: 404. */
public class CarrierInvoiceFileMissingException extends NotFoundException {

  public CarrierInvoiceFileMissingException(String key) {
    super("No object in the store for carrier invoice file key " + key);
  }
}
