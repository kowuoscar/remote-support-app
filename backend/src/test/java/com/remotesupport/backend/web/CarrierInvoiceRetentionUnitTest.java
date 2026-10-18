package com.remotesupport.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.remotesupport.backend.repository.CarrierInvoiceFileRepository;
import com.remotesupport.backend.repository.DueCarrierInvoiceFile;
import com.remotesupport.backend.web.CarrierInvoiceRetention.RetentionRun;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

/** {@link CarrierInvoiceRetention} over a mocked storage and a mocked repository. */
@ExtendWith(OutputCaptureExtension.class)
class CarrierInvoiceRetentionUnitTest {

  private CarrierInvoiceFileRepository repository;
  private CarrierInvoiceFileStorage storage;
  private CarrierInvoiceRetention retention;

  @BeforeEach
  void setUp() {
    repository = mock(CarrierInvoiceFileRepository.class);
    storage = mock(CarrierInvoiceFileStorage.class);
    retention = new CarrierInvoiceRetention(repository, storage, mock(PlatformTransactionManager.class));
  }

  private static DueCarrierInvoiceFile due(String key) {
    return new DueCarrierInvoiceFile(UUID.randomUUID(), key);
  }

  private static List<String> linesAbove(CapturedOutput output, String level) {
    return output.getAll().lines().filter(line -> line.contains(" " + level + " ")).toList();
  }

  @Test
  void aStoreFailureOnTheSecondOfThreeFilesMarksOnlyTheFirstAndStopsTheRun(CapturedOutput output) {
    DueCarrierInvoiceFile first = due("k1");
    DueCarrierInvoiceFile second = due("k2");
    DueCarrierInvoiceFile third = due("k3");
    when(repository.findDue(any(), any())).thenReturn(List.of(first, second, third));
    doThrow(new ObjectStoreUnavailableException("down")).when(storage).delete("k2");

    RetentionRun run = retention.runOnce();

    assertThat(run).isEqualTo(new RetentionRun(3, 1, true));
    InOrder order = inOrder(storage, repository);
    order.verify(storage).delete("k1");
    order.verify(repository).markDeleted(eq(first.id()), any(Instant.class));
    order.verify(storage).delete("k2");
    verify(repository, never()).markDeleted(eq(second.id()), any());
    verify(repository, never()).markDeleted(eq(third.id()), any());
    verify(storage, never()).delete("k3");
    assertThat(linesAbove(output, "WARN")).hasSize(1);
    assertThat(linesAbove(output, "WARN").get(0))
        .contains(
            "Carrier invoice retention stopped: object store unavailable; 1 deleted, 2 still due; next run retries");
    assertThat(linesAbove(output, "ERROR")).isEmpty();
    assertThat(linesAbove(output, "INFO")).isEmpty();
  }

  @Test
  void nothingDueMakesNoStoreCallAndLogsNothingAboveDebug(CapturedOutput output) {
    when(repository.findDue(any(), any())).thenReturn(List.of());

    RetentionRun run = retention.runOnce();

    assertThat(run).isEqualTo(new RetentionRun(0, 0, false));
    verifyNoInteractions(storage);
    verify(repository, never()).markDeleted(any(), any());
    assertThat(linesAbove(output, "INFO")).isEmpty();
    assertThat(linesAbove(output, "WARN")).isEmpty();
    assertThat(linesAbove(output, "ERROR")).isEmpty();
  }

  @Test
  void theQueryIsAskedForAtMost500FilesApprovedAtLeast30DaysAgo() {
    when(repository.findDue(any(), any())).thenReturn(List.of());
    Instant before = Instant.now();

    retention.runOnce();

    ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findDue(cutoff.capture(), page.capture());
    assertThat(page.getValue().getPageSize()).isEqualTo(500);
    assertThat(page.getValue().getOffset()).isZero();
    assertThat(cutoff.getValue())
        .isBetween(before.minus(java.time.Duration.ofDays(30)), Instant.now().minus(java.time.Duration.ofDays(30)));
  }

  @Test
  void theJobHoldsNoObjectStoreAvailabilityToProbeOrSet() {
    assertThat(Arrays.stream(CarrierInvoiceRetention.class.getDeclaredFields()).map(Field::getType))
        .doesNotContain(ObjectStoreAvailability.class);
    assertThat(Arrays.stream(CarrierInvoiceRetention.class.getDeclaredConstructors()[0].getParameterTypes()))
        .doesNotContain(ObjectStoreAvailability.class);
  }

  @Test
  void aRunThatDeletedFilesLogsExactlyOneInfoLine(CapturedOutput output) {
    when(repository.findDue(any(), any())).thenReturn(List.of(due("k1"), due("k2")));

    RetentionRun run = retention.runOnce();

    assertThat(run).isEqualTo(new RetentionRun(2, 2, false));
    assertThat(linesAbove(output, "INFO")).hasSize(1);
    assertThat(linesAbove(output, "INFO").get(0)).contains("Carrier invoice retention deleted 2 files");
    assertThat(linesAbove(output, "WARN")).isEmpty();
  }
}
