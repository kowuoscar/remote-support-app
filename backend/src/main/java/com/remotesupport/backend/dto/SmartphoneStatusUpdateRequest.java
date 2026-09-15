package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.SmartphoneStatus;
import jakarta.validation.constraints.NotNull;

public record SmartphoneStatusUpdateRequest(@NotNull SmartphoneStatus status) {}
