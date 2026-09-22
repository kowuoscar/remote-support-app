package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code password} carries the shared {@link PasswordPolicy} minimum (password-minimum-length
 * ticket); a violation returns the same ordinary Bean Validation {@code 400} this endpoint already
 * produces for a blank password — no new {@code code}.
 */
public record TesterCreateRequest(
    @NotBlank String username,
    @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, message = PasswordPolicy.TOO_SHORT_MESSAGE)
        String password,
    boolean isPrimaryContact) {}
