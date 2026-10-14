package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.AgentInvoiceStatus;
import com.remotesupport.backend.domain.BillingMonth;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.dto.TenantTotalsResponse;
import com.remotesupport.backend.dto.TenantTotalsResponse.CurrencyAmount;
import com.remotesupport.backend.repository.AgentInvoiceRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.CurrencyTotalRow;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Selects and sums the money the Manager's dashboard shows (real-manager-dashboard spec, "The
 * money definitions"): the current UTC billing month's Client Invoices that are {@code SENT} or
 * {@code APPROVED} (billed) and Agent Invoices that are {@code APPROVED} or {@code PAID} (payout),
 * per currency. Every counted status is past the send, so only stored totals are read (ADR 0003,
 * ADR 0004); nothing is written and no invoice is created.
 */
@Component
public class TenantTotalsService {

  private static final List<ClientInvoiceStatus> BILLED_STATUSES =
      List.of(ClientInvoiceStatus.SENT, ClientInvoiceStatus.APPROVED);
  private static final List<AgentInvoiceStatus> PAYOUT_STATUSES =
      List.of(AgentInvoiceStatus.APPROVED, AgentInvoiceStatus.PAID);

  private static final Comparator<CurrencyAmount> BY_CURRENCY_CODE =
      Comparator.comparing(CurrencyAmount::currency);

  private final ClientInvoiceRepository clientInvoiceRepository;
  private final AgentInvoiceRepository agentInvoiceRepository;

  public TenantTotalsService(
      ClientInvoiceRepository clientInvoiceRepository, AgentInvoiceRepository agentInvoiceRepository) {
    this.clientInvoiceRepository = clientInvoiceRepository;
    this.agentInvoiceRepository = agentInvoiceRepository;
  }

  @Transactional(readOnly = true)
  public TenantTotalsResponse currentMonth(UUID tenantId) {
    LocalDate billingMonth = BillingMonth.current();
    return new TenantTotalsResponse(
        billingMonth,
        ordered(clientInvoiceRepository.sumTotalsByCurrency(tenantId, billingMonth, BILLED_STATUSES)),
        ordered(agentInvoiceRepository.sumTotalsByCurrency(tenantId, billingMonth, PAYOUT_STATUSES)));
  }

  private static List<CurrencyAmount> ordered(List<CurrencyTotalRow> rows) {
    return rows.stream()
        .map(row -> new CurrencyAmount(row.currency().name(), row.amount()))
        .sorted(BY_CURRENCY_CODE)
        .toList();
  }
}
