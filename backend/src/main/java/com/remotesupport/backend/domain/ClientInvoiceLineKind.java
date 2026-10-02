package com.remotesupport.backend.domain;

/** What a {@link ClientInvoiceLine} bills; fixes which reference the line carries. */
public enum ClientInvoiceLineKind {
  /** One Postpaid SIM Card's share of the base amount; carries a SIM Card. */
  POSTPAID_SIM,
  /** The base amount of an invoice sent before lines existed, as one number; carries neither. */
  BASE_AMOUNT,
  /** One {@link Fee}; carries a Fee. */
  FEE
}
