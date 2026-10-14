package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.SmartphoneOwner;
import jakarta.validation.constraints.NotNull;

/** Corrects a Smartphone's owner. A missing {@code owner} is refused as {@code OWNER_INVALID}. */
public record SmartphoneOwnerUpdateRequest(@NotNull(message = "OWNER_INVALID") SmartphoneOwner owner) {}
