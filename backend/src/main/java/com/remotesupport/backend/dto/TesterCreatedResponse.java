package com.remotesupport.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * The {@code 201} body of Tester creation: {@link TesterResponse}'s fields flattened, plus {@code
 * password} when the product generated one (omitted when the caller typed it). Shown once; never
 * used by the list endpoint, and {@code toString} redacts it.
 */
public record TesterCreatedResponse(
    UUID id,
    UUID clientId,
    String username,
    boolean isPrimaryContact,
    @JsonInclude(JsonInclude.Include.NON_NULL) String password) {

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
        .formatted(id, clientId, username, password == null ? null : "[redacted]");
  }
}
