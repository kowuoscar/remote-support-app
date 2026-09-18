package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Smartphone;
import java.util.UUID;

// serial is null for a Smartphone created without one (smartphone-owner-and-optional-serial
// ticket AC). owner is always CLIENT or COMPANY -- replaces the removed "assignedTo".
public record SmartphoneResponse(
    UUID id, UUID contractId, String model, String serial, String owner, String status) {

  public static SmartphoneResponse of(Smartphone smartphone) {
    return new SmartphoneResponse(
        smartphone.getId(),
        smartphone.getContract().getId(),
        smartphone.getModel(),
        smartphone.getSerial(),
        smartphone.getOwner().name(),
        smartphone.getStatus().name());
  }
}
