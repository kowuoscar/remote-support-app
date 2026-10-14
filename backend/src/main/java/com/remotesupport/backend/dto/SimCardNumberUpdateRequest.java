package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Corrects a SIM Card's number. A missing or blank {@code number} is refused as {@code
 * NUMBER_BLANK}; the service trims and length-checks it.
 */
public record SimCardNumberUpdateRequest(@NotBlank(message = "NUMBER_BLANK") String number) {}
