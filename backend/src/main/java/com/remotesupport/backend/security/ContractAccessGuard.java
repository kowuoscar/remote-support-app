package com.remotesupport.backend.security;

import com.remotesupport.backend.domain.Contract;
import com.remotesupport.backend.repository.ContractRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Per-resource Contract authorization (manager-entity-setup ticket predates the guard convention
 * later tickets established — {@link FleetAccessGuard}, {@link RequestAccessGuard}, {@link
 * ClientInvoiceAccessGuard}, {@link AgentInvoiceAccessGuard} — this brings {@link
 * com.remotesupport.backend.web.ContractController} in line with it). Contracts have no single
 * resource to check ownership against up front the way the other guards do — listing them *is*
 * the scoping — so this guard resolves and returns the caller's visible set directly rather than
 * validating an already-loaded Contract.
 */
@Component
public class ContractAccessGuard {

  private final ContractRepository contractRepository;
  private final CallerIdentityResolver callerIdentityResolver;

  public ContractAccessGuard(
      ContractRepository contractRepository, CallerIdentityResolver callerIdentityResolver) {
    this.contractRepository = contractRepository;
    this.callerIdentityResolver = callerIdentityResolver;
  }

  /**
   * Manager sees every Contract in the tenant; an Agent only their own (matched via the
   * fleet-management ticket's Agent-to-User link); a Tester only their Client's (spec.md Access
   * control: "every actor sees only their own Contracts...").
   */
  public List<Contract> visibleContracts(AuthenticatedPrincipal principal) {
    return switch (principal.role()) {
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
  }
}
