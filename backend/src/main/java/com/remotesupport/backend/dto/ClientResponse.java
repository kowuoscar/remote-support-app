package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Client;
import java.util.UUID;

public record ClientResponse(UUID id, String name, String primaryContactUsername, long contractCount) {

  public static ClientResponse of(Client client, String primaryContactUsername, long contractCount) {
    return new ClientResponse(client.getId(), client.getName(), primaryContactUsername, contractCount);
  }
}
