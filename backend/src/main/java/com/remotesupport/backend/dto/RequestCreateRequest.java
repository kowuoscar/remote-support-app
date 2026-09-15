package com.remotesupport.backend.dto;

import com.remotesupport.backend.domain.RequestType;
import jakarta.validation.constraints.NotNull;

public record RequestCreateRequest(@NotNull RequestType type) {}
