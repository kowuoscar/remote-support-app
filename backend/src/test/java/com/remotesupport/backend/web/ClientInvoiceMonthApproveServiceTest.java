package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.remotesupport.backend.domain.BillingMonth;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.repository.CarrierInvoiceFileRepository;
import com.remotesupport.backend.repository.ClientInvoiceLineRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The month approval's order of checks over mocked collaborators (manager-reviews-by-agent spec,
 * Testing decisions 3): nothing to approve before {@code MONTH_CHANGED}, and a refusal approves no
 * invoice.
 */
class ClientInvoiceMonthApproveServiceTest {

  private static final LocalDate MONTH = BillingMonth.current();

  private final UUID agentId = UUID.randomUUID();
  private final AuthenticatedPrincipal principal = mock(AuthenticatedPrincipal.class);

  private ClientInvoiceRepository invoiceRepository;
  private ClientInvoiceService invoiceService;

  @BeforeEach
  void setUp() {
    when(principal.tenantId()).thenReturn(UUID.randomUUID());
    when(principal.userId()).thenReturn(UUID.randomUUID());
    invoiceRepository = mock(ClientInvoiceRepository.class);
    invoiceService =
        spy(
            new ClientInvoiceService(
                invoiceRepository,
                mock(ContractRepository.class),
                mock(FeeRepository.class),
                mock(ContractAmountService.class),
                mock(CarrierInvoiceFileRepository.class),
                mock(ClientInvoiceLineRepository.class),
                mock(ClientInvoicePdfRenderer.class),
                mock(AgentInvoiceService.class),
                mock(ObjectStoreAvailability.class)));
  }

  private static ClientInvoice invoice(ClientInvoiceStatus status) {
    ClientInvoice invoice = new ClientInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setBillingMonth(MONTH);
    invoice.setStatus(status);
    return invoice;
  }

  private ClientInvoiceConflictException refusal(Set<UUID> expected) {
    return assertThrows(
        ClientInvoiceConflictException.class,
        () -> invoiceService.approveMonth(agentId, MONTH, expected, principal));
  }

  @Test
  void nothingToApproveIsCheckedBeforeMonthChangedAndApprovesNothing() {
    when(invoiceRepository.findMonthForUpdate(agentId, MONTH))
        .thenReturn(List.of(invoice(ClientInvoiceStatus.APPROVED), invoice(ClientInvoiceStatus.DRAFT)));

    ClientInvoiceConflictException refused = refusal(Set.of(UUID.randomUUID()));

    assertThat(refused.reason()).isEqualTo(ClientInvoiceConflictException.Reason.NOTHING_TO_APPROVE);
    verify(invoiceService, never()).markApproved(any(), any(), any());
  }

  @Test
  void aDifferentSetOfIdsIsMonthChangedAndApprovesNothing() {
    ClientInvoice first = invoice(ClientInvoiceStatus.SENT);
    ClientInvoice second = invoice(ClientInvoiceStatus.SENT);
    when(invoiceRepository.findMonthForUpdate(agentId, MONTH)).thenReturn(List.of(first, second));

    ClientInvoiceConflictException refused = refusal(Set.of(first.getId()));

    assertThat(refused.reason()).isEqualTo(ClientInvoiceConflictException.Reason.MONTH_CHANGED);
    verify(invoiceService, never()).markApproved(any(), any(), any());
    assertThat(first.getStatus()).isEqualTo(ClientInvoiceStatus.SENT);
  }

  @Test
  void onlyTheSentInvoicesOfTheMonthAreTheBatchAndAreApprovedAtOneInstant() {
    ClientInvoice sent = invoice(ClientInvoiceStatus.SENT);
    ClientInvoice approved = invoice(ClientInvoiceStatus.APPROVED);
    when(invoiceRepository.findMonthForUpdate(agentId, MONTH)).thenReturn(List.of(sent, approved));

    ClientInvoiceService.MonthApproval result =
        invoiceService.approveMonth(agentId, MONTH, Set.of(sent.getId()), principal);

    assertThat(result.invoiceIds()).containsExactly(sent.getId());
    assertThat(sent.getStatus()).isEqualTo(ClientInvoiceStatus.APPROVED);
    assertThat(sent.getApprovedAt()).isEqualTo(result.approvedAt());
    assertThat(approved.getApprovedAt()).isNull();
    verify(invoiceService).markApproved(sent, result.approvedAt(), principal);
    verify(invoiceService, never()).markApproved(approved, result.approvedAt(), principal);
    assertThat(result.approvedAt()).isBeforeOrEqualTo(Instant.now());
  }
}
