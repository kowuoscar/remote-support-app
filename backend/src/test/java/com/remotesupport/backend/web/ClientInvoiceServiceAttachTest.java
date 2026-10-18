package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.Tenant;
import com.remotesupport.backend.repository.CarrierInvoiceFileRepository;
import com.remotesupport.backend.repository.ClientInvoiceLineRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

/** {@link ClientInvoiceService#recordAttachedFile} over mocked repositories: the refusals that guard a SIM's one file. */
class ClientInvoiceServiceAttachTest {

  private final UUID invoiceId = UUID.randomUUID();
  private final UUID simId = UUID.randomUUID();
  private final MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", "abc".getBytes());
  private final AuthenticatedPrincipal principal = mock(AuthenticatedPrincipal.class);

  private CarrierInvoiceFileRepository fileRepository;
  private ClientInvoiceService service;
  private ClientInvoice invoice;
  private SimCard sim;

  @BeforeEach
  void setUp() {
    ClientInvoiceRepository invoiceRepository = mock(ClientInvoiceRepository.class);
    fileRepository = mock(CarrierInvoiceFileRepository.class);
    ContractAmountService amounts = mock(ContractAmountService.class);
    ClientInvoiceLineRepository lineRepository = mock(ClientInvoiceLineRepository.class);
    service =
        new ClientInvoiceService(
            invoiceRepository,
            mock(ContractRepository.class),
            mock(FeeRepository.class),
            amounts,
            fileRepository,
            lineRepository,
            mock(ClientInvoicePdfRenderer.class),
            mock(AgentInvoiceService.class),
            mock(ObjectStoreAvailability.class));

    Contract contract = mock(Contract.class);
    when(contract.getId()).thenReturn(UUID.randomUUID());
    com.remotesupport.backend.domain.Client client = mock(com.remotesupport.backend.domain.Client.class);
    when(client.getName()).thenReturn("Acme");
    when(contract.getClient()).thenReturn(client);
    invoice = new ClientInvoice();
    invoice.setId(invoiceId);
    invoice.setTenant(mock(Tenant.class));
    invoice.setContract(contract);
    invoice.setStatus(ClientInvoiceStatus.DRAFT);
    invoice.setBillingMonth(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1));
    when(invoiceRepository.findByIdForUpdate(invoiceId)).thenReturn(Optional.of(invoice));
    when(principal.role()).thenReturn("MANAGER");

    sim = new SimCard();
    sim.setId(simId);
    sim.setNumber("+1-202-555-0102");
    sim.setFlavor(com.remotesupport.backend.domain.SimCardFlavor.POSTPAID);
    sim.setMonthlyFeeAmount(java.math.BigDecimal.TEN);
    when(amounts.billablePostpaidSims(any(), any())).thenReturn(List.of(sim));
  }

  @Test
  void aUniqueConstraintViolationOnSaveIsTheCodedAlreadyAttached409() {
    when(fileRepository.saveAndFlush(any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("duplicate key value violates unique constraint \"uq_carrier_invoice_files_invoice_sim\"")));

    assertThatThrownBy(() -> service.recordAttachedFile(invoice, UUID.randomUUID(), "k", file, simId, principal))
        .isInstanceOfSatisfying(
            ClientInvoiceConflictException.class,
            e -> assertThat(e.reason()).isEqualTo(ClientInvoiceConflictException.Reason.CARRIER_INVOICE_ALREADY_ATTACHED));
  }

  @Test
  void anotherIntegrityViolationIsNotMistakenForAnAlreadyAttachedSim() {
    DataIntegrityViolationException other =
        new DataIntegrityViolationException("x", new RuntimeException("violates foreign key constraint"));
    when(fileRepository.saveAndFlush(any())).thenThrow(other);

    assertThatThrownBy(() -> service.recordAttachedFile(invoice, UUID.randomUUID(), "k", file, simId, principal))
        .isSameAs(other);
  }

  @Test
  void aSimThatAlreadyHasItsFileIsTheCodedAlreadyAttached409() {
    when(fileRepository.existsByClientInvoiceIdAndSimCardId(invoiceId, simId)).thenReturn(true);

    assertThatThrownBy(() -> service.recordAttachedFile(invoice, UUID.randomUUID(), "k", file, simId, principal))
        .isInstanceOfSatisfying(
            ClientInvoiceConflictException.class,
            e -> assertThat(e.reason()).isEqualTo(ClientInvoiceConflictException.Reason.CARRIER_INVOICE_ALREADY_ATTACHED));
  }

  @Test
  void aSimWithNoPostpaidLineOnTheInvoiceIsA400() {
    assertThatThrownBy(() -> service.recordAttachedFile(invoice, UUID.randomUUID(), "k", file, UUID.randomUUID(), principal))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Not a Postpaid SIM line of this Client Invoice");
  }

  @Test
  void theNameCarriesTheCurrentNameOfAnArchivedCarrier() {
    com.remotesupport.backend.domain.Carrier carrier = new com.remotesupport.backend.domain.Carrier();
    carrier.setName("Old Carrier");
    carrier.setArchivedAt(java.time.Instant.now());
    sim.setCarrier(carrier);

    service.recordAttachedFile(invoice, UUID.randomUUID(), "k", file, simId, principal);

    org.mockito.ArgumentCaptor<com.remotesupport.backend.domain.CarrierInvoiceFile> saved =
        org.mockito.ArgumentCaptor.forClass(com.remotesupport.backend.domain.CarrierInvoiceFile.class);
    org.mockito.Mockito.verify(fileRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getFilename())
        .isEqualTo(
            "Acme - "
                + java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH)
                    .format(invoice.getBillingMonth())
                + " - Old Carrier - +1-202-555-0102.pdf");
  }
}
