package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record SmartphoneCreateRequest(@NotBlank String model, @NotBlank String serial, String assignedTo) {}
