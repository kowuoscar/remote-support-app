package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Tester;
import java.time.Instant;
import java.util.UUID;

/** {@code deactivatedAt} is when the Tester's Login was switched off; {@code null} while active. */
public record TesterResponse(
    UUID id, UUID clientId, String username, boolean isPrimaryContact, Instant deactivatedAt) {

  public static TesterResponse of(Tester tester) {
    return new TesterResponse(
        tester.getId(),
        tester.getClient().getId(),
        tester.getUser().getUsername(),
        tester.isPrimaryContact(),
        tester.getUser().getDeactivatedAt());
  }
}
