package com.remotesupport.backend.support;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.AgentInvoice;
import com.remotesupport.backend.domain.AgentInvoiceStatus;
import com.remotesupport.backend.domain.Carrier;
import com.remotesupport.backend.domain.Client;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.domain.Currency;
import com.remotesupport.backend.domain.Role;
import com.remotesupport.backend.domain.Smartphone;
import com.remotesupport.backend.domain.SmartphoneOwner;
import com.remotesupport.backend.domain.SmartphoneStatus;
import com.remotesupport.backend.domain.Tenant;
import com.remotesupport.backend.domain.Tester;
import com.remotesupport.backend.domain.TopupOption;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.repository.AgentInvoiceRepository;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.CarrierRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ClientRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.SmartphoneRepository;
import com.remotesupport.backend.repository.TenantRepository;
import com.remotesupport.backend.repository.TesterRepository;
import com.remotesupport.backend.repository.TopupOptionRepository;
import com.remotesupport.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Builds data in a second tenant straight through the repositories — there is no API for creating
 * a tenant — so tests can prove a caller never sees or reaches another tenant's invoices or
 * Carriers. Imported into a test's context with {@code @Import(OtherTenantFixture.class)}.
 */
@Component
public class OtherTenantFixture {

  private final TenantRepository tenantRepository;
  private final ClientRepository clientRepository;
  private final AgentRepository agentRepository;
  private final ContractRepository contractRepository;
  private final ClientInvoiceRepository clientInvoiceRepository;
  private final AgentInvoiceRepository agentInvoiceRepository;
  private final CarrierRepository carrierRepository;
  private final TopupOptionRepository topupOptionRepository;
  private final SmartphoneRepository smartphoneRepository;
  private final UserRepository userRepository;
  private final TesterRepository testerRepository;
  private final PasswordEncoder passwordEncoder;

  public OtherTenantFixture(
      TenantRepository tenantRepository,
      ClientRepository clientRepository,
      AgentRepository agentRepository,
      ContractRepository contractRepository,
      ClientInvoiceRepository clientInvoiceRepository,
      AgentInvoiceRepository agentInvoiceRepository,
      CarrierRepository carrierRepository,
      TopupOptionRepository topupOptionRepository,
      SmartphoneRepository smartphoneRepository,
      UserRepository userRepository,
      TesterRepository testerRepository,
      PasswordEncoder passwordEncoder) {
    this.tenantRepository = tenantRepository;
    this.clientRepository = clientRepository;
    this.agentRepository = agentRepository;
    this.contractRepository = contractRepository;
    this.clientInvoiceRepository = clientInvoiceRepository;
    this.agentInvoiceRepository = agentInvoiceRepository;
    this.carrierRepository = carrierRepository;
    this.topupOptionRepository = topupOptionRepository;
    this.smartphoneRepository = smartphoneRepository;
    this.userRepository = userRepository;
    this.testerRepository = testerRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /** A sent Client Invoice, for the current month, on a Contract in a brand-new tenant. */
  public UUID sentClientInvoiceInAnotherTenant() {
    Instant now = Instant.now();
    Tenant tenant = newTenant(now);
    Agent agent = newAgent(tenant, now);

    Client client = newClient(tenant, "Other Tenant Client", now);

    Contract contract = new Contract();
    contract.setId(UUID.randomUUID());
    contract.setTenant(tenant);
    contract.setClient(client);
    contract.setAgent(agent);
    contract.setCurrency(Currency.USD);
    contract.setCreatedAt(now);
    contractRepository.save(contract);

    ClientInvoice invoice = new ClientInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setTenant(tenant);
    invoice.setContract(contract);
    invoice.setBillingMonth(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1));
    invoice.setStatus(ClientInvoiceStatus.SENT);
    invoice.setCurrency(Currency.USD);
    invoice.setCreatedAt(now);
    invoice.setSentAt(now.minusSeconds(86_400 * 30L));
    invoice.setSnapshotBaseAmount(new BigDecimal("10.00"));
    clientInvoiceRepository.saveAndFlush(invoice);

    return invoice.getId();
  }

