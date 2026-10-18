package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.remotesupport.backend.domain.BillingMonth;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardFlavor;
import com.remotesupport.backend.dto.ClientInvoiceMonthSendResponse;
import com.remotesupport.backend.repository.CarrierInvoiceFileRepository;
import com.remotesupport.backend.repository.ClientInvoiceLineRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The month send's order of checks over mocked collaborators (agent-sends-the-month spec, Testing
 * decisions 1): the current month, then nothing to send, then (under the lock) already sent, then
 * {@code MONTH_CHANGED}, then the carrier gate; and that a refusal stores no line and sends no
 * invoice.
 */
class ClientInvoiceMonthServiceTest {

  private static final LocalDate MONTH = BillingMonth.current();

  private final UUID agentId = UUID.randomUUID();
  private final UUID tenantId = UUID.randomUUID();
  private final AuthenticatedPrincipal principal = mock(AuthenticatedPrincipal.class);

  private ContractRepository contractRepository;
  private ClientInvoiceDrafts drafts;
  private ClientInvoiceRepository invoiceRepository;
  private CarrierInvoiceFileRepository fileRepository;
  private ClientInvoiceService invoiceService;
  private ClientInvoiceMonthService monthService;

  @BeforeEach
  void setUp() {
    when(principal.tenantId()).thenReturn(tenantId);
    contractRepository = mock(ContractRepository.class);
    drafts = mock(ClientInvoiceDrafts.class);
    invoiceRepository = mock(ClientInvoiceRepository.class);
    fileRepository = mock(CarrierInvoiceFileRepository.class);
    invoiceService =
        spy(
            new ClientInvoiceService(
                invoiceRepository,
                contractRepository,
                mock(FeeRepository.class),
                mock(ContractAmountService.class),
                fileRepository,
                mock(ClientInvoiceLineRepository.class),
                mock(ClientInvoicePdfRenderer.class),
                mock(AgentInvoiceService.class),
                mock(ObjectStoreAvailability.class)));
    monthService = new ClientInvoiceMonthService(contractRepository, drafts, invoiceService);
  }

  private ClientInvoice invoice(boolean everSent, boolean ready) {
    ClientInvoice invoice = new ClientInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setBillingMonth(MONTH);
    invoice.setFirstSentAt(everSent ? Instant.now().minusSeconds(60) : null);
    SimCard sim = new SimCard();
    sim.setId(UUID.randomUUID());
    sim.setNumber("+1-202-555-0" + (100 + (int) (Math.random() * 899)));
    sim.setFlavor(SimCardFlavor.POSTPAID);
    sim.setMonthlyFeeAmount(BigDecimal.TEN);
    ClientInvoiceService.ResolvedLine line =
        new ClientInvoiceService.ResolvedLine(
            com.remotesupport.backend.domain.ClientInvoiceLineKind.POSTPAID_SIM,
            sim,
            null,
            BigDecimal.TEN,
            BigDecimal.TEN,
            null);
    doReturn(List.of(line)).when(invoiceService).lines(invoice);
    com.remotesupport.backend.domain.CarrierInvoiceFile file = new com.remotesupport.backend.domain.CarrierInvoiceFile();
    file.setSimCard(sim);
    file.setId(UUID.randomUUID());
    when(fileRepository.findByClientInvoiceIdOrderByUploadedAtAsc(invoice.getId()))
        .thenReturn(ready ? List.of(file) : List.of());
    return invoice;
  }

  private void lockedMonthHolds(ClientInvoice... invoices) {
    when(invoiceRepository.findMonthForUpdate(agentId, MONTH)).thenReturn(List.of(invoices));
  }

  private void agentHoldsOneContract() {
    when(contractRepository.findByTenantIdAndAgentIdOrderByCreatedAtAsc(tenantId, agentId))
        .thenReturn(List.of(mock(Contract.class)));
  }

  @Test
  void anotherMonthIsRefusedBeforeAnythingIsReadOrWritten() {
    assertThatThrownBy(() -> monthService.send(agentId, MONTH.minusMonths(1), Optional.empty(), principal))
        .isInstanceOfSatisfying(
            ClientInvoiceConflictException.class,
            e -> assertThat(e.reason()).isEqualTo(ClientInvoiceConflictException.Reason.NOT_THE_CURRENT_MONTH));
    assertThatThrownBy(() -> monthService.send(agentId, MONTH.plusMonths(1), Optional.empty(), principal))
        .isInstanceOf(ClientInvoiceConflictException.class);

    verifyNoInteractions(contractRepository, drafts, invoiceRepository);
  }

