package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Client;
import java.util.UUID;

/** The calling Tester's own Client ({@code GET /api/me/client}). */
public record MeClientResponse(UUID clientId, String name) {

  public static MeClientResponse of(Client client) {
    return new MeClientResponse(client.getId(), client.getName());
  }
}
