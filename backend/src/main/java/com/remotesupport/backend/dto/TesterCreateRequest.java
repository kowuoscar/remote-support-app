package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record TesterCreateRequest(
    @NotBlank String username, @NotBlank String password, boolean isPrimaryContact) {}
