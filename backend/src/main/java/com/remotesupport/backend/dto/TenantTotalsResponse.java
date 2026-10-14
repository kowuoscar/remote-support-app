package com.remotesupport.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * This month's billed and payout totals for the caller's Tenant (real-manager-dashboard spec):
 * {@code billingMonth} is the month both figures cover, and each list holds one entry per currency
 * with a counted invoice, ordered by currency code. Currencies are never summed together.
 */
public record TenantTotalsResponse(
    LocalDate billingMonth, List<CurrencyAmount> billed, List<CurrencyAmount> payout) {

  /** An amount with its currency. */
  public record CurrencyAmount(String currency, BigDecimal amount) {}
}
