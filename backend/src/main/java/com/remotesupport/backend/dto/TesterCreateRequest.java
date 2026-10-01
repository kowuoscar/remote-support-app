package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A Tester's login under a Client. The password is generated and returned once; a body that still
 * sends {@code password} has it ignored like any unknown property.
 */
public record TesterCreateRequest(@NotBlank String username, boolean isPrimaryContact) {}
