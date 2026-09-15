package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.Client;
import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.dto.ContractCreateRequest;
import com.remotesupport.backend.dto.ContractResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.repository.ClientRepository;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.repository.TenantRepository;
import com.remotesupport.backend.security.CallerIdentityResolver;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
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
 * Manager-only Contract CRUD (create + list). A Contract links exactly one Client and one Agent,
 * but neither is unique on its own — a Client may hold several Contracts and so may an Agent
 * (spec.md Core entities). Currency is copied from the Agent's currency at creation time.
 */
@RestController
@RequestMapping("/api/contracts")
public class ContractController {

  private final ContractRepository contractRepository;
  private final ClientRepository clientRepository;
  private final AgentRepository agentRepository;
  private final TenantRepository tenantRepository;
  private final CallerIdentityResolver callerIdentityResolver;

  public ContractController(
      ContractRepository contractRepository,
      ClientRepository clientRepository,
      AgentRepository agentRepository,
      TenantRepository tenantRepository,
      CallerIdentityResolver callerIdentityResolver) {
    this.contractRepository = contractRepository;
    this.clientRepository = clientRepository;
    this.agentRepository = agentRepository;
    this.tenantRepository = tenantRepository;
    this.callerIdentityResolver = callerIdentityResolver;
  }

  @PostMapping
  public ResponseEntity<ContractResponse> create(
      @Valid @RequestBody ContractCreateRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Client client =
        clientRepository
            .findByIdAndTenantId(request.clientId(), principal.tenantId())
            .orElseThrow(() -> new NotFoundException("No client with id " + request.clientId()));
    Agent agent =
        agentRepository
            .findByIdAndTenantId(request.agentId(), principal.tenantId())
            .orElseThrow(() -> new NotFoundException("No agent with id " + request.agentId()));

    Contract contract = new Contract();
    contract.setId(UUID.randomUUID());
    contract.setTenant(tenantRepository.getReferenceById(principal.tenantId()));
    contract.setClient(client);
    contract.setAgent(agent);
    contract.setCurrency(agent.getCurrency());
    contract.setCreatedAt(Instant.now());
    contractRepository.save(contract);

    AuditLog.created("Contract", contract.getId(), principal.userId(), principal.tenantId());

    return ResponseEntity.status(HttpStatus.CREATED).body(ContractResponse.of(contract));
  }

  /**
   * Manager sees every Contract in the tenant; an Agent only their own (matched via the
   * fleet-management ticket's Agent-to-User link); a Tester only their Client's — the same
   * scoping Fleet visibility uses, needed here so a Contract switcher has something to switch
   * between (spec.md Access control: "every actor sees only their own Contracts...").
   */
  @GetMapping
  public List<ContractResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    List<Contract> contracts =
        switch (principal.role()) {
          case "MANAGER" -> contractRepository.findByTenantIdOrderByCreatedAtAsc(principal.tenantId());
          case "AGENT" ->
              callerIdentityResolver
                  .resolveAgentId(principal)
                  .map(
                      agentId ->
                          contractRepository.findByTenantIdAndAgentIdOrderByCreatedAtAsc(
                              principal.tenantId(), agentId))
                  .orElseGet(List::of);
          case "TESTER" ->
              callerIdentityResolver
                  .resolveClientId(principal)
                  .map(
                      clientId ->
                          contractRepository.findByTenantIdAndClientIdOrderByCreatedAtAsc(
                              principal.tenantId(), clientId))
                  .orElseGet(List::of);
          default -> List.of();
        };
    return contracts.stream().map(ContractResponse::of).toList();
  }
}
