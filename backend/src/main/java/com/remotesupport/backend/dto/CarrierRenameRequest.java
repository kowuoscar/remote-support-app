package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CarrierRenameRequest(@NotBlank @Size(max = 255) String name) {}
