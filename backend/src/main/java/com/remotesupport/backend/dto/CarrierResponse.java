package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.Carrier;
import java.time.Instant;
import java.util.UUID;

/** {@code archivedAt} is null while the Carrier is active. */
public record CarrierResponse(UUID id, String country, String name, Instant archivedAt) {

  public static CarrierResponse of(Carrier carrier) {
    return new CarrierResponse(
        carrier.getId(), carrier.getCountry().name(), carrier.getName(), carrier.getArchivedAt());
  }
}
