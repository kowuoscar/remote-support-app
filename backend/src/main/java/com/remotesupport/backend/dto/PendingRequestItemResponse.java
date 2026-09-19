package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Request;
import java.time.Instant;

/**
 * One Request at Pending Approval, as the Manager's Pending Requests page needs it (spec.md
 * Manager approval: "type, Client, Tester, Agent, the requested details ... and age") — the
 * Request-side equivalent of {@link ReviewQueueItemResponse}. {@code request} carries the type,
 * Tester (via {@code raisedByUsername}/{@code raisedByTesterId}), Agent-authored flag and every
 * type's own denormalized details (for a Replace, the unit that would be retired) —
 * {@link RequestResponse} already has all of it; this only adds what a page listing Requests
 * across every Contract needs on top: the Client and Agent names, and how long it has waited
 * (since a Request starts Pending Approval the moment it's created, that's simply {@code
 * createdAt} — no extra column).
 */
public record PendingRequestItemResponse(RequestResponse request, String clientName, String agentName, Instant waitingSince) {

  public static PendingRequestItemResponse of(Request request) {
    return new PendingRequestItemResponse(
        RequestResponse.of(request),
        request.getContract().getClient().getName(),
        request.getContract().getAgent().getName(),
        request.getCreatedAt());
  }
}
