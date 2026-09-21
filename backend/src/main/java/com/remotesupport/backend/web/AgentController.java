package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.StandingAmountType;
import com.remotesupport.backend.domain.User;
import com.remotesupport.backend.dto.AgentCreateRequest;
import com.remotesupport.backend.dto.AgentLoginCreateRequest;
import com.remotesupport.backend.dto.AgentResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.AgentLogin;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.TenantRepository;
import com.remotesupport.backend.repository.UserRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager-only Agent CRUD (create + list), and giving a login-less Agent its login. An Agent's
 * currency is derived from its country, never chosen independently by the Manager (spec.md: "a
 * country (which fixes their currency)").
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

  private final AgentRepository agentRepository;
  private final ContractRepository contractRepository;
  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final StandingAmountService standingAmountService;
  private final AgentLoginService agentLoginService;

  public AgentController(
      AgentRepository agentRepository,
      ContractRepository contractRepository,
      TenantRepository tenantRepository,
      UserRepository userRepository,
      StandingAmountService standingAmountService,
      AgentLoginService agentLoginService) {
    this.agentRepository = agentRepository;
    this.contractRepository = contractRepository;
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
    this.standingAmountService = standingAmountService;
    this.agentLoginService = agentLoginService;
  }

  /**
   * Creates the Agent, its initial standing salary and its login in one transaction
   * (agent-login-on-creation spec, "One request, one transaction"): a username conflict leaves no
   * Agent, standing amount or User behind. There is deliberately no username pre-check: the
   * conflict is detected by the {@code users} unique constraint after the Agent and its standing
   * amount are written, and this transaction is what removes them (AgentCreationAtomicityTest).
   */
  @PostMapping
  @Transactional
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

    User login =
        agentLoginService.create(
            agent, request.username().strip(), request.password(), principal.userId());

    AuditLog.created("Agent", agent.getId(), principal.userId(), principal.tenantId());

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(AgentResponse.of(agent, 0, login.getUsername()));
  }

  /**
   * Gives an existing Agent that has none its login (agent-login-on-creation spec, "Login for an
   * existing Agent"): 404 outside the caller's tenant, 409 when the Agent already has a login or
   * the username is taken — told apart by the body's {@code code}.
   */
  @PostMapping("/{agentId}/login")
  @Transactional
  public ResponseEntity<AgentResponse> createLogin(
      @PathVariable UUID agentId,
      @Valid @RequestBody AgentLoginCreateRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Agent agent =
        agentRepository
            .findByIdAndTenantId(agentId, principal.tenantId())
            .orElseThrow(() -> new NotFoundException("No agent with id " + agentId));
    String username = request.username().strip();
    agentLoginService.requireLoginCreatable(agent, username);

    User login = agentLoginService.create(agent, username, request.password(), principal.userId());

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            AgentResponse.of(
                agent, contractRepository.countByAgentId(agent.getId()), login.getUsername()));
  }

  @GetMapping
  public List<AgentResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Map<UUID, String> loginUsernames =
        userRepository.findAgentLoginsByTenantId(principal.tenantId()).stream()
            .collect(Collectors.toMap(AgentLogin::agentId, AgentLogin::username));
    return agentRepository.findByTenantIdOrderByNameAsc(principal.tenantId()).stream()
        .map(
            agent ->
                AgentResponse.of(
                    agent,
                    contractRepository.countByAgentId(agent.getId()),
                    loginUsernames.get(agent.getId())))
        .toList();
  }

  /**
   * Both login-creation 409s carry a {@code code} ({@code USERNAME_TAKEN} or {@code
   * AGENT_ALREADY_HAS_LOGIN}) so the client can tell "choose another email" from "this page is
   * stale" — a plain {@link ConflictException} body carries no message.
   */
  @ExceptionHandler(AgentLoginConflictException.class)
  public ResponseEntity<Map<String, String>> agentLoginConflict(AgentLoginConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", e.reason().name(), "message", e.getMessage()));
  }
}
