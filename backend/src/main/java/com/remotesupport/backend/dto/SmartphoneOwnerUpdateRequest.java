package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.SmartphoneOwner;

/** Corrects a Smartphone's owner. A missing {@code owner} is refused as {@code OWNER_INVALID}. */
public record SmartphoneOwnerUpdateRequest(SmartphoneOwner owner) {}