  /** A sent Agent Invoice, for the current month, for an Agent in a brand-new tenant. */
  public UUID sentAgentInvoiceInAnotherTenant() {
    Instant now = Instant.now();
    Tenant tenant = newTenant(now);
    Agent agent = newAgent(tenant, now);

    AgentInvoice invoice = new AgentInvoice();
    invoice.setId(UUID.randomUUID());
    invoice.setTenant(tenant);
    invoice.setAgent(agent);
    invoice.setBillingMonth(LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1));
    invoice.setStatus(AgentInvoiceStatus.SENT);
    invoice.setCurrency(Currency.USD);
    invoice.setCreatedAt(now);
    invoice.setSentAt(now.minusSeconds(86_400 * 30L));
    invoice.setSnapshotLocalSupportFees(BigDecimal.ZERO);
    invoice.setSnapshotSalary(new BigDecimal("1000.00"));
    invoice.setSnapshotRolloutAdvanceRepayment(BigDecimal.ZERO);
    invoice.setSnapshotRolloutAdvanceNewAdvance(BigDecimal.ZERO);
    agentInvoiceRepository.saveAndFlush(invoice);

    return invoice.getId();
  }

  /**
   * An active United States Carrier in a brand-new tenant — the seeded Agent's own Country, so a
   * refusal can only come from tenant scoping, never from the per-Country check.
   */
  public UUID carrierInAnotherTenant() {
    Instant now = Instant.now();
    Carrier carrier = new Carrier();
    carrier.setId(UUID.randomUUID());
    carrier.setTenant(newTenant(now));
    carrier.setCountry(Country.UNITED_STATES);
    carrier.setName("Other Tenant Wireless");
    carrier.setCreatedAt(now);
    carrierRepository.saveAndFlush(carrier);
    return carrier.getId();
  }

  /** An active Topup Option of an active United States Carrier, in a brand-new tenant. */
  public UUID topupOptionInAnotherTenant() {
    UUID carrierId = carrierInAnotherTenant();
    TopupOption option = new TopupOption();
    option.setId(UUID.randomUUID());
    option.setCarrier(carrierRepository.getReferenceById(carrierId));
    option.setName("Other Tenant Refill");
    option.setPrice(new BigDecimal("20.00"));
    option.setCreatedAt(Instant.now());
    topupOptionRepository.saveAndFlush(option);
    return option.getId();
  }

  /**
   * A Smartphone in another tenant's Agent's Stock (agent-stock ticket AC: "another tenant's Stock
   * is invisible") — built straight through the repository, since {@code holdingAgent} can only be
   * set by a completed Return today.
   */
  public UUID stockSmartphoneInAnotherTenant() {
    Instant now = Instant.now();
    Tenant tenant = newTenant(now);
    Agent agent = newAgent(tenant, now);

    Smartphone smartphone = new Smartphone();
    smartphone.setId(UUID.randomUUID());
    smartphone.setTenant(tenant);
    smartphone.setHoldingAgent(agent);
    smartphone.setModel("Other Tenant Stock Phone");
    smartphone.setOwner(SmartphoneOwner.COMPANY);
    smartphone.setStatus(SmartphoneStatus.ACTIVE);
    smartphone.setCreatedAt(now);
    smartphoneRepository.saveAndFlush(smartphone);
    return smartphone.getId();
  }

  /**
   * A brand-new Tenant with a working {@code MANAGER}-role login and one {@link Client} of its
   * own (second-tenant-test-seam spec's {@code OtherTenantLogin}): a sign-in assertion needs the
   * Tenant to compare against and the plaintext password, neither recoverable from the stored
   * hash, and the Client rides along so the signed-in Manager has something of its own to read.
   * {@code username}/{@code password} are parameters rather than fixed, so the same method serves
   * both a clean username and one colliding with the seeded Tenant's own (pin-colliding-username-
   * sign-in ticket).
   */
  public OtherTenantLogin managerLoginInAnotherTenant(String username, String password) {
    Instant now = Instant.now();
    Tenant tenant = newTenant(now);

    User user = new User();
    user.setId(UUID.randomUUID());
    user.setTenant(tenant);
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(Role.MANAGER);
    user.setCreatedAt(now);
    userRepository.saveAndFlush(user);

    Client client = newClient(tenant, "Other Tenant Manager's Client", now);
    clientRepository.flush();

    return new OtherTenantLogin(tenant.getId(), username, password, client.getId());
  }

  /**
   * A brand-new Tenant with an Agent that has a working {@code AGENT}-role login, written directly
   * with a known password (a reset never reveals the original, so a test can only prove "still
   * signs in with its original password" if the fixture knows it).
   */
  public OtherTenantAgentLogin agentLoginInAnotherTenant(String username, String password) {
    Instant now = Instant.now();
    Tenant tenant = newTenant(now);
    Agent agent = newAgent(tenant, now);

    User user = new User();
    user.setId(UUID.randomUUID());
    user.setTenant(tenant);
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(Role.AGENT);
    user.setAgent(agent);
    user.setCreatedAt(now);
    userRepository.saveAndFlush(user);

    return new OtherTenantAgentLogin(tenant.getId(), agent.getId(), username, password);
  }

  /**
   * A brand-new Tenant with a Client and a Tester under it, the Tester's {@code TESTER}-role login
   * written directly with a known password (see {@link #agentLoginInAnotherTenant}).
   */
  public OtherTenantTesterLogin testerLoginInAnotherTenant(String username, String password) {
    Instant now = Instant.now();
    Tenant tenant = newTenant(now);
    Client client = newClient(tenant, "Other Tenant Tester's Client", now);

    User user = new User();
    user.setId(UUID.randomUUID());
    user.setTenant(tenant);
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(Role.TESTER);
    user.setCreatedAt(now);
    userRepository.saveAndFlush(user);

    Tester tester = new Tester();
    tester.setId(UUID.randomUUID());
    tester.setTenant(tenant);
    tester.setClient(client);
    tester.setUser(user);
    tester.setPrimaryContact(false);
    tester.setCreatedAt(now);
    testerRepository.saveAndFlush(tester);

    return new OtherTenantTesterLogin(
        tenant.getId(), client.getId(), tester.getId(), username, password);
  }

  /** A Tester and its login in another Tenant, as built by {@link #testerLoginInAnotherTenant}. */
  public record OtherTenantTesterLogin(
      UUID tenantId, UUID clientId, UUID testerId, String username, String password) {}

  /** An Agent and its login in another Tenant, as built by {@link #agentLoginInAnotherTenant}. */
  public record OtherTenantAgentLogin(
      UUID tenantId, UUID agentId, String username, String password) {}

  /**
   * A second Tenant's login and its data, as built by {@link #managerLoginInAnotherTenant}: the
   * Tenant to compare a sign-in against, the credentials that sign in to it, and the Client that
   * proves it reads only its own data. {@code password} is the plaintext, never recoverable from
   * the stored hash.
   */
  public record OtherTenantLogin(UUID tenantId, String username, String password, UUID clientId) {}

  private Tenant newTenant(Instant now) {
    Tenant tenant = new Tenant();
    tenant.setId(UUID.randomUUID());
    tenant.setName("Other Tenant");
    tenant.setCreatedAt(now);
    return tenantRepository.save(tenant);
  }

  private Agent newAgent(Tenant tenant, Instant now) {
    Agent agent = new Agent();
    agent.setId(UUID.randomUUID());
    agent.setTenant(tenant);
    agent.setName("Other Tenant Agent");
    agent.setCountry(Country.UNITED_STATES);
    agent.setCurrency(Currency.USD);
    agent.setSalaryAmount(new BigDecimal("1000.00"));
    agent.setCreatedAt(now);
    return agentRepository.save(agent);
  }

  private Client newClient(Tenant tenant, String name, Instant now) {
    Client client = new Client();
    client.setId(UUID.randomUUID());
    client.setTenant(tenant);
    client.setName(name);
    client.setCreatedAt(now);
    return clientRepository.save(client);
  }
}
