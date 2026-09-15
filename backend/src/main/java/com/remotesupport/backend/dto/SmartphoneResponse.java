package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Smartphone;
import java.util.UUID;

public record SmartphoneResponse(
    UUID id, UUID contractId, String model, String serial, String assignedTo, String status) {

  public static SmartphoneResponse of(Smartphone smartphone) {
    return new SmartphoneResponse(
        smartphone.getId(),
        smartphone.getContract().getId(),
        smartphone.getModel(),
        smartphone.getSerial(),
        smartphone.getAssignedTo(),
        smartphone.getStatus().name());
  }
}
