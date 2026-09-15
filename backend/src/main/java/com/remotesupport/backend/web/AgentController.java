package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.StandingAmountType;
import com.remotesupport.backend.dto.AgentCreateRequest;
import com.remotesupport.backend.dto.AgentResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.TenantRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager-only Agent CRUD (create + list). An Agent's currency is derived from its country, never
 * chosen independently by the Manager (spec.md: "a country (which fixes their currency)").
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

  private final AgentRepository agentRepository;
  private final ContractRepository contractRepository;
  private final TenantRepository tenantRepository;
  private final StandingAmountService standingAmountService;

  public AgentController(
      AgentRepository agentRepository,
      ContractRepository contractRepository,
      TenantRepository tenantRepository,
      StandingAmountService standingAmountService) {
    this.agentRepository = agentRepository;
    this.contractRepository = contractRepository;
    this.tenantRepository = tenantRepository;
    this.standingAmountService = standingAmountService;
  }

  @PostMapping
  public ResponseEntity<AgentResponse> create(
      @Valid @RequestBody AgentCreateRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Agent agent = new Agent();
    agent.setId(UUID.randomUUID());
    agent.setTenant(tenantRepository.getReferenceById(principal.tenantId()));
    agent.setName(request.name());
    agent.setCountry(request.country());
    agent.setCurrency(request.country().currency());
    agent.setSalaryAmount(request.salaryAmount());
    agent.setCreatedAt(Instant.now());
    agentRepository.save(agent);

    // The salary supplied at creation is this Agent's initial standing salary, effective
    // immediately (the calendar month it was created in) — writes the same StandingAmountType.
    // SALARY history AgentStandingAmountController's updates append to, so
    // AgentInvoiceController's resolution never needs a special-cased fallback (see
    // AgentStandingAmount's Javadoc).
    standingAmountService.record(
        agent,
        StandingAmountType.SALARY,
        agent.getSalaryAmount(),
        LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1),
        principal.userId());

    AuditLog.created("Agent", agent.getId(), principal.userId(), principal.tenantId());

    return ResponseEntity.status(HttpStatus.CREATED).body(AgentResponse.of(agent, 0));
  }

  @GetMapping
  public List<AgentResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return agentRepository.findByTenantIdOrderByNameAsc(principal.tenantId()).stream()
        .map(agent -> AgentResponse.of(agent, contractRepository.countByAgentId(agent.getId())))
        .toList();
  }
}
