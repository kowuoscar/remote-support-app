package com.remotesupport.backend.domain;

/**
 * The currencies an Agent (and therefore a Contract) can run in this iteration — one per
 * {@link Country} in the fixed lookup. See {@code docs/adr/} for why the lookup is a small fixed
 * enum rather than a full ISO-4217 catalog.
 */
public enum Currency {
  USD,
  EUR,
  GBP,
  MXN,
  PHP
}