  @Test
  void anAgentWithNoContractIsNothingToSendAndNoDraftIsCreated() {
    when(contractRepository.findByTenantIdAndAgentIdOrderByCreatedAtAsc(tenantId, agentId)).thenReturn(List.of());

    assertThatThrownBy(() -> monthService.send(agentId, MONTH, Optional.empty(), principal))
        .isInstanceOfSatisfying(
            ClientInvoiceConflictException.class,
            e -> assertThat(e.reason()).isEqualTo(ClientInvoiceConflictException.Reason.NOTHING_TO_SEND));

    verifyNoInteractions(drafts, invoiceRepository);
  }

  @Test
  void everyContractGetsItsDraftBeforeTheMonthIsLocked() {
    Contract one = mock(Contract.class);
    Contract two = mock(Contract.class);
    when(contractRepository.findByTenantIdAndAgentIdOrderByCreatedAtAsc(tenantId, agentId))
        .thenReturn(List.of(one, two));
    ClientInvoice first = invoice(false, true);
    ClientInvoice second = invoice(false, true);
    lockedMonthHolds(first, second);
    doNothing().when(invoiceService).markSent(any(), any(), any());

    ClientInvoiceMonthSendResponse sent = monthService.send(agentId, MONTH, Optional.empty(), principal);

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(drafts, invoiceRepository);
    order.verify(drafts).getOrCreate(one, MONTH, principal);
    order.verify(drafts).getOrCreate(two, MONTH, principal);
    order.verify(invoiceRepository).findMonthForUpdate(agentId, MONTH);
    assertThat(sent.billingMonth()).isEqualTo(MONTH);
    assertThat(sent.invoiceIds()).containsExactly(first.getId(), second.getId());
  }

  @Test
  void aReadyMonthSendsEveryInvoiceAtOneInstant() {
    agentHoldsOneContract();
    ClientInvoice first = invoice(false, true);
    ClientInvoice second = invoice(false, true);
    lockedMonthHolds(first, second);
    doNothing().when(invoiceService).markSent(any(), any(), any());

    ClientInvoiceMonthSendResponse sent =
        monthService.send(agentId, MONTH, Optional.of(Set.of(first.getId(), second.getId())), principal);

    org.mockito.ArgumentCaptor<Instant> instants = org.mockito.ArgumentCaptor.forClass(Instant.class);
    verify(invoiceService).markSent(eq(first), instants.capture(), eq(principal));
    verify(invoiceService).markSent(eq(second), instants.capture(), eq(principal));
    assertThat(instants.getAllValues()).hasSize(2).containsOnly(sent.sentAt());
  }

  /** Which refusal wins when several apply: the first in the spec's order. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("refusals")
  void refusalsComeInTheSpecsOrderAndNoInvoiceIsSent(
      String name,
      boolean someInvoiceWasSent,
      boolean expectedDiffers,
      boolean someInvoiceNotReady,
      ClientInvoiceConflictException.Reason expected) {
    agentHoldsOneContract();
    ClientInvoice first = invoice(someInvoiceWasSent, !someInvoiceNotReady);
    ClientInvoice second = invoice(false, true);
    lockedMonthHolds(first, second);
    Optional<Set<UUID>> expectedIds =
        expectedDiffers ? Optional.of(Set.of(first.getId())) : Optional.of(Set.of(first.getId(), second.getId()));

    assertThatThrownBy(() -> monthService.send(agentId, MONTH, expectedIds, principal))
        .isInstanceOfSatisfying(
            ClientInvoiceConflictException.class, e -> assertThat(e.reason()).isEqualTo(expected));

    verify(invoiceService, never()).markSent(any(), any(), any());
  }

  static Stream<Arguments> refusals() {
    return Stream.of(
        Arguments.of(
            "already sent beats everything",
            true,
            true,
            true,
            ClientInvoiceConflictException.Reason.MONTH_ALREADY_SENT),
        Arguments.of(
            "changed beats the carrier gate",
            false,
            true,
            true,
            ClientInvoiceConflictException.Reason.MONTH_CHANGED),
        Arguments.of(
            "the carrier gate last",
            false,
            false,
            true,
            ClientInvoiceConflictException.Reason.CARRIER_INVOICES_MISSING));
  }

  @Test
  void theCarrierGateNamesEveryNotReadyInvoiceNotJustTheFirst() {
    agentHoldsOneContract();
    ClientInvoice notReadyOne = invoice(false, false);
    ClientInvoice ready = invoice(false, true);
    ClientInvoice notReadyTwo = invoice(false, false);
    lockedMonthHolds(notReadyOne, ready, notReadyTwo);

    assertThatThrownBy(() -> monthService.send(agentId, MONTH, Optional.empty(), principal))
        .isInstanceOfSatisfying(
            ClientInvoiceConflictException.class,
            e -> assertThat(e.invoiceIds()).containsExactly(notReadyOne.getId(), notReadyTwo.getId()));

    verify(invoiceService, never()).markSent(any(), any(), any());
  }
}
