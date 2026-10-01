package com.remotesupport.backend.dto;

import java.util.UUID;

/**
 * The {@code 201} body of Tester creation: {@link TesterResponse}'s fields flattened, plus the
 * generated {@code password}. Shown once; never used by the list endpoint, and {@code toString}
 * redacts it.
 */
public record TesterCreatedResponse(
    UUID id,
    UUID clientId,
    String username,
    boolean isPrimaryContact,
    String password) {

  public static TesterCreatedResponse of(TesterResponse tester, String generatedPassword) {
    return new TesterCreatedResponse(
        tester.id(),
        tester.clientId(),
        tester.username(),
        tester.isPrimaryContact(),
        generatedPassword);
  }

  @Override
  public String toString() {
    return "TesterCreatedResponse[id=%s, clientId=%s, username=%s, password=%s]"
        .formatted(id, clientId, username, "[redacted]");
  }
}
