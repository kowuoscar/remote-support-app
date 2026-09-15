package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ContractCreateRequest(@NotNull UUID clientId, @NotNull UUID agentId) {}
