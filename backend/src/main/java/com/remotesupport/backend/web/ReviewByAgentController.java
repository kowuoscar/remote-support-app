package com.remotesupport.backend.web;

import com.remotesupport.backend.dto.ReviewAgentResponse;
import com.remotesupport.backend.dto.ReviewMonthResponse;
import com.remotesupport.backend.dto.ReviewMonthSummaryResponse;
import com.remotesupport.backend.security.JwtService.AuthenticatedPrincipal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Manager's review by Agent (manager-reviews-by-agent spec, "Reads"): the Agents with what
 * waits on the Manager, an Agent's months, and an Agent's month. Views of the Review Queue, so
 * Manager-only at the matcher level (SecurityConfig, {@code /api/review-queue/**}). Read-only: it
 * never creates or changes an invoice.
 */
@RestController
public class ReviewByAgentController {

  private final ReviewByAgentService reviewByAgentService;

  public ReviewByAgentController(ReviewByAgentService reviewByAgentService) {
    this.reviewByAgentService = reviewByAgentService;
  }

  @GetMapping("/api/review-queue/agents")
  public List<ReviewAgentResponse> agents(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return reviewByAgentService.agents(principal.tenantId());
  }

  @GetMapping("/api/review-queue/agents/{agentId}/months")
  public List<ReviewMonthSummaryResponse> months(
      @PathVariable UUID agentId, @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return reviewByAgentService.months(principal.tenantId(), agentId);
  }

  /** {@code month} is {@code YYYY-MM}. */
  @GetMapping("/api/review-queue/agents/{agentId}/months/{month}")
  public ReviewMonthResponse month(
      @PathVariable UUID agentId,
      @PathVariable String month,
      @AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return reviewByAgentService.month(principal.tenantId(), agentId, parse(month));
  }

  private static LocalDate parse(String month) {
    try {
      return YearMonth.parse(month).atDay(1);
    } catch (DateTimeParseException e) {
      throw new InvalidRequestException("month must be YYYY-MM");
    }
  }
}
