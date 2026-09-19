package com.remotesupport.backend.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One SIM Card's effective cancellation date, given at completion of a {@code RETURN} Request
 * (manager-decides-return-disposition ticket, spec.md Solution's Completion table: "the Agent
 * gives the effective cancellation date for each such SIM Card; completion is refused without
 * one"). A plain list of these, rather than a {@code Map<UUID, LocalDate>}, matches {@link
 * RequestApprovalRequest.UnitDisposition}'s own shape for the same reason: one entry per SIM Card
 * being cancelled, and a Return can cancel more than one at once.
 */
public record SimCardCancellationRequest(UUID simCardId, LocalDate effectiveDate) {}
