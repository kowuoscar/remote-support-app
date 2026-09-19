package com.remotesupport.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.remotesupport.backend.domain.AgentInvoice;
import com.remotesupport.backend.domain.AgentInvoiceStatus;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.SimCard;
import com.remotesupport.backend.domain.SimCardFlavor;
import com.remotesupport.backend.repository.AgentInvoiceRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ClientRepository;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.repository.RequestRepository;
import com.remotesupport.backend.repository.SimCardRepository;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.web.ContractAmountService;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The API seam the ticket's own Tests section names: boot the {@code demo} profile against a
 * fresh database and assert the story's shape, then run the loader again and assert nothing was
 * added (demo-story-loader ticket). Deliberately does <b>not</b> extend {@code IntegrationTest} —
 * that base class is {@code @ActiveProfiles("test")} and shares one static container across
 * every other backend test, and this test needs its own fresh database under a different
 * profile, so it stands up its own dedicated Testcontainers Postgres instead (same
 * "singleton container, never stopped" shape, just not shared).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DemoDataLoaderApiTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  static {
    POSTGRES.start();
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private DemoDataLoader demoDataLoader;
  @Autowired private ClientRepository clientRepository;
  @Autowired private RequestRepository requestRepository;
  @Autowired private FeeRepository feeRepository;
  @Autowired private SmartphoneRepository smartphoneRepository;
  @Autowired private SimCardRepository simCardRepository;
  @Autowired private ClientInvoiceRepository clientInvoiceRepository;
  @Autowired private AgentInvoiceRepository agentInvoiceRepository;
  @Autowired private ContractAmountService contractAmountService;

  @Test
  void theStoryCoversEveryRequestTypeAndStatusAStockUnitACancelledPostpaidSimAndFrozenInvoices() {
    List<Request> requests = requestRepository.findAll();
    Set<RequestType> types = requests.stream().map(Request::getType).collect(Collectors.toSet());
    Set<RequestStatus> statuses = requests.stream().map(Request::getStatus).collect(Collectors.toSet());
    assertThat(types).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(RequestType.class));
    assertThat(statuses).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(RequestStatus.class));

    boolean stockUnitExists =
        smartphoneRepository.findAll().stream().anyMatch(s -> s.getHoldingAgent() != null)
            || simCardRepository.findAll().stream().anyMatch(s -> s.getHoldingAgent() != null);
    assertThat(stockUnitExists).as("a unit in an Agent's Stock").isTrue();

    List<SimCard> cancelledPostpaidSims =
        simCardRepository.findAll().stream()
            .filter(s -> s.getFlavor() == SimCardFlavor.POSTPAID && s.getCancellationEffectiveDate() != null)
            .toList();
    assertThat(cancelledPostpaidSims).as("a cancelled Postpaid SIM").isNotEmpty();

    List<ClientInvoice> clientInvoices = clientInvoiceRepository.findAll();
    assertThat(clientInvoices).anyMatch(i -> i.getStatus() == ClientInvoiceStatus.SENT);
    List<ClientInvoice> approvedClientInvoices =
        clientInvoices.stream().filter(i -> i.getStatus() == ClientInvoiceStatus.APPROVED).toList();
    assertThat(approvedClientInvoices).as("an approved Client Invoice from a past month").isNotEmpty();
    ClientInvoice approvedClientInvoice = approvedClientInvoices.get(0);
    assertThat(approvedClientInvoice.getSnapshotBaseAmount())
        .as("frozen base amount equals what ContractAmountService computes for that month")
        .isEqualByComparingTo(
            contractAmountService.baseAmount(
                approvedClientInvoice.getContract().getId(), approvedClientInvoice.getBillingMonth()));

    List<AgentInvoice> agentInvoices = agentInvoiceRepository.findAll();
    assertThat(agentInvoices).anyMatch(i -> i.getStatus() == AgentInvoiceStatus.SENT);
    List<AgentInvoice> paidAgentInvoices =
        agentInvoices.stream().filter(i -> i.getStatus() == AgentInvoiceStatus.PAID).toList();
    assertThat(paidAgentInvoices).as("a paid Agent Invoice from a past month").isNotEmpty();
    for (AgentInvoice invoice : paidAgentInvoices) {
      BigDecimal total =
          invoice
              .getSnapshotLocalSupportFees()
              .add(invoice.getSnapshotSalary())
              .add(invoice.getSnapshotRolloutAdvanceRepayment())
              .add(invoice.getSnapshotRolloutAdvanceNewAdvance());
      assertThat(total).as("every snapshot line is frozen (non-null) on a paid invoice").isNotNull();
    }

    // Second start: idempotent, writes nothing more.
    long requestCount = requestRepository.count();
    long feeCount = feeRepository.count();
    long smartphoneCount = smartphoneRepository.count();
    long simCardCount = simCardRepository.count();
    long clientInvoiceCount = clientInvoiceRepository.count();
    long agentInvoiceCount = agentInvoiceRepository.count();
    long clientCount = clientRepository.count();

    demoDataLoader.run(new DefaultApplicationArguments());

    assertThat(requestRepository.count()).isEqualTo(requestCount);
    assertThat(feeRepository.count()).isEqualTo(feeCount);
    assertThat(smartphoneRepository.count()).isEqualTo(smartphoneCount);
    assertThat(simCardRepository.count()).isEqualTo(simCardCount);
    assertThat(clientInvoiceRepository.count()).isEqualTo(clientInvoiceCount);
    assertThat(agentInvoiceRepository.count()).isEqualTo(agentInvoiceCount);
    assertThat(clientRepository.count()).isEqualTo(clientCount);
  }

  @Test
  void theBackendStillRespondsWithTheDemoProfileActive() throws Exception {
    mockMvc.perform(get("/api/health")).andExpect(status().isOk());
  }
}
