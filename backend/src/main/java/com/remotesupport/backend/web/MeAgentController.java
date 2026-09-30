package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.StandingAmountType;
import com.remotesupport.backend.dto.AgentOwnRecordResponse;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.security.CallerIdentityResolver;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The calling Agent's own record and standing amounts ({@code GET /api/me/agent}). Takes no id:
 * the Agent is resolved from the principal, so one Agent cannot address another's figures.
 * Setting a standing amount stays Manager-only at {@link AgentStandingAmountController}.
 */
@RestController
@RequestMapping("/api/me/agent")
public class MeAgentController {

  private final CallerIdentityResolver callerIdentityResolver;
  private final AgentRepository agentRepository;
  private final StandingAmountService standingAmountService;

  public MeAgentController(
      CallerIdentityResolver callerIdentityResolver,
      AgentRepository agentRepository,
      StandingAmountService standingAmountService) {
    this.callerIdentityResolver = callerIdentityResolver;
    this.agentRepository = agentRepository;
    this.standingAmountService = standingAmountService;
  }

  @GetMapping
  public AgentOwnRecordResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Agent agent =
        callerIdentityResolver
            .resolveAgentId(principal)
            .flatMap(id -> agentRepository.findByIdAndTenantId(id, principal.tenantId()))
            .orElseThrow(() -> new NotFoundException("The caller is not linked to an Agent"));
    LocalDate month = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
    BigDecimal salary =
        standingAmountService.resolve(agent.getId(), StandingAmountType.SALARY, month);
    BigDecimal rolloutAdvance =
        standingAmountService.resolve(agent.getId(), StandingAmountType.ROLLOUT_ADVANCE, month);
    return AgentOwnRecordResponse.of(agent, salary, rolloutAdvance);
  }
}
