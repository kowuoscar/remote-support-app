package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A login for an existing Agent that has none (create-login-for-existing-agent ticket). The
 * password is generated and returned once; a body that still sends {@code password} has it ignored
 * like any unknown property.
 */
public record AgentLoginCreateRequest(@NotBlank String username) {}
