package com.remotesupport.backend.dto;

/**
 * Corrects a Smartphone's model. Trimmed and length-checked by the service; a missing or blank
 * {@code model} is refused as {@code MODEL_BLANK}.
 */
public record SmartphoneModelUpdateRequest(String model) {}
