package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.RequestStatus;
import com.remotesupport.backend.domain.RequestType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * {@code type} is always required. {@code testerId} and {@code startingStatus} are only
 * meaningful for an Agent-authored creation (agent-request-fulfillment ticket AC: "Agent can log
 * a Request directly ... for one of their own Contracts, starting at Submitted or immediately at
 * Completed") — a Tester-authored creation (tester-request-submission ticket) ignores both: the
 * Tester is inferred from the caller's own login, and status always starts {@code SUBMITTED}.
 */
public record RequestCreateRequest(
    @NotNull RequestType type, UUID testerId, RequestStatus startingStatus) {}
