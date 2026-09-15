package com.remotesupport.backend.web;

import com.remotesupport.backend.domain.Agent;
import com.remotesupport.backend.domain.AgentStandingAmount;
import com.remotesupport.backend.domain.StandingAmountType;
import com.remotesupport.backend.dto.AgentStandingAmountResponse;
import com.remotesupport.backend.dto.AgentStandingAmountUpdateRequest;
import com.remotesupport.backend.dto.AgentStandingAmountsResponse;
import com.remotesupport.backend.logging.AuditLog;
import com.remotesupport.backend.repository.AgentRepository;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A Manager setting and viewing an Agent's standing salary and standing Rollout Advance (spec.md
 * Solution/user stories 5-6; agent-standing-amounts-and-invoice-generation ticket). Manager-only
 * at the {@link com.remotesupport.backend.security.SecurityConfig} matcher level — this whole
 * path is nested under the existing Manager-only {@code /api/agents/**} rule, so no new matcher
 * was needed (unlike the Agent Invoice's own view, which an Agent may also reach).
 *
 * <p><b>Next-month-effective, always.</b> {@link #update} never accepts an {@code effectiveMonth}
 * from the caller: every change is computed to take effect starting the month *after* the one in
 * progress, exactly once, here — see {@link AgentStandingAmount}'s Javadoc for why this is modeled
 * as an append-only history rather than a mutable column.
 */
@RestController
@RequestMapping("/api/agents/{agentId}/standing-amounts")
public class AgentStandingAmountController {

  private final AgentRepository agentRepository;
  private final StandingAmountService standingAmountService;

  public AgentStandingAmountController(
      AgentRepository agentRepository, StandingAmountService standingAmountService) {
    this.agentRepository = agentRepository;
    this.standingAmountService = standingAmountService;
  }

  /** The salary/Rollout Advance amounts in effect right now — the edit form's defaults. */
  @GetMapping
  public AgentStandingAmountsResponse get(
      @PathVariable UUID agentId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Agent agent = findAgent(agentId, principal);
    LocalDate month = currentBillingMonth();
    BigDecimal salary = standingAmountService.resolve(agent.getId(), StandingAmountType.SALARY, month);
    BigDecimal rolloutAdvance =
        standingAmountService.resolve(agent.getId(), StandingAmountType.ROLLOUT_ADVANCE, month);
    return new AgentStandingAmountsResponse(salary, rolloutAdvance);
  }

  @PostMapping
  public ResponseEntity<AgentStandingAmountResponse> update(
      @PathVariable UUID agentId,
      @Valid @RequestBody AgentStandingAmountUpdateRequest request,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    Agent agent = findAgent(agentId, principal);
    LocalDate currentMonth = currentBillingMonth();
    LocalDate effectiveMonth = currentMonth.plusMonths(1);

    BigDecimal oldAmount = standingAmountService.resolve(agent.getId(), request.amountType(), currentMonth);
    AgentStandingAmount saved =
        standingAmountService.record(
            agent, request.amountType(), request.amount(), effectiveMonth, principal.userId());

    AuditLog.standingAmountChanged(
        agent.getId(),
        request.amountType().name(),
        oldAmount,
        request.amount(),
        effectiveMonth,
        principal.userId(),
        principal.tenantId());

    return ResponseEntity.status(HttpStatus.CREATED).body(AgentStandingAmountResponse.of(saved));
  }

  private Agent findAgent(UUID agentId, AuthenticatedPrincipal principal) {
    return agentRepository
        .findByIdAndTenantId(agentId, principal.tenantId())
        .orElseThrow(() -> new NotFoundException("No agent with id " + agentId));
  }

  private LocalDate currentBillingMonth() {
    return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
  }
}
