package com.remotesupport.backend.demo;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.AgentInvoice;
import com.remotesupport.backend.domain.AgentInvoiceStatus;
import com.remotesupport.backend.domain.Client;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceFeeSnapshot;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.domain.Currency;
import com.remotesupport.backend.domain.Disposition;
import com.remotesupport.backend.domain.Fee;
import com.remotesupport.backend.domain.FeeType;
import com.remotesupport.backend.domain.Request;
import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.RequestType;
import com.remotesupport.backend.domain.SimCardFlavor;
import com.remotesupport.backend.domain.SmartphoneOwner;
import com.remotesupport.backend.domain.SmartphoneStatus;
import com.remotesupport.backend.domain.StandingAmountType;
import com.remotesupport.backend.domain.Tester;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.dto.AgentCreateRequest;
import com.remotesupport.backend.dto.CarrierCreateRequest;
import com.remotesupport.backend.dto.CarrierOfferRequest;
import com.remotesupport.backend.dto.CarrierResponse;
import com.remotesupport.backend.dto.ClientCreateRequest;
import com.remotesupport.backend.dto.ContractCreateRequest;
import com.remotesupport.backend.dto.FeeCreateRequest;
import com.remotesupport.backend.dto.RequestApprovalRequest;
import com.remotesupport.backend.dto.RequestApprovalRequest.UnitDisposition;
import com.remotesupport.backend.dto.RequestCreateRequest;
import com.remotesupport.backend.dto.RequestRejectRequest;
import com.remotesupport.backend.dto.RequestResponse;
import com.remotesupport.backend.dto.RequestStatusUpdateRequest;
import com.remotesupport.backend.dto.SimCardCancellationRequest;
import com.remotesupport.backend.dto.SimCardCreateRequest;
import com.remotesupport.backend.dto.SimCardInstalledInUpdateRequest;
import com.remotesupport.backend.dto.SmartphoneCreateRequest;
import com.remotesupport.backend.dto.SmartphoneStatusUpdateRequest;
import com.remotesupport.backend.dto.TesterCreateRequest;
import com.remotesupport.backend.repository.AgentInvoiceRepository;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.ClientInvoiceFeeSnapshotRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ClientRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.FeeRepository;
import com.remotesupport.backend.repository.RequestRepository;
import com.remotesupport.backend.repository.TenantRepository;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import com.remotesupport.backend.web.AgentController;
import com.remotesupport.backend.web.AgentInvoiceController;
import com.remotesupport.backend.web.AgentInvoiceService;
import com.remotesupport.backend.web.CarrierController;
import com.remotesupport.backend.web.CarrierOfferController;
import com.remotesupport.backend.web.ClientController;
import com.remotesupport.backend.web.ClientInvoiceController;
import com.remotesupport.backend.web.ContractAmountService;
import com.remotesupport.backend.web.ContractController;
import com.remotesupport.backend.web.FeeController;
import com.remotesupport.backend.web.RequestByIdController;
import com.remotesupport.backend.web.RequestController;
import com.remotesupport.backend.web.SimCardController;
import com.remotesupport.backend.web.SmartphoneController;
import com.remotesupport.backend.web.StandingAmountService;
import com.remotesupport.backend.web.TesterController;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the demo story on startup, once, for local use only (demo-data-story spec.md Solution's
 * "Demo story (loader)"; demo-story-loader ticket). Active only under the {@code demo} Spring
 * profile — never in {@code test} (backend integration tests) or the plain/default profile the
 * committed e2e flow runs the backend under — so it never runs anywhere the trimmed Flyway
 * baseline ({@code trim-seed-to-test-baseline} ticket) needs to stay clean.
 *
 * <p><b>Idempotent by a fixed marker id.</b> {@link #CLIENT_SOLSTICE_ID} is the one anchor this
 * loader assigns a fixed, recognisable id to (every other row it creates gets a real,
 * randomly-generated id from the same controller/service a person or the API would use) — a
 * restart that finds a Client with that id already present skips straight through, writing
 * nothing (ticket AC).
 *
 * <p><b>Current-month activity goes through the application's own services.</b> Every Request,
 * Fee, approval, completion and this month's two invoices are built by calling the real
 * controllers/services directly as Spring beans (bypassing only the HTTP/JSON layer, not the
 * behaviour it wraps) with a constructed {@link AuthenticatedPrincipal} for the acting role —
 * every one of those methods already takes the caller's principal as a plain parameter rather
 * than reading it off {@code SecurityContextHolder}, which is exactly what makes this possible.
 * So every Fleet effect, snapshot and audit event this month is whatever the app itself would
 * have produced for a real user clicking through the UI.
 *
 * <p><b>Past months are written directly.</b> {@code createdAt}/{@code billingMonth} on a Fee or
 * Request, and a Client/Agent Invoice's own billing month, are always "now" inside the real
 * create paths — there is no backdating control anywhere in the app — so the two prior months'
 * invoice history ({@link #writePastClientInvoice}/{@link #writePastAgentInvoice}) constructs
 * those rows directly, but computes their frozen numbers with the exact same {@link
 * ContractAmountService}/{@link AgentInvoiceService} the real send/approve/mark-paid transitions
 * use — only the cosmetic {@code sentAt}/{@code approvedAt}/{@code paidAt} timestamps are
 * overwritten afterward, so the past months read as history rather than all happening "today".
 */
@Component
@Profile("demo")
public class DemoDataLoader implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

  // --- Fixed anchors from the trimmed Flyway baseline (trim-seed-to-test-baseline ticket) ---
  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID MANAGER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID JORDAN_AGENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
  private static final UUID JORDAN_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID US_ATT_CARRIER_ID = UUID.fromString("c0000000-0000-0000-0000-000000000001");
  private static final UUID US_TMOBILE_CARRIER_ID = UUID.fromString("c0000000-0000-0000-0000-000000000002");
  private static final UUID US_VERIZON_CARRIER_ID = UUID.fromString("c0000000-0000-0000-0000-000000000003");
  private static final UUID US_ATT_TOPUP_25_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
  private static final UUID US_TMOBILE_TOPUP_5GB_ID = UUID.fromString("d0000000-0000-0000-0000-000000000003");
  private static final UUID US_ATT_PLAN_STARTER_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");

  // --- The one fixed, recognisable anchor this loader itself assigns — also the idempotency marker ---
  private static final UUID CLIENT_SOLSTICE_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");

  // --- Demo-only logins, documented in README.md and the docker-compose header ---
  public static final String PRIYA_USERNAME = "priya.shah@example.com";
  public static final String PRIYA_PASSWORD = "PriyaDemo123!";
  public static final String DANA_USERNAME = "dana.whitfield@solsticeretail.example";
  public static final String TESTER_PASSWORD = "SolsticeDemo123!";
  public static final String NOAH_USERNAME = "noah.kim@harborline.example";
  public static final String HARBOR_TESTER_PASSWORD = "HarborDemo123!";

  private final TenantRepository tenantRepository;
  private final ClientRepository clientRepository;
  private final AgentRepository agentRepository;
  private final ContractRepository contractRepository;
  private final TesterRepository testerRepository;
  private final UserRepository userRepository;
  private final RequestRepository requestRepository;
  private final FeeRepository feeRepository;
  private final ClientInvoiceRepository clientInvoiceRepository;
  private final ClientInvoiceFeeSnapshotRepository clientInvoiceFeeSnapshotRepository;
  private final AgentInvoiceRepository agentInvoiceRepository;

  private final ClientController clientController;
  private final ContractController contractController;
  private final TesterController testerController;
  private final AgentController agentController;
  private final CarrierController carrierController;
  private final CarrierOfferController carrierOfferController;
  private final SmartphoneController smartphoneController;
  private final SimCardController simCardController;
  private final RequestController requestController;
  private final RequestByIdController requestByIdController;
  private final FeeController feeController;
  private final ClientInvoiceController clientInvoiceController;
  private final AgentInvoiceController agentInvoiceController;
  private final AgentInvoiceService agentInvoiceService;
  private final StandingAmountService standingAmountService;
  private final ContractAmountService contractAmountService;

  public DemoDataLoader(
      TenantRepository tenantRepository,
      ClientRepository clientRepository,
      AgentRepository agentRepository,
      ContractRepository contractRepository,
      TesterRepository testerRepository,
      UserRepository userRepository,
      RequestRepository requestRepository,
      FeeRepository feeRepository,
      ClientInvoiceRepository clientInvoiceRepository,
      ClientInvoiceFeeSnapshotRepository clientInvoiceFeeSnapshotRepository,
      AgentInvoiceRepository agentInvoiceRepository,
      ClientController clientController,
      ContractController contractController,
      TesterController testerController,
      AgentController agentController,
      CarrierController carrierController,
      CarrierOfferController carrierOfferController,
      SmartphoneController smartphoneController,
      SimCardController simCardController,
      RequestController requestController,
      RequestByIdController requestByIdController,
      FeeController feeController,
      ClientInvoiceController clientInvoiceController,
      AgentInvoiceController agentInvoiceController,
      AgentInvoiceService agentInvoiceService,
      StandingAmountService standingAmountService,
      ContractAmountService contractAmountService) {
    this.tenantRepository = tenantRepository;
    this.clientRepository = clientRepository;
    this.agentRepository = agentRepository;
    this.contractRepository = contractRepository;
    this.testerRepository = testerRepository;
    this.userRepository = userRepository;
    this.requestRepository = requestRepository;
    this.feeRepository = feeRepository;
    this.clientInvoiceRepository = clientInvoiceRepository;
    this.clientInvoiceFeeSnapshotRepository = clientInvoiceFeeSnapshotRepository;
    this.agentInvoiceRepository = agentInvoiceRepository;
    this.clientController = clientController;
    this.contractController = contractController;
    this.testerController = testerController;
    this.agentController = agentController;
    this.carrierController = carrierController;
    this.carrierOfferController = carrierOfferController;
    this.smartphoneController = smartphoneController;
    this.simCardController = simCardController;
    this.requestController = requestController;
    this.requestByIdController = requestByIdController;
    this.feeController = feeController;
    this.clientInvoiceController = clientInvoiceController;
    this.agentInvoiceController = agentInvoiceController;
    this.agentInvoiceService = agentInvoiceService;
    this.standingAmountService = standingAmountService;
    this.contractAmountService = contractAmountService;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (clientRepository.findById(CLIENT_SOLSTICE_ID).isPresent()) {
      log.info("Demo story already present (Client {} exists) — skipping.", CLIENT_SOLSTICE_ID);
      return;
    }
    log.info("Building the demo story...");
    Counts counts = new Counts();

    AuthenticatedPrincipal manager = principal(MANAGER_USER_ID, "manager@example.com", "MANAGER");
    AuthenticatedPrincipal jordan = principal(JORDAN_USER_ID, "agent@example.com", "AGENT");

    // --- Agents ---
    Agent priyaAgent = createPriya(manager);
    AuthenticatedPrincipal priya =
        principal(
            userRepository.findByAgentId(priyaAgent.getId()).orElseThrow().getId(), PRIYA_USERNAME, "AGENT");
    createMarcus();
    counts.agents = 3; // Jordan (existing) + Priya + Marcus

    // --- Carriers: United Kingdom catalog ---
    UkCarriers uk = createUkCarrierCatalog(manager);
    counts.carriers = 3; // EE, O2, Vodafone (archived)

    // --- Clients, Contracts, Testers ---
    UUID solsticeId = createMarkerClient();
    UUID harborId = clientController.create(new ClientCreateRequest("Harbor Line Logistics"), manager).getBody().id();
    counts.clients = 2;

    UUID contractUs1 =
        contractController.create(new ContractCreateRequest(solsticeId, JORDAN_AGENT_ID), manager).getBody().id();
    UUID contractUk1 =
        contractController.create(new ContractCreateRequest(solsticeId, priyaAgent.getId()), manager).getBody().id();
    UUID contractUs2 =
        contractController.create(new ContractCreateRequest(harborId, JORDAN_AGENT_ID), manager).getBody().id();
    counts.contracts = 3;

    UUID danaId = createTester(solsticeId, DANA_USERNAME, TESTER_PASSWORD, true, manager);
    UUID marcoId =
        createTester(solsticeId, "marco.diaz@solsticeretail.example", TESTER_PASSWORD, false, manager);
    UUID elenaId =
        createTester(solsticeId, "elena.fischer@solsticeretail.example", TESTER_PASSWORD, false, manager);
    UUID noahId = createTester(harborId, NOAH_USERNAME, HARBOR_TESTER_PASSWORD, false, manager);
    UUID sofiaId =
        createTester(harborId, "sofia.alvarez@harborline.example", HARBOR_TESTER_PASSWORD, false, manager);
    counts.testers = 5;
    AuthenticatedPrincipal dana = testerPrincipal(danaId);
    AuthenticatedPrincipal noah = testerPrincipal(noahId);

    // --- Fleets ---
    Us1Fleet us1 = buildUs1Fleet(contractUs1, manager);
    Uk1Fleet uk1 = buildUk1Fleet(contractUk1, uk, manager);
    Us2Fleet us2 = buildUs2Fleet(contractUs2, manager);
    counts.smartphones = 11;
    counts.simCards = 11;

    // --- Standing-amount history ---
    Agent jordanAgent = agentRepository.findById(JORDAN_AGENT_ID).orElseThrow();
    LocalDate month0 = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
    LocalDate month1Ago = month0.minusMonths(1);
    LocalDate month2Ago = month0.minusMonths(2);
    LocalDate month3Ago = month0.minusMonths(3);
    // Jordan's own baseline SALARY row (V5/V14 migrations) is effective at whatever month his
    // seeded `created_at` falls in — "ages ago" on a real, long-running demo volume, but "this
    // month" on a freshly-migrated database (its DEFAULT is `now()`, and Flyway just ran). A
    // history row dated *before* that baseline would therefore be silently overridden by it, on
    // a fresh database — so the "old" salary is pinned explicitly at `month2Ago` (always earlier
    // than the baseline's own effective month, however far back that turns out to be) and the
    // raise at `month0` (so it wins the current month over the baseline on the same tie by write
    // order, and every past month still resolves to the pinned old salary instead of 0).
    standingAmountService.record(jordanAgent, StandingAmountType.SALARY, new BigDecimal("2500.00"), month2Ago, MANAGER_USER_ID);
    standingAmountService.record(jordanAgent, StandingAmountType.SALARY, new BigDecimal("2700.00"), month0, MANAGER_USER_ID);
    standingAmountService.record(priyaAgent, StandingAmountType.SALARY, new BigDecimal("4200.00"), month3Ago, MANAGER_USER_ID);
    standingAmountService.record(
        priyaAgent, StandingAmountType.ROLLOUT_ADVANCE, new BigDecimal("500.00"), month2Ago, MANAGER_USER_ID);
    standingAmountService.record(
        priyaAgent, StandingAmountType.ROLLOUT_ADVANCE, new BigDecimal("750.00"), month0, MANAGER_USER_ID);

    // --- Past-month history: Fees, Client Invoices approved, Agent Invoices paid ---
    writePastHistory(contractUs1, dana, jordan, month2Ago, month1Ago, counts);
    writePastHistory(contractUk1, dana, priya, month2Ago, month1Ago, counts);
    writePastHistory(contractUs2, noah, jordan, month2Ago, month1Ago, counts);
    writePastAgentInvoice(jordanAgent, month2Ago, jordan, counts);
    writePastAgentInvoice(jordanAgent, month1Ago, jordan, counts);
    writePastAgentInvoice(priyaAgent, month2Ago, priya, counts);
    writePastAgentInvoice(priyaAgent, month1Ago, priya, counts);

    // --- Current-month Requests, through the app's own services ---
    runContractUs1Requests(contractUs1, us1, dana, jordan, manager, counts);
    runContractUk1Requests(contractUk1, uk1, uk, dana, priya, manager, counts);
    runContractUs2Requests(contractUs2, us2, noah, jordan, manager, counts);

    // --- This month's invoices ---
    clientInvoiceController.send(contractUs1, jordan);
    clientInvoiceController.get(contractUk1, priya);
    clientInvoiceController.get(contractUs2, jordan);
    counts.clientInvoicesSentOrDraft = 3;

    agentInvoiceController.send(JORDAN_AGENT_ID, jordan);
    agentInvoiceController.get(priyaAgent.getId(), priya);
    counts.agentInvoicesSentOrDraft = 2;

    log.info("Demo story built: {}", counts);
  }

  // ---------------------------------------------------------------------------------------------
  // Agents
  // ---------------------------------------------------------------------------------------------

  private Agent createPriya(AuthenticatedPrincipal manager) {
    var response =
        agentController
            .create(
                new AgentCreateRequest(
                    "Priya Shah", Country.UNITED_KINGDOM, new BigDecimal("4500.00"), PRIYA_USERNAME, PRIYA_PASSWORD),
                manager)
            .getBody();
    return agentRepository.findById(response.id()).orElseThrow();
  }

  /**
   * A legacy Agent with no login (spec.md "The story", Agents: "One legacy Agent without a
   * login, so the Manager's 'Create login' flow has a target"). {@link AgentController#create}
   * always creates a login together with the Agent (agent-login-on-creation spec) — there is no
   * API path that produces this shape, exactly like the pre-agent-login-on-creation seeded Jordan
   * Ellis row this mirrors (V5 migration), so this one row is written directly.
   */
  private void createMarcus() {
    UUID id = UUID.randomUUID();
    Agent marcus = new Agent();
    marcus.setId(id);
    marcus.setTenant(tenantRepository.getReferenceById(TENANT_ID));
    marcus.setName("Marcus Webb");
    marcus.setCountry(Country.UNITED_STATES);
    marcus.setCurrency(Currency.USD);
    marcus.setSalaryAmount(new BigDecimal("2200.00"));
    marcus.setCreatedAt(Instant.now().minus(Duration.ofDays(180)));
    agentRepository.save(marcus);
    standingAmountService.record(
        marcus,
        StandingAmountType.SALARY,
        marcus.getSalaryAmount(),
        LocalDate.now(ZoneOffset.UTC).minusMonths(6).withDayOfMonth(1),
        MANAGER_USER_ID);
  }

  // ---------------------------------------------------------------------------------------------
  // Carriers
  // ---------------------------------------------------------------------------------------------

  private record UkCarriers(UUID eeId, UUID o2Id, UUID eePlanId, UUID o2TopupOptionId) {}

  private UkCarriers createUkCarrierCatalog(AuthenticatedPrincipal manager) {
    CarrierResponse ee = carrierController.create(new CarrierCreateRequest(Country.UNITED_KINGDOM, "EE"), manager).getBody();
    CarrierResponse o2 = carrierController.create(new CarrierCreateRequest(Country.UNITED_KINGDOM, "O2"), manager).getBody();
    CarrierResponse vodafone =
        carrierController.create(new CarrierCreateRequest(Country.UNITED_KINGDOM, "Vodafone"), manager).getBody();
    carrierController.archive(vodafone.id(), manager);

    carrierOfferController.createTopupOption(ee.id(), new CarrierOfferRequest("Boost 10", new BigDecimal("10.00")), manager);
    UUID eeBoost20 =
        carrierOfferController
            .createTopupOption(ee.id(), new CarrierOfferRequest("Boost 20", new BigDecimal("20.00")), manager)
            .getBody()
            .id();
    carrierOfferController.archiveTopupOption(ee.id(), eeBoost20, manager);
    UUID o2Topup =
        carrierOfferController
            .createTopupOption(o2.id(), new CarrierOfferRequest("Data Topup", new BigDecimal("8.00")), manager)
            .getBody()
            .id();

    UUID eePlan =
        carrierOfferController
            .createPostpaidPlan(ee.id(), new CarrierOfferRequest("Flex 20", new BigDecimal("20.00")), manager)
            .getBody()
            .id();
    carrierOfferController.createPostpaidPlan(o2.id(), new CarrierOfferRequest("Essential", new BigDecimal("18.00")), manager);

    return new UkCarriers(ee.id(), o2.id(), eePlan, o2Topup);
  }

  // ---------------------------------------------------------------------------------------------
  // Clients, Contracts, Testers
  // ---------------------------------------------------------------------------------------------

  /**
   * The one fixed-id anchor (see class Javadoc) — {@link ClientController#create} always assigns
   * a random id, so the marker row is written directly, matching exactly what that endpoint would
   * have stored for every other field.
   */
  private UUID createMarkerClient() {
    Client client = new Client();
    client.setId(CLIENT_SOLSTICE_ID);
    client.setTenant(tenantRepository.getReferenceById(TENANT_ID));
    client.setName("Solstice Retail Group");
    client.setCreatedAt(Instant.now());
    clientRepository.save(client);
    return client.getId();
  }

  private UUID createTester(
      UUID clientId, String username, String password, boolean primaryContact, AuthenticatedPrincipal manager) {
    var response =
        testerController
            .create(clientId, new TesterCreateRequest(username, password, primaryContact), manager)
            .getBody();
    return response.id();
  }

  private AuthenticatedPrincipal testerPrincipal(UUID testerId) {
    Tester tester = testerRepository.findById(testerId).orElseThrow();
    return principal(tester.getUser().getId(), tester.getUser().getUsername(), "TESTER");
  }

  // ---------------------------------------------------------------------------------------------
  // Fleets
  // ---------------------------------------------------------------------------------------------

  private record Us1Fleet(
      UUID a1, UUID a2, UUID a3, UUID a4, UUID simC1, UUID simC2, UUID simD, UUID simE, UUID simF) {}

  private Us1Fleet buildUs1Fleet(UUID contractId, AuthenticatedPrincipal manager) {
    UUID a1 = createSmartphone(contractId, "iPhone 13", null, SmartphoneOwner.CLIENT, manager);
    UUID a2 = createSmartphone(contractId, "Pixel 7", "PX7-88210", SmartphoneOwner.COMPANY, manager);
    UUID a3 = createSmartphone(contractId, "Galaxy A14", "GA14-55021", SmartphoneOwner.COMPANY, manager);
    UUID a4 = createSmartphone(contractId, "OnePlus 10", "OP10-33087", SmartphoneOwner.COMPANY, manager);
    smartphoneController.updateStatus(contractId, a3, new SmartphoneStatusUpdateRequest(SmartphoneStatus.IN_REPAIR), manager);

    UUID simC1 = createSimCard(contractId, "+1-202-555-0101", US_ATT_CARRIER_ID, SimCardFlavor.PREPAID, null, manager);
    UUID simC2 =
        createSimCard(contractId, "+1-202-555-0102", US_ATT_CARRIER_ID, SimCardFlavor.POSTPAID, US_ATT_PLAN_STARTER_ID, manager);
    UUID simD = createSimCard(contractId, "+1-202-555-0103", US_TMOBILE_CARRIER_ID, SimCardFlavor.PREPAID, null, manager);
    UUID simE = createSimCard(contractId, "+1-202-555-0104", US_TMOBILE_CARRIER_ID, SimCardFlavor.PREPAID, null, manager);
    UUID simF = createSimCard(contractId, "+1-202-555-0105", US_VERIZON_CARRIER_ID, SimCardFlavor.PREPAID, null, manager);

    install(contractId, simC1, a2, manager);
    install(contractId, simC2, a2, manager);
    install(contractId, simD, a1, manager);
    install(contractId, simE, a4, manager);

    return new Us1Fleet(a1, a2, a3, a4, simC1, simC2, simD, simE, simF);
  }

  private record Uk1Fleet(UUID b1, UUID bClient, UUID bCompany, UUID b3, UUID simG, UUID simH, UUID simI, UUID simStock) {}

  private Uk1Fleet buildUk1Fleet(UUID contractId, UkCarriers uk, AuthenticatedPrincipal manager) {
    UUID b1 = createSmartphone(contractId, "iPhone 14", "IP14-UK-2201", SmartphoneOwner.COMPANY, manager);
    UUID bClient = createSmartphone(contractId, "Moto G Power", "MGP-UK-1180", SmartphoneOwner.CLIENT, manager);
    UUID bCompany = createSmartphone(contractId, "iPhone SE Spare", "SE-UK-4471", SmartphoneOwner.COMPANY, manager);
    UUID b3 = createSmartphone(contractId, "iPhone 12", "IP12-UK-0092", SmartphoneOwner.COMPANY, manager);

    UUID simG = createSimCard(contractId, "+44-7700-900101", uk.eeId(), SimCardFlavor.POSTPAID, uk.eePlanId(), manager);
    UUID simH = createSimCard(contractId, "+44-7700-900102", uk.o2Id(), SimCardFlavor.PREPAID, null, manager);
    UUID simI = createSimCard(contractId, "+44-7700-900103", uk.eeId(), SimCardFlavor.PREPAID, null, manager);
    UUID simStock = createSimCard(contractId, "+44-7700-900104", uk.o2Id(), SimCardFlavor.PREPAID, null, manager);

    install(contractId, simG, b1, manager);
    install(contractId, simI, b3, manager);

    return new Uk1Fleet(b1, bClient, bCompany, b3, simG, simH, simI, simStock);
  }

  private record Us2Fleet(UUID c1, UUID c2, UUID d1, UUID simJ, UUID simK) {}

  private Us2Fleet buildUs2Fleet(UUID contractId, AuthenticatedPrincipal manager) {
    UUID c1 = createSmartphone(contractId, "iPhone 13 mini", "IP13M-9021", SmartphoneOwner.CLIENT, manager);
    UUID c2 = createSmartphone(contractId, "Spare Android", "SPARE-2270", SmartphoneOwner.COMPANY, manager);
    UUID d1 = createSmartphone(contractId, "Old Field Phone", "OFP-0099", SmartphoneOwner.COMPANY, manager);

    UUID simJ = createSimCard(contractId, "+1-303-555-0201", US_TMOBILE_CARRIER_ID, SimCardFlavor.PREPAID, null, manager);
    UUID simK = createSimCard(contractId, "+1-303-555-0202", US_VERIZON_CARRIER_ID, SimCardFlavor.PREPAID, null, manager);

    install(contractId, simJ, c1, manager);
    install(contractId, simK, c2, manager);

    return new Us2Fleet(c1, c2, d1, simJ, simK);
  }

  private UUID createSmartphone(
      UUID contractId, String model, String serial, SmartphoneOwner owner, AuthenticatedPrincipal manager) {
    return smartphoneController
        .create(contractId, new SmartphoneCreateRequest(model, serial, owner), manager)
        .getBody()
        .id();
  }

  private UUID createSimCard(
      UUID contractId, String number, UUID carrierId, SimCardFlavor flavor, UUID planId, AuthenticatedPrincipal manager) {
    return simCardController
        .create(contractId, new SimCardCreateRequest(number, carrierId, flavor, planId), manager)
        .getBody()
        .id();
  }

  private void install(UUID contractId, UUID simCardId, UUID smartphoneId, AuthenticatedPrincipal manager) {
    simCardController.updateInstalledIn(contractId, simCardId, new SimCardInstalledInUpdateRequest(smartphoneId), manager);
  }

  // ---------------------------------------------------------------------------------------------
  // Current-month Requests — Contract US1 (Solstice / Jordan)
  // ---------------------------------------------------------------------------------------------

  private void runContractUs1Requests(
      UUID contractId, Us1Fleet fleet, AuthenticatedPrincipal tester, AuthenticatedPrincipal agent,
      AuthenticatedPrincipal manager, Counts counts) {
    // A completed Reboot.
    UUID reboot = createRequest(contractId, reboot(fleet.a2()), tester).id();
    progressAndComplete(contractId, reboot, agent);
    counts.requests++;

    // A cancelled Request with a reason.
    UUID cancelled = createRequest(contractId, reboot(fleet.a2()), tester).id();
    requestController.updateStatus(contractId, cancelled, cancel("Tester no longer needs support this week"), agent);
    counts.requests++;

    // A completed Topup with its Fee from an Option.
    UUID topup = createRequest(contractId, topup(fleet.simC1(), US_ATT_TOPUP_25_ID), tester).id();
    progressAndComplete(contractId, topup, agent);
    feeController.create(contractId, feeForExisting(topup, FeeType.TOPUP, new BigDecimal("25.00"), US_ATT_TOPUP_25_ID), agent);
    counts.requests++;
    counts.fees++;

    // A completed SIM Swap exchange.
    UUID swap = createRequest(contractId, simSwapExchange(fleet.simD(), fleet.simE()), tester).id();
    progressAndComplete(contractId, swap, agent);
    counts.requests++;

    // An Other repair completed with a Fee.
    UUID other = createRequest(contractId, other("Cracked screen replacement on-site"), tester).id();
    progressAndComplete(contractId, other, agent);
    feeController.create(contractId, feeForExisting(other, FeeType.OTHER, new BigDecimal("45.00"), null), agent);
    counts.requests++;
    counts.fees++;

    // A Provision Smartphone approved and In Progress, ready to be fulfilled from the Agent's own
    // Stock (Jordan's Stock — see the Contract US2 Return below, which puts "Old Field Phone" there).
    UUID provisionSmartphone = createRequest(contractId, provisionSmartphone("Motorola Edge"), tester).id();
    approve(provisionSmartphone, manager);
    requestController.updateStatus(contractId, provisionSmartphone, status(RequestStatus.IN_PROGRESS), agent);
    counts.requests++;
  }

  // ---------------------------------------------------------------------------------------------
  // Current-month Requests — Contract UK1 (Solstice / Priya)
  // ---------------------------------------------------------------------------------------------

  private void runContractUk1Requests(
      UUID contractId, Uk1Fleet fleet, UkCarriers uk, AuthenticatedPrincipal tester, AuthenticatedPrincipal agent,
      AuthenticatedPrincipal manager, Counts counts) {
    // A Replace Smartphone completed (old phone retired, SIM carried over).
    UUID replaceSmartphone = createRequest(contractId, replaceSmartphone(fleet.b3()), tester).id();
    approve(replaceSmartphone, manager);
    progressAndComplete(contractId, replaceSmartphone, agent);
    counts.requests++;

    // A Replace SIM rejected with a reason.
    UUID replaceSim = createRequest(contractId, replaceSim(fleet.simH()), tester).id();
    requestByIdController.reject(
        replaceSim, new RequestRejectRequest("Not enough usage this month to justify a replacement"), manager);
    counts.requests++;

    // A Provision Smartphone at Pending Approval.
    createRequest(contractId, provisionSmartphone("Samsung Galaxy A54"), tester);
    counts.requests++;

    // A Provision SIM approved and In Progress.
    UUID provisionSim = createRequest(contractId, provisionSim(SimCardFlavor.PREPAID, uk.eeId()), tester).id();
    approve(provisionSim, manager);
    requestController.updateStatus(contractId, provisionSim, status(RequestStatus.IN_PROGRESS), agent);
    counts.requests++;

    // A Return completed with each Disposition: the Client-owned phone posts back to the Client
    // automatically, the Manager chooses Posted to company / Cancelled / Kept in Stock for the
    // rest — including the one Postpaid SIM cancelled this month.
    RequestResponse returnRequest =
        createRequest(
            contractId,
            returnRequest(List.of(fleet.bClient(), fleet.bCompany()), List.of(fleet.simG(), fleet.simStock())),
            tester);
    UUID companyUnitId = returnedUnitIdFor(returnRequest, fleet.bCompany(), null);
    UUID cancelledSimUnitId = returnedUnitIdFor(returnRequest, null, fleet.simG());
    UUID stockSimUnitId = returnedUnitIdFor(returnRequest, null, fleet.simStock());
    approve(
        returnRequest.id(),
        new RequestApprovalRequest(
            List.of(
                new UnitDisposition(companyUnitId, Disposition.POSTED_TO_COMPANY),
                new UnitDisposition(cancelledSimUnitId, Disposition.CANCELLED),
                new UnitDisposition(stockSimUnitId, Disposition.KEPT_IN_STOCK))),
        manager);
    requestController.updateStatus(contractId, returnRequest.id(), status(RequestStatus.IN_PROGRESS), agent);
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    requestController.updateStatus(
        contractId,
        returnRequest.id(),
        completeWithCancellations(List.of(new SimCardCancellationRequest(fleet.simG(), today))),
        agent);
    counts.requests++;
  }

  // ---------------------------------------------------------------------------------------------
  // Current-month Requests — Contract US2 (Harbor Line / Jordan)
  // ---------------------------------------------------------------------------------------------

  private void runContractUs2Requests(
      UUID contractId, Us2Fleet fleet, AuthenticatedPrincipal tester, AuthenticatedPrincipal agent,
      AuthenticatedPrincipal manager, Counts counts) {
    // A fresh Topup, still Submitted — something waiting for the Agent to start.
    createRequest(contractId, topup(fleet.simJ(), US_TMOBILE_TOPUP_5GB_ID), tester);
    counts.requests++;

    // A Return at Pending Approval, awaiting Dispositions — left for the Manager to decide live.
    createRequest(contractId, returnRequest(List.of(fleet.c2()), List.of(fleet.simK())), tester);
    counts.requests++;

    // A Return completed, Kept in Stock — gives Jordan a Stock unit ("Old Field Phone").
    RequestResponse keepInStock = createRequest(contractId, returnRequest(List.of(fleet.d1()), List.of()), tester);
    UUID unitId = returnedUnitIdFor(keepInStock, fleet.d1(), null);
    approve(keepInStock.id(), new RequestApprovalRequest(List.of(new UnitDisposition(unitId, Disposition.KEPT_IN_STOCK))), manager);
    progressAndComplete(contractId, keepInStock.id(), agent);
    counts.requests++;
  }

  private UUID returnedUnitIdFor(RequestResponse request, UUID smartphoneId, UUID simCardId) {
    return request.returnedUnits().stream()
        .filter(
            unit ->
                (smartphoneId != null && smartphoneId.equals(unit.smartphoneId()))
                    || (simCardId != null && simCardId.equals(unit.simCardId())))
        .findFirst()
        .orElseThrow()
        .id();
  }

  private RequestResponse createRequest(UUID contractId, RequestCreateRequest body, AuthenticatedPrincipal principal) {
    return requestController.create(contractId, body, principal).getBody();
  }

  private void progressAndComplete(UUID contractId, UUID requestId, AuthenticatedPrincipal agent) {
    requestController.updateStatus(contractId, requestId, status(RequestStatus.IN_PROGRESS), agent);
    requestController.updateStatus(contractId, requestId, status(RequestStatus.COMPLETED), agent);
  }

  private void approve(UUID requestId, AuthenticatedPrincipal manager) {
    requestByIdController.approve(requestId, null, manager);
  }

  private void approve(UUID requestId, RequestApprovalRequest body, AuthenticatedPrincipal manager) {
    requestByIdController.approve(requestId, body, manager);
  }

  // ---------------------------------------------------------------------------------------------
  // Past-month history
  // ---------------------------------------------------------------------------------------------

  /**
   * One small Fee for each of {@code month2Ago}/{@code month1Ago} (hung off a minimal, directly
   * written {@code OTHER} Request, since {@link Fee#getRequest()} is a non-nullable FK — the same
   * shape a real proactive Fee's auto-created linking Request already establishes, just written
   * directly since a Fee's own {@code billingMonth} is always "now" through every real API path),
   * then the two past Client Invoices, {@code APPROVED}, with their base amount and Fee-line
   * snapshot computed by the same {@link ContractAmountService} the real send transition uses.
   */
  private void writePastHistory(
      UUID contractId,
      AuthenticatedPrincipal testerForFixture,
      AuthenticatedPrincipal agent,
      LocalDate month2Ago,
      LocalDate month1Ago,
      Counts counts) {
    Contract contract = contractRepository.findById(contractId).orElseThrow();
    Tester tester = testerRepository.findByUserId(testerForFixture.userId()).orElseThrow();
    User raisedBy = userRepository.findById(agent.userId()).orElseThrow();

    writeHistoricalFee(contract, tester, raisedBy, month2Ago, new BigDecimal("15.00"));
    writeHistoricalFee(contract, tester, raisedBy, month1Ago, new BigDecimal("18.00"));
    counts.fees += 2;

    writePastClientInvoice(contract, month2Ago);
    writePastClientInvoice(contract, month1Ago);
    counts.clientInvoicesApproved += 2;
  }

  private void writeHistoricalFee(Contract contract, Tester tester, User raisedBy, LocalDate month, BigDecimal amount) {
    Instant createdAt = month.atStartOfDay(ZoneOffset.UTC).plusSeconds(3 * 24 * 3600).toInstant();

    Request request = new Request();
    request.setId(UUID.randomUUID());
    request.setTenant(contract.getTenant());
    request.setContract(contract);
    request.setTester(tester);
    request.setRaisedByUser(raisedBy);
    request.setAgentAuthored(true);
    request.setType(RequestType.OTHER);
    request.setStatus(RequestStatus.COMPLETED);
    request.setDescription("On-site device check");
    request.setCreatedAt(createdAt);
    requestRepository.save(request);

    Fee fee = new Fee();
    fee.setId(UUID.randomUUID());
    fee.setTenant(contract.getTenant());
    fee.setContract(contract);
    fee.setRequest(request);
    fee.setFeeType(FeeType.OTHER);
    fee.setAmount(amount);
    fee.setCurrency(contract.getCurrency());
    fee.setDescription("On-site device check");
    fee.setBillingMonth(month);
    fee.setCreatedAt(createdAt);
    feeRepository.save(fee);
  }

  private void writePastClientInvoice(Contract contract, LocalDate month) {
    Instant sentAt = month.atStartOfDay(ZoneOffset.UTC).plusSeconds(28 * 24 * 3600).toInstant();
    Instant approvedAt = sentAt.plusSeconds(2 * 24 * 3600);

    ClientInvoice invoice = new ClientInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setTenant(contract.getTenant());
    invoice.setContract(contract);
    invoice.setBillingMonth(month);
    invoice.setStatus(ClientInvoiceStatus.APPROVED);
    invoice.setCurrency(contract.getCurrency());
    invoice.setCreatedAt(sentAt);
    invoice.setSentAt(sentAt);
    invoice.setApprovedAt(approvedAt);
    invoice.setSnapshotBaseAmount(contractAmountService.baseAmount(contract.getId(), month));
    clientInvoiceRepository.save(invoice);

    for (Fee fee : contractAmountService.feesForMonth(contract.getId(), month)) {
      ClientInvoiceFeeSnapshot line = new ClientInvoiceFeeSnapshot();
      line.setId(UUID.randomUUID());
      line.setTenant(contract.getTenant());
      line.setClientInvoice(invoice);
      line.setFee(fee);
      line.setCreatedAt(sentAt);
      clientInvoiceFeeSnapshotRepository.save(line);
    }
  }

  /**
   * A past Agent Invoice, {@code PAID} — freezes its four lines with the real {@link
   * AgentInvoiceService} (send/approve/markPaid), the exact computation a real send/approve/paid
   * cycle would have produced, then overwrites only the cosmetic timestamps so it reads as
   * history rather than all happening in the same instant as today's run.
   */
  private void writePastAgentInvoice(Agent agent, LocalDate month, AuthenticatedPrincipal principal, Counts counts) {
    Instant sentAt = month.atStartOfDay(ZoneOffset.UTC).plusSeconds(28 * 24 * 3600).toInstant();
    Instant approvedAt = sentAt.plusSeconds(2 * 24 * 3600);
    Instant paidAt = sentAt.plusSeconds(5 * 24 * 3600);

    AgentInvoice invoice = new AgentInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setTenant(agent.getTenant());
    invoice.setAgent(agent);
    invoice.setBillingMonth(month);
    invoice.setStatus(AgentInvoiceStatus.DRAFT);
    invoice.setCurrency(agent.getCurrency());
    invoice.setCreatedAt(sentAt);
    agentInvoiceRepository.save(invoice);

    agentInvoiceService.send(invoice, principal);
    agentInvoiceService.approve(invoice, principal);
    agentInvoiceService.markPaid(invoice, principal);

    invoice.setSentAt(sentAt);
    invoice.setApprovedAt(approvedAt);
    invoice.setPaidAt(paidAt);
    agentInvoiceRepository.save(invoice);
    counts.agentInvoicesPaid++;
  }

  // ---------------------------------------------------------------------------------------------
  // RequestCreateRequest / RequestStatusUpdateRequest / FeeCreateRequest factories
  // ---------------------------------------------------------------------------------------------

  private static RequestCreateRequest reboot(UUID targetSmartphoneId) {
    return new RequestCreateRequest(
        RequestType.REBOOT, null, null, null, null, null, null, null,
        targetSmartphoneId, null, null, null, null, null, null, null, null, null, null);
  }

  private static RequestCreateRequest topup(UUID targetSimCardId, UUID topupOptionId) {
    return new RequestCreateRequest(
        RequestType.TOPUP, null, null, null, null, null, null, null,
        null, targetSimCardId, topupOptionId, null, null, null, null, null, null, null, null);
  }

  private static RequestCreateRequest simSwapExchange(UUID firstSimCardId, UUID secondSimCardId) {
    return new RequestCreateRequest(
        RequestType.SIM_SWAP, null, null, null, null, null, null, null,
        null, firstSimCardId, null, null, null, null, null, null, secondSimCardId, null, null);
  }

  private static RequestCreateRequest other(String description) {
    return new RequestCreateRequest(
        RequestType.OTHER, null, null, description, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null);
  }

  private static RequestCreateRequest provisionSmartphone(String requestedModel) {
    return new RequestCreateRequest(
        RequestType.PROVISION_SMARTPHONE, null, null, null, null, null, null, null,
        null, null, null, requestedModel, null, null, null, null, null, null, null);
  }

  private static RequestCreateRequest provisionSim(SimCardFlavor flavor, UUID carrierId) {
    return new RequestCreateRequest(
        RequestType.PROVISION_SIM, null, null, null, null, null, null, null,
        null, null, null, null, flavor, carrierId, null, null, null, null, null);
  }

  private static RequestCreateRequest replaceSmartphone(UUID targetSmartphoneId) {
    return new RequestCreateRequest(
        RequestType.REPLACE_SMARTPHONE, null, null, null, null, null, null, null,
        targetSmartphoneId, null, null, null, null, null, null, null, null, null, null);
  }

  private static RequestCreateRequest replaceSim(UUID targetSimCardId) {
    return new RequestCreateRequest(
        RequestType.REPLACE_SIM, null, null, null, null, null, null, null,
        null, targetSimCardId, null, null, null, null, null, null, null, null, null);
  }

  private static RequestCreateRequest returnRequest(List<UUID> smartphoneIds, List<UUID> simCardIds) {
    return new RequestCreateRequest(
        RequestType.RETURN, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, smartphoneIds, simCardIds);
  }

  private static RequestStatusUpdateRequest status(RequestStatus status) {
    return new RequestStatusUpdateRequest(status, null, null, null, null, null, null, List.of(), null, null);
  }

  private static RequestStatusUpdateRequest cancel(String reason) {
    return new RequestStatusUpdateRequest(
        RequestStatus.CANCELLED, reason, null, null, null, null, null, List.of(), null, null);
  }

  private static RequestStatusUpdateRequest completeWithCancellations(List<SimCardCancellationRequest> cancellations) {
    return new RequestStatusUpdateRequest(
        RequestStatus.COMPLETED, null, null, null, null, null, null, cancellations, null, null);
  }

  private static FeeCreateRequest feeForExisting(UUID requestId, FeeType feeType, BigDecimal amount, UUID topupOptionId) {
    return new FeeCreateRequest(
        requestId, feeType, amount, null, null, null, null, null, null, topupOptionId, null, null, null, null, null, null);
  }

  private static AuthenticatedPrincipal principal(UUID userId, String username, String role) {
    return new AuthenticatedPrincipal(userId, username, TENANT_ID, role);
  }

  /** Observability (ticket AC): logged once, whether the loader built the story or skipped it. */
  private static final class Counts {
    int agents;
    int carriers;
    int clients;
    int contracts;
    int testers;
    int smartphones;
    int simCards;
    int requests;
    int fees;
    int clientInvoicesApproved;
    int clientInvoicesSentOrDraft;
    int agentInvoicesPaid;
    int agentInvoicesSentOrDraft;

    @Override
    public String toString() {
      return "agents="
          + agents
          + ", carriers="
          + carriers
          + ", clients="
          + clients
          + ", contracts="
          + contracts
          + ", testers="
          + testers
          + ", smartphones="
          + smartphones
          + ", simCards="
          + simCards
          + ", requests="
          + requests
          + ", fees="
          + fees
          + ", clientInvoices(approved="
          + clientInvoicesApproved
          + ", thisMonth="
          + clientInvoicesSentOrDraft
          + "), agentInvoices(paid="
          + agentInvoicesPaid
          + ", thisMonth="
          + agentInvoicesSentOrDraft
          + ")";
    }
  }
}
