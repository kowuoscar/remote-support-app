package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Request;
import java.time.Instant;
import java.util.UUID;

public record RequestResponse(
    UUID id,
    UUID contractId,
    String type,
    String status,
    UUID raisedByTesterId,
    String raisedByUsername,
    boolean agentAuthored,
    String loggedByUsername,
    String cancellationReason,
    Instant createdAt) {

  public static RequestResponse of(Request request) {
    return new RequestResponse(
        request.getId(),
        request.getContract().getId(),
        request.getType().name(),
        request.getStatus().name(),
        request.getTester().getId(),
        request.getTester().getUser().getUsername(),
        request.isAgentAuthored(),
        request.getRaisedByUser().getUsername(),
        request.getCancellationReason(),
        request.getCreatedAt());
  }
}
