package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Contract;
import java.util.UUID;

public record ContractResponse(
    UUID id,
    UUID clientId,
    String clientName,
    UUID agentId,
    String agentName,
    String country,
    String currency) {

  public static ContractResponse of(Contract contract) {
    return new ContractResponse(
        contract.getId(),
        contract.getClient().getId(),
        contract.getClient().getName(),
        contract.getAgent().getId(),
        contract.getAgent().getName(),
        contract.getAgent().getCountry().name(),
        contract.getCurrency().name());
  }
}
