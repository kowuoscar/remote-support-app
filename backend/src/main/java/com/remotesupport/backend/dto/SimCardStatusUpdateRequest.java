package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.SimCardStatus;
import jakarta.validation.constraints.NotNull;

public record SimCardStatusUpdateRequest(@NotNull SimCardStatus status) {}
