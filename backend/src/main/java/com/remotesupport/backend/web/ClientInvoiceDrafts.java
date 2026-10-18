package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * The one get-or-create of a Contract's draft Client Invoice for a billing month, shared by the
 * Contract-addressed routes ({@link ClientInvoiceController}) and the month send ({@link
 * ClientInvoiceMonthService}). A second, concurrent first access creating the same month's draft is
 * settled by the unique {@code (contract_id, billing_month)} constraint (V11 migration), not by
 * application-level locking.
 *
 * <p>Deliberately not {@code @Transactional}: each repository call runs in its own transaction, so
 * the loser of a creation race is the only transaction that aborts and the fallback read runs in a
 * fresh one.
 */
@Component
public class ClientInvoiceDrafts {

  private final ClientInvoiceRepository clientInvoiceRepository;

  public ClientInvoiceDrafts(ClientInvoiceRepository clientInvoiceRepository) {
    this.clientInvoiceRepository = clientInvoiceRepository;
  }

  /** The Contract's invoice for {@code billingMonth}, created as a {@code DRAFT} when it has none. */
  public ClientInvoice getOrCreate(Contract contract, LocalDate billingMonth, AuthenticatedPrincipal principal) {
    return clientInvoiceRepository
        .findByContractIdAndBillingMonth(contract.getId(), billingMonth)
        .orElseGet(() -> createDraft(contract, billingMonth, principal));
  }

  private ClientInvoice createDraft(Contract contract, LocalDate billingMonth, AuthenticatedPrincipal principal) {
    ClientInvoice invoice = new ClientInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setTenant(contract.getTenant());
    invoice.setContract(contract);
    invoice.setBillingMonth(billingMonth);
    invoice.setStatus(ClientInvoiceStatus.DRAFT);
    invoice.setCurrency(contract.getCurrency());
    invoice.setCreatedAt(Instant.now());

    try {
      clientInvoiceRepository.saveAndFlush(invoice);
    } catch (DataIntegrityViolationException raceLost) {
      // Another request racing to view this same Contract/month for the first time already won
      // (V11's unique (contract_id, billing_month) constraint): fall back to the row it created
      // instead of failing this request.
      return clientInvoiceRepository
          .findByContractIdAndBillingMonth(contract.getId(), billingMonth)
          .orElseThrow(() -> raceLost);
    }

    AuditLog.created("ClientInvoice", invoice.getId(), principal.userId(), principal.tenantId());
    return invoice;
  }
}
