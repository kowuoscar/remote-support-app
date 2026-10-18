package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.remotesupport.backend.domain.CarrierInvoiceFile;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.Tenant;
import com.remotesupport.backend.repository.CarrierInvoiceFileRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

/** {@link CarrierInvoiceFileService} over a mocked storage and a mocked {@link ClientInvoiceService}. */
class CarrierInvoiceFileServiceTest {

  private CarrierInvoiceFileStorage storage;
  private ClientInvoiceService clientInvoiceService;
  private ObjectStoreAvailability availability;
  private CarrierInvoiceFileService service;
  private ClientInvoice invoice;
  private final MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", "abc".getBytes());
  private final AuthenticatedPrincipal principal = mock(AuthenticatedPrincipal.class);
  private final UUID tenantId = UUID.randomUUID();
  private final UUID invoiceId = UUID.randomUUID();
  private final UUID simCardId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    storage = mock(CarrierInvoiceFileStorage.class);
    clientInvoiceService = mock(ClientInvoiceService.class);
    availability = mock(ObjectStoreAvailability.class);
    when(availability.isAvailable()).thenReturn(true);
    service =
        new CarrierInvoiceFileService(storage, clientInvoiceService, mock(CarrierInvoiceFileRepository.class), availability);
    invoice = mock(ClientInvoice.class);
    Tenant tenant = mock(Tenant.class);
    when(tenant.getId()).thenReturn(tenantId);
    when(invoice.getTenant()).thenReturn(tenant);
    when(invoice.getId()).thenReturn(invoiceId);
    when(storage.keyFor(eq(tenantId), eq(invoiceId), any(UUID.class))).thenReturn("the/key");
  }

  @Test
  void anExceptionFromRecordAttachedFileDeletesTheKeyAndRethrows() {
    ConflictException refused = new ConflictException("sent");
    when(clientInvoiceService.recordAttachedFile(any(), any(), any(), any(), any(), any())).thenThrow(refused);

    assertThatThrownBy(() -> service.attach(invoice, file, simCardId, principal)).isSameAs(refused);

    verify(storage).put("the/key", file);
    verify(storage).delete("the/key");
  }

  @Test
  void aFailingDeleteStillRethrowsTheOriginalException() {
    ConflictException refused = new ConflictException("sent");
    when(clientInvoiceService.recordAttachedFile(any(), any(), any(), any(), any(), any())).thenThrow(refused);
    doThrow(new ObjectStoreUnavailableException("down")).when(storage).delete("the/key");

    assertThatThrownBy(() -> service.attach(invoice, file, simCardId, principal)).isSameAs(refused);
  }

  @Test
  void aFailingPutNeverCallsRecordAttachedFile() {
    ObjectStoreUnavailableException down = new ObjectStoreUnavailableException("down");
    doThrow(down).when(storage).put("the/key", file);

    assertThatThrownBy(() -> service.attach(invoice, file, simCardId, principal)).isSameAs(down);

    verify(clientInvoiceService, never()).recordAttachedFile(any(), any(), any(), any(), any(), any());
    verify(storage, never()).delete(any());
  }

  @Test
  void attachAnswers503WithoutCallingTheStoreWhileFileStorageIsUnavailable() {
    when(availability.isAvailable()).thenReturn(false);

    assertThatThrownBy(() -> service.attach(invoice, file, simCardId, principal)).isInstanceOf(ObjectStoreUnavailableException.class);

    verify(storage, never()).put(any(), any());
    verify(clientInvoiceService, never()).recordAttachedFile(any(), any(), any(), any(), any(), any());
  }

  @Test
  void theRowIsRecordedUnderTheSameFileIdAndKeyTheObjectWasWrittenTo() {
    service.attach(invoice, file, simCardId, principal);

    ArgumentCaptor<UUID> fileId = ArgumentCaptor.forClass(UUID.class);
    verify(storage).keyFor(eq(tenantId), eq(invoiceId), fileId.capture());
    verify(clientInvoiceService).recordAttachedFile(invoice, fileId.getValue(), "the/key", file, simCardId, principal);
    assertThat(fileId.getValue()).isNotNull();
  }

  @Test
  void downloadAnswers503WithoutCallingTheStoreWhileFileStorageIsUnavailable() {
    CarrierInvoiceFileRepository repository = mock(CarrierInvoiceFileRepository.class);
    CarrierInvoiceFile row = mock(CarrierInvoiceFile.class);
    UUID fileId = UUID.randomUUID();
    when(repository.findByIdAndClientInvoiceId(fileId, invoiceId)).thenReturn(java.util.Optional.of(row));
    when(availability.isAvailable()).thenReturn(false);
    CarrierInvoiceFileService withRows = new CarrierInvoiceFileService(storage, clientInvoiceService, repository, availability);

    assertThatThrownBy(() -> withRows.download(invoice, fileId)).isInstanceOf(ObjectStoreUnavailableException.class);

    verify(storage, never()).read(any());
  }

  // --- remove ---------------------------------------------------------------------------------

  @Test
  void removeDeletesTheObjectOnlyAfterRemoveFileReturns() {
    UUID fileId = UUID.randomUUID();
    when(clientInvoiceService.removeFile(invoice, fileId, principal)).thenReturn("the/key");
    org.mockito.InOrder order = org.mockito.Mockito.inOrder(clientInvoiceService, storage);

    service.remove(invoice, fileId, principal);

    order.verify(clientInvoiceService).removeFile(invoice, fileId, principal);
    order.verify(storage).delete("the/key");
  }

  @Test
  void aFailingDeleteAfterRemoveFileLogsAWarnAndDoesNotThrow() {
    UUID fileId = UUID.randomUUID();
    when(clientInvoiceService.removeFile(invoice, fileId, principal)).thenReturn("the/key");
    doThrow(new ObjectStoreUnavailableException("down")).when(storage).delete("the/key");
    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(CarrierInvoiceFileService.class);
    ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
        new ch.qos.logback.core.read.ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      service.remove(invoice, fileId, principal);
    } finally {
      logger.detachAppender(appender);
    }

    assertThat(appender.list)
        .anySatisfy(
            e -> {
              assertThat(e.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
              assertThat(e.getFormattedMessage()).contains("no row refers to it").contains("the/key");
            });
  }

  @Test
  void anExceptionFromRemoveFileDeletesNothing() {
    UUID fileId = UUID.randomUUID();
    ConflictException refused = new ConflictException("sent");
    when(clientInvoiceService.removeFile(invoice, fileId, principal)).thenThrow(refused);

    assertThatThrownBy(() -> service.remove(invoice, fileId, principal)).isSameAs(refused);

    verify(storage, never()).delete(any());
  }

  @Test
  void removeNeverCallsTheStorageWhileFileStorageIsUnavailable() {
    when(availability.isAvailable()).thenReturn(false);

    assertThatThrownBy(() -> service.remove(invoice, UUID.randomUUID(), principal))
        .isInstanceOf(ObjectStoreUnavailableException.class);

    verify(storage, never()).delete(any());
    verify(clientInvoiceService, never()).removeFile(any(), any(), any());
  }
}
