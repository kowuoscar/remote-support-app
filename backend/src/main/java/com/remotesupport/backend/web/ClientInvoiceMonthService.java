package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.BillingMonth;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.dto.ClientInvoiceMonthSendResponse;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The month send (agent-sends-the-month spec, "The month send"): every current-month Client
 * Invoice of an Agent's Contracts sent together, all or nothing. This class takes the steps that
 * must run outside a transaction: the current-month check, then completing the month (the draft
 * of every Contract the Agent holds, each get-or-created in its own short transaction as a page
 * view does, so a creation race settled by the V11 constraint never aborts the send). The locked,
 * transactional half is {@link ClientInvoiceService#sendMonth}, which sends the invoices that
 * exist once it holds their locks: a Contract created after this class listed them has no invoice
 * yet and is, by definition, added after the month was sent.
 */
@Component
public class ClientInvoiceMonthService {

  private final ContractRepository contractRepository;
  private final ClientInvoiceDrafts clientInvoiceDrafts;
  private final ClientInvoiceService clientInvoiceService;

  public ClientInvoiceMonthService(
      ContractRepository contractRepository,
      ClientInvoiceDrafts clientInvoiceDrafts,
      ClientInvoiceService clientInvoiceService) {
    this.contractRepository = contractRepository;
    this.clientInvoiceDrafts = clientInvoiceDrafts;
    this.clientInvoiceService = clientInvoiceService;
  }

  /**
   * Sends the Agent's month. Refusals are coded {@link ClientInvoiceConflictException}s, in order:
   * {@code NOT_THE_CURRENT_MONTH}, {@code NOTHING_TO_SEND}, then the locked half's {@code
   * MONTH_ALREADY_SENT}, {@code MONTH_CHANGED}, {@code CARRIER_INVOICES_MISSING}; none writes
   * anything but the drafts of the Contracts (what any page view would have created).
   */
  public ClientInvoiceMonthSendResponse send(
      UUID agentId,
      LocalDate billingMonth,
      Optional<Set<UUID>> expectedInvoiceIds,
      AuthenticatedPrincipal principal) {
    if (!billingMonth.equals(BillingMonth.current())) {
      throw new ClientInvoiceConflictException(
          ClientInvoiceConflictException.Reason.NOT_THE_CURRENT_MONTH,
          "Only the current month can be sent");
    }

    List<Contract> contracts =
        contractRepository.findByTenantIdAndAgentIdOrderByCreatedAtAsc(principal.tenantId(), agentId);
    if (contracts.isEmpty()) {
      throw new ClientInvoiceConflictException(
          ClientInvoiceConflictException.Reason.NOTHING_TO_SEND, "This Agent holds no Contract to send");
    }
    for (Contract contract : contracts) {
      clientInvoiceDrafts.getOrCreate(contract, billingMonth, principal);
    }

    ClientInvoiceService.MonthSend sent =
        clientInvoiceService.sendMonth(agentId, billingMonth, expectedInvoiceIds, principal);
    return new ClientInvoiceMonthSendResponse(billingMonth, sent.sentAt(), sent.invoiceIds());
  }
}
