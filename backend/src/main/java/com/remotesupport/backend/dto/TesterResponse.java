package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Tester;
import java.util.UUID;

public record TesterResponse(UUID id, UUID clientId, String username, boolean isPrimaryContact) {

  public static TesterResponse of(Tester tester) {
    return new TesterResponse(
        tester.getId(),
        tester.getClient().getId(),
        tester.getUser().getUsername(),
        tester.isPrimaryContact());
  }
}
