package com.remotesupport.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Corrects a Smartphone's model. A missing or blank {@code model} is refused as {@code
 * MODEL_BLANK}; the service trims and length-checks it.
 */
public record SmartphoneModelUpdateRequest(@NotBlank(message = "MODEL_BLANK") String model) {}
