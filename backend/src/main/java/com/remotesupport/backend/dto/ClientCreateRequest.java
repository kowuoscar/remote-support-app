package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ClientCreateRequest(@NotBlank String name) {}
