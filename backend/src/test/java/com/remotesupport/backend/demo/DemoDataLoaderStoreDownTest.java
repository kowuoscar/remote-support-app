package com.remotesupport.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.repository.CarrierInvoiceFileRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.support.ObjectStoreTestSupport;
import com.remotesupport.backend.web.CarrierInvoiceFileStorage;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The demo must start whatever the object store's state (ADR 0007). With the store refusing every
 * write, the loader completes, logs one WARN (and attempts no write), and leaves contract US-1's current-month Client
 * Invoice a draft, because the send gate would refuse it without its Carrier Invoice Files. Like
 * {@link DemoDataLoaderApiTest}, it owns a fresh database under the {@code demo} profile; the store
 * is an endpoint nobody listens on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("demo")
class DemoDataLoaderStoreDownTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  /** What the loader logged while the context booted. */
  private static final ListAppender<ILoggingEvent> LOG = new ListAppender<>();

  static {
    POSTGRES.start();
    LOG.start();
  }

  /**
   * The loader runs as part of the boot, and Spring Boot resets the logging configuration just
   * before it builds beans, so the appender is attached by a bean, which is built after that reset
   * and before the runners.
   */
  @TestConfiguration
  static class CaptureLoaderLog {
    @PostConstruct
    void attach() {
      ((Logger) LoggerFactory.getLogger(DemoDataLoader.class)).addAppender(LOG);
      ((Logger) LoggerFactory.getLogger(CarrierInvoiceFileStorage.class)).addAppender(LOG);
    }
  }

  @DynamicPropertySource
  static void unreachableObjectStore(DynamicPropertyRegistry registry) {
    ObjectStoreTestSupport.register(registry);
    registry.add("OBJECT_STORE_ENDPOINT", () -> "http://127.0.0.1:1");
  }

  @Autowired private ClientInvoiceRepository clientInvoiceRepository;
  @Autowired private CarrierInvoiceFileRepository carrierInvoiceFileRepository;

  @Test
  void whenTheStoreRefusesTheLoaderCompletesWarnsOnceAndLeavesTheCurrentMonthInvoiceADraft() {
    LocalDate thisMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
    List<ClientInvoice> current =
        clientInvoiceRepository.findAll().stream().filter(i -> thisMonth.equals(i.getBillingMonth())).toList();

    assertThat(current).as("the loader built this month's invoices").isNotEmpty();
    assertThat(current).allMatch(i -> i.getStatus() == ClientInvoiceStatus.DRAFT);
    assertThat(carrierInvoiceFileRepository.count()).isZero();
    assertThat(clientInvoiceRepository.findAll()).anyMatch(i -> i.getStatus() == ClientInvoiceStatus.APPROVED);
    assertThat(LOG.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList())
        .hasSize(1);
    assertThat(LOG.list.stream().filter(e -> e.getLoggerName().equals(CarrierInvoiceFileStorage.class.getName())))
        .as("the loader probes the store first, so no write is attempted and logs its own ERROR")
        .isEmpty();
  }
}
