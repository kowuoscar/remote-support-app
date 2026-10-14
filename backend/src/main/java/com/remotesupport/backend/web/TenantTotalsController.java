package com.remotesupport.backend.web;

import com.remotesupport.backend.dto.TenantTotalsResponse;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Manager's Tenant-wide money totals for the current billing month (real-manager-dashboard
 * spec). Manager-only at the matcher level (SecurityConfig); takes no parameter — the Tenant comes
 * from the principal and the month from {@code BillingMonth.current()}. Read-only.
 */
@RestController
public class TenantTotalsController {

  private static final Logger log = LoggerFactory.getLogger(TenantTotalsController.class);

  private final TenantTotalsService tenantTotalsService;

  public TenantTotalsController(TenantTotalsService tenantTotalsService) {
    this.tenantTotalsService = tenantTotalsService;
  }

  @GetMapping("/api/tenant-totals")
  public TenantTotalsResponse read(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    TenantTotalsResponse totals = tenantTotalsService.currentMonth(principal.tenantId());
    log.debug(
        "tenant totals read tenantId={} billedCurrencies={} payoutCurrencies={}",
        principal.tenantId(),
        totals.billed().size(),
        totals.payout().size());
    return totals;
  }
}
