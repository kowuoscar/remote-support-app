package com.remotesupport.backend.support;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.Client;
import com.remotesupport.backend.domain.ClientInvoice;
import com.remotesupport.backend.domain.ClientInvoiceStatus;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.domain.Country;
import com.remotesupport.backend.domain.Currency;
import com.remotesupport.backend.domain.Tenant;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.ClientInvoiceRepository;
import com.remotesupport.backend.repository.ClientRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds data in a second tenant straight through the repositories — there is no API for creating
 * a tenant — so tests can prove a Manager never sees or reaches another tenant's invoices.
 * Imported into a test's context with {@code @Import(OtherTenantFixture.class)}.
 */
@Component
public class OtherTenantFixture {

  private final TenantRepository tenantRepository;
  private final ClientRepository clientRepository;
  private final AgentRepository agentRepository;
  private final ContractRepository contractRepository;
  private final ClientInvoiceRepository clientInvoiceRepository;

  public OtherTenantFixture(
      TenantRepository tenantRepository,
      ClientRepository clientRepository,
      AgentRepository agentRepository,
      ContractRepository contractRepository,
      ClientInvoiceRepository clientInvoiceRepository) {
    this.tenantRepository = tenantRepository;
    this.clientRepository = clientRepository;
    this.agentRepository = agentRepository;
    this.contractRepository = contractRepository;
    this.clientInvoiceRepository = clientInvoiceRepository;
  }

  /** A sent Client Invoice, for the current month, on a Contract in a brand-new tenant. */
  public UUID sentClientInvoiceInAnotherTenant() {
    Instant now = Instant.now();

    Tenant tenant = new Tenant();
    tenant.setId(UUID.randomUUID());
    tenant.setName("Other Tenant");
    tenant.setCreatedAt(now);
    tenantRepository.save(tenant);

    Client client = new Client();
    client.setId(UUID.randomUUID());
    client.setTenant(tenant);
    client.setName("Other Tenant Client");
    client.setCreatedAt(now);
    clientRepository.save(client);

    Agent agent = new Agent();
    agent.setId(UUID.randomUUID());
    agent.setTenant(tenant);
    agent.setName("Other Tenant Agent");
    agent.setCountry(Country.UNITED_STATES);
    agent.setCurrency(Currency.USD);
    agent.setSalaryAmount(new BigDecimal("1000.00"));
    agent.setCreatedAt(now);
    agentRepository.save(agent);

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
}
